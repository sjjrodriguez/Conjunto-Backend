# Plan técnico P6 — separación física de apps, sesión segura, portería, pagos y personal

Estado verificado contra el código (backend `main` @ 63853c2, frontend `main`). Todo lo del prompt que ya existe se conserva.
Rama por bloque y apiladas (`p6/bN-...`); solo compilar + tests por bloque; una corrida de emulador al final; merge solo cuando se pida.

## 0. Hallazgos que cambian el diseño (premisas del prompt vs. código real)

| # | Prompt dice | Realidad | Decisión propuesta |
|---|---|---|---|
| 1 | "84 endpoints en ApiService" | 116 anotaciones HTTP | Se reparten por prefijo de ruta (sección 4); las firmas no cambian |
| 2 | `SecurityConfig` default `enforce:true` | El default real lo manda `application.properties:90` (`${SECURITY_ENFORCE:false}`), no el `@Value`; `ModoTransicionTest` fuerza `false` | Cambiar los dos (`@Value` y properties a `true`); `ModoTransicionTest` queda como prueba del modo opcional. **Producción en Render debe tener `SECURITY_ENFORCE=true` antes de publicar**, o la app antigua sin token deja de entrar |
| 3 | CONTADOR sin PUT/DELETE de cuotas | `/api/cuotas/**` usa `cartera()` = ADMIN+CONTADOR (permite todo) | Reglas por método: GET y POST solicitud = cartera; PUT/DELETE y generar = admin |
| 4 | `POST /api/auth/**` es público | Un `permitAll` general taparía `logout` y `biometria/activar` (necesitan sesión) | Reglas explícitas antes: `/api/auth/logout`, `/api/auth/biometria/activar`, `DELETE /api/auth/biometria/*` = autenticado; `login`, `biometria/login`, `registro/**` = público |
| 5 | Rol `PORTERIA` | `TokenService.paraAdmin` convierte todo lo que no es CONTADOR en `ADMIN` → un PORTERIA entraría como ADMIN | Se corrige en el mismo bloque que crea el rol (mapa explícito rol→rol). Es el riesgo de seguridad nº 1 |
| 6 | `token_version` en `apartamento` | El login por apartamento comparte un solo sujeto (`apartamento:N`) entre todos los que usan esa clave: un logout invalidaría a todos | Se añade la columna como pidió, pero el logout de un token de apartamento solo borra el dispositivo; los residentes con documento y los admin sí incrementan su propia versión |
| 7 | `token_version` validado en el filtro JWT | JWT stateless: validar = una consulta por petición | Validador `OAuth2TokenValidator` con caché Caffeine de 30 s por sujeto; el logout limpia la entrada |
| 8 | Webhook Wompi con `WOMPI_INTEGRITY_SECRET` | La firma del **evento** usa otro secreto (`WOMPI_EVENTS_SECRET`); el de integridad firma el checkout | Se usan los dos. Variables: `WOMPI_PUBLIC_KEY`, `WOMPI_INTEGRITY_SECRET`, `WOMPI_EVENTS_SECRET`, `WOMPI_CHECKOUT_URL` |
| 9 | Cuota "PAGADA" | `ck_cuota_estado` ya tiene `PAGADA` (V6) | Reutilizar; idempotencia por `transaccion_pago.estado` ya `APROBADO` |
| 10 | `src/main` nunca referencia `ui/admin` | Hoy sí: `DestinoNotificacion` (ReservasAdminFragment, VehiculosAdminFragment), `ObraDetalleFragment` (`editarPersonal`); y desde residente: `ReservaAdapter`, `ReservaConZona`, `ZonaAdapter`, `ResidenteAdapter` | Esos adaptadores y `editarPersonal` pasan a `ui/comun`; `DestinoNotificacion` se parte en interfaz común + implementación por flavor |
| 11 | Un grafo de navegación por flavor | `nav_graph.xml` tiene 42 destinos, los compartidos están mezclados; una clase ausente en un flavor revienta al inflar, no al compilar | `nav_comun.xml` en main (`<include>`) con chat, cuenta, obra detalle, cuotas, reservar zona, residentes, legal…; cada flavor solo declara los suyos. Verificación: script que cruza `android:name` del grafo con las clases del flavor |
| 12 | Recursos | Layouts/strings de pantallas del otro flavor seguirían en `src/main/res` | Se dejan en main (los recursos sin referencias los quita el shrinker en release). Mover layouts es opcional y de bajo valor |
| 13 | "20 tests de integración" | 26 clases de test | Todos deben seguir verdes; V15–V18 no pueden alterar sus datos |
| 14 | "Biometría nunca guarda contraseña" | Hoy `AccesoBiometrico` cifra usuario+contraseña | Se reemplaza por token opaco por dispositivo; quien tenga huella activada hoy tendrá que reactivarla una vez (se migra borrando el dato antiguo) |

## 1. Bloques (orden de ejecución)

| Bloque | Contenido | Repos | Riesgo |
|---|---|---|---|
| B1 | Build: `TIPO_APP`, `git mv`, `nav_comun`, `ConfigApp`, `ApiComun/ApiResidente/ApiAdmin`, header `X-App-Tipo`, quitar `LoginSelector` | Front | Alto (muchos moves) |
| B2 | V15 + `TokenService`/saludo/`token_version`/logout/`LoginResponse` + `enforce` + reglas cuotas + banderas arrendatario + vehículos B1 + `SessionManager`/`MainActivity` + chip ocupación A1/A2 + A3 | Ambos | Alto |
| B3 | V16 (publicaciones) + filtros/lectura/fijar + UI | Ambos | Medio |
| B4 | V16 (biometría, legales, dispositivo) + `credenciales_dispositivo` + `AccesoBiometrico` nuevo + consentimientos + solicitudes de datos + push por app | Ambos | Alto |
| B5 | V17 portería + rol PORTERIA + `@Scheduled` + push alta prioridad + UI | Ambos | Medio |
| B6 | Wompi sandbox: checkout + webhook + `MiCarteraFragment` | Ambos | Medio (necesita claves sandbox del usuario) |
| B7 | V18 trabajadores/turnos/nómina/presupuesto + UI | Ambos | Medio |
| B8 | `SeguridadIntegrationTest` ampliada (matriz 200/403 por rol) + builds de ambos flavors | Back | Bajo |

Las migraciones se numeran por orden de bloque, así que V16 se escribe en B3 (publicaciones) y se **extiende en B4**; como Flyway no permite editar una migración aplicada, propongo **partir V16 en dos**: `V16__publicaciones_extendidas.sql` y `V17__biometria_y_legal.sql`, y renumerar portería/pagos a `V18` y personal a `V19`. (Pedido original: V15–V18; esto es V15–V19.)

## 2. Migraciones Flyway (idempotentes, reversa comentada al final)

- **V15__ocupacion_banderas_y_seguridad_sesion**: `apartamento` + `tipo_ocupacion` (CHECK), 3 banderas, `token_version`; sync `ARRENDATARIO` desde residentes titulares aprobados (`tipo_habitante='ARRENDATARIO'`, `estado='APROBADO'`); `administrador`: `DROP CONSTRAINT IF EXISTS ck_administrador_rol` + re-crear con `PORTERIA`, `token_version`, `activo`; `residente.token_version`; `configuracion_conjunto` + semilla `vehiculos_alta_solo_admin=false`. Ojo: `cupo_equipo` (V13) también tiene `ck_cupo_equipo_rol` solo con ADMIN/CONTADOR → se amplía igual para poder fijar cupo de PORTERIA.
- **V16__publicaciones_extendidas**: columnas de `publicacion` + `publicacion_lectura` + índice `(audiencia, torre)`.
- **V17__biometria_y_legal**: `credenciales_dispositivo`; `textos_legales(id, tipo, version, contenido, vigente_desde)` UNIQUE(tipo, version); `consentimientos(id, sujeto, texto_id, aceptado_en, ip)`; `solicitudes_datos(id, sujeto, tipo CONSULTA|SUPRESION, estado, detalle, creada_en, resuelta_en, resuelta_por)`; `dispositivo` + `tipo_app`, `application_id`.
- **V18__porteria_y_pasarela_pagos**: `visita_porteria`, `transaccion_pago` (+ `firma_webhook`, índice por `cuota_id`).
- **V19__trabajadores_conjunto_turnos_nomina_presupuesto**: `empresa_prestadora`, `trabajador_conjunto` (CHECK `vinculo` y `cargo`), `turno_trabajador` (CHECK `hora_inicio < hora_fin`), `nomina_trabajador` (UNIQUE trabajador+periodo), `presupuesto_personal` (UNIQUE periodo+cargo).

## 3. Mover código (`git mv`) — frontend, base `app/src/main/java/com/example/conjuntoresidencial`

```bash
J=app/src/main/java/com/example/conjuntoresidencial
A=app/src/administracion/java/com/example/conjuntoresidencial
R=app/src/residente/java/com/example/conjuntoresidencial
mkdir -p $A/ui/admin $A/ui/auth $A/ui/chat $R/ui/propietario $R/ui/residentes $R/ui/auth

# 0) Primero, lo compartido que hoy vive en el lado equivocado (se queda en main)
git mv $J/ui/admin/ReservaAdapter.kt   $J/ui/comun/ReservaAdapter.kt      # incluye ReservaConZona
git mv $J/ui/admin/ZonaAdapter.kt      $J/ui/comun/ZonaAdapter.kt
git mv $J/ui/admin/ResidenteAdapter.kt $J/ui/comun/ResidenteAdapter.kt
git mv $J/ui/admin/PersonalAdapter.kt  $J/ui/obras/PersonalAdapter.kt     # junto con editarPersonal (de DialogosAdmin.kt)
git mv $J/ui/admin/NotificacionesFragment.kt $J/ui/comun/NotificacionesFragment.kt   # a confirmar: lo usan las dos apps

# 1) Administración
for f in AdminDashboardFragment ApartamentoAdapter CarteraAdminAdapter CarteraAdminFragment CarteraAdminViewModel \
         ControlObrasFragment ControlObrasViewModel DetalleApartamentoFragment DialogosAdmin DirectorioApartamentosFragment \
         DirectorioViewModel EditarZonaFragment ElegirParqueadero EquipoFragment ParqueaderosFragment PersonalObraFragment \
         PersonalObraViewModel ReservasAdminFragment ReservasAdminViewModel TorreAdapter VehiculoAdminAdapter \
         VehiculosAdminFragment VehiculosAdminViewModel ZonasAdminViewModel ZonasComunesAdminFragment; do
  git mv $J/ui/admin/$f.kt $A/ui/admin/$f.kt; done
git mv $J/ui/admin/CarteraResumenFragment.kt $A/ui/admin/CarteraResumenFragment.kt
git mv $J/ui/auth/LoginAdminFragment.kt  $A/ui/auth/LoginAdminFragment.kt
git mv $J/ui/auth/LoginAdminViewModel.kt $A/ui/auth/LoginAdminViewModel.kt
git mv $J/ui/chat/ConversacionesFragment.kt $A/ui/chat/ConversacionesFragment.kt

# 2) Residentes
for f in PropietarioDashboardFragment MiCarteraFragment MiCarteraViewModel MisReservasFragment MisReservasViewModel \
         MisVehiculosFragment MisVehiculosViewModel VehiculosPorTipoAdapter ZonasComunesPropietarioFragment ZonasPropietarioViewModel; do
  git mv $J/ui/propietario/$f.kt $R/ui/propietario/$f.kt; done
# MisObras*, RegistrarObra* los usa también ControlObrasFragment (admin) -> se quedan en main
git mv $J/ui/residentes/MiHogarFragment.kt    $R/ui/residentes/MiHogarFragment.kt
git mv $J/ui/residentes/ArrendatarioFragment.kt $R/ui/residentes/ArrendatarioFragment.kt
for f in LoginApartamentoFragment LoginApartamentoViewModel RegistroFragment RegistroViewModel; do
  git mv $J/ui/auth/$f.kt $R/ui/auth/$f.kt; done

# 3) Sale
git rm $J/ui/auth/LoginSelectorFragment.kt app/src/main/res/layout/fragment_login_selector.xml
```

(Lista de ficheros exacta se confirma con `ls` al ejecutar; si un ViewModel lo usa el otro flavor, se queda en main.)

## 4. Matriz endpoint → roles → flavor (resumen)

| Prefijo / endpoint | ADMIN | CONTADOR | PORTERIA | PROPIETARIO | ARRENDATARIO | Interfaz Retrofit |
|---|---|---|---|---|---|---|
| `POST /auth/login`, `/auth/biometria/login`, `/auth/registro/**` | público | público | público | público | público | Comun (login admin → Admin, login apto/registro → Residente) |
| `POST /auth/logout`, `/auth/biometria/activar`, `DELETE /auth/biometria/*`, cambio de contraseña | ✔ | ✔ | ✔ | ✔ | ✔ | Comun |
| `/dispositivos`, `/notificaciones`, `/chat` (sin contador/portería) | ✔ | notif. | notif. | ✔ | ✔ | Comun |
| `GET /publicaciones`, `POST /publicaciones/{id}/lectura` | ✔ | ✔ | ✖ | ✔ (filtrado) | ✔ (filtrado) | Comun |
| `POST/PUT/DELETE /publicaciones`, `PATCH …/fijar` | ✔ | ✖ | ✖ | ✖ | ✖ | Admin |
| `GET /vehiculos/apartamento/{id}` | ✔ | ✖ | ✖ | ✔ | ✔ | Comun |
| `POST/PUT/DELETE /vehiculos…` | ✔ | ✖ | ✖ | ✔ (si no `vehiculos_alta_solo_admin`) | **403** | Residente (crear) / Admin |
| `/vehiculos` (lista), estado, cupos, `/parqueaderos/**` | ✔ | ✖ | ✖ | ✖ | ✖ | Admin |
| `GET /cuotas/apartamento/{id}` | ✔ | ✔ | ✖ | ✔ | si `arrendatario_ve_cartera` | Comun |
| `POST /cuotas/{id}/comprobante`, `POST /pagos/checkout` | — | — | ✖ | ✔ | si `arrendatario_puede_pagar` | Residente |
| `PUT/DELETE /cuotas/{id}`, generar cuotas | ✔ | **403** | ✖ | ✖ | ✖ | Admin |
| `GET /cartera/**`, solicitudes (crear) | ✔ | ✔ | ✖ | ✖ | ✖ | Admin |
| `PUT /cartera/solicitudes/*` | ✔ | ✖ | ✖ | ✖ | ✖ | Admin |
| `GET /obras/**` del apartamento | ✔ | ✖ | ✖ | ✔ | si `arrendatario_ve_obras` | Comun |
| `/obras` lista, estado, aprobación | ✔ | ✖ | ✖ | ✖ | ✖ | Admin |
| `PATCH /apartamentos/{id}/ocupacion-y-banderas` | ✔ | ✖ | ✖ | ✖ | ✖ | Admin |
| `GET/POST/PATCH /porteria/**` | ✔ | ✖ | ✔ | ✖ | ✖ | Admin |
| `PATCH /porteria/visitas/{id}/respuesta` (autorizar/rechazar) | ✖ | ✖ | ✔ (por llamada) | ✔ | ✔ | Residente |
| `GET /porteria/visitas/pendientes` (las del apto) | — | — | — | ✔ | ✔ | Residente |
| `POST /pagos/checkout` | — | — | — | ✔ | si puede pagar | Residente |
| `POST /pagos/webhook/wompi` | público (firma SHA-256) | | | | | — (servidor) |
| `/admin/trabajadores/**`, empresas, turnos, nómina, `/admin/presupuesto-personal/**` | ✔ | solo `GET …/resumen` | ✖ | ✖ | ✖ | Admin |
| `POST /solicitudes-datos` | ✔ | ✔ | ✔ | ✔ | ✔ | Comun |
| `GET/PUT /solicitudes-datos` (gestión) | ✔ | ✖ | ✖ | ✖ | ✖ | Admin |

## 5. Archivos nuevos (principales)

Backend: `V15…V19*.sql`, `security/TokenVersionValidator`, `serviceimpl/CredencialDispositivoService`, `controller/BiometriaController`, `TextoLegalController`+service, `SolicitudDatosController`, `PorteriaController`+`VisitaPorteriaService`+`@Scheduled ExpiradorVisitas`, `PagoCheckoutController`+`WompiService`+`WompiWebhookController`, `TrabajadorConjuntoController`+`ServiceImpl`, modelos/repos/DTOs de cada tabla, tests `PorteriaIntegrationTest`, `PagosWompiIntegrationTest`, `TrabajadoresSeguridadTest`.

Frontend: `ConfigApp` (×2), `nav_comun.xml`, `nav_graph.xml` (×2), `ApiComun/ApiResidente/ApiAdmin`, `DestinosApp` (×2), pantallas de Portería (`PorteriaFragment`, `VisitaRespuestaDialog`) y de Personal/Presupuesto (`PersonalFragment`, `EmpresasFragment`, `TurnosFragment`, `PresupuestoFragment`) en administracion; `ui/comun/ConsentimientoDialog`, `SolicitudDatosFragment`.

## 6. Archivos modificados (principales)

Backend: `TokenService`, `LoginResponse`, `AuthController`/`AuthServiceImpl`, `JwtConfig`, `SecurityConfig`, `application.properties`, `ApartamentoController/ServiceImpl`, `VehiculoServiceImpl`, `CuotaServiceImpl`, `PagoServiceImpl`, `ObraServiceImpl`, `PublicacionServiceImpl/Controller/DTO/Model`, `Dispositivo*`, `AvisosPush`, `EquipoService` (`AdministradorController`, crear PORTERIA), `DatosDemo`.

Frontend: `app/build.gradle.kts`, `AndroidManifest`, `MainActivity`, `ConjuntoApp`, `RetrofitClient`, `AuthInterceptor`, `SessionManager`, `AccesoBiometrico`, `InicioSesion`, `DataClasses`, `DestinoNotificacion`, `ObraDetalleFragment`, `MisVehiculosFragment`, `DirectorioApartamentosFragment`, `DetalleApartamentoFragment`, `PublicarFragment`, `PublicacionAdapter`, `LegalFragment`, `DocumentoFragment`, `terminos.txt`, `privacidad.txt`, `AvisosCelular`, `ServicioMensajes`, `MiCarteraFragment`, `AdminDashboardFragment`, `EquipoFragment`.

## 7. Nota sobre la forma de entrega

El prompt pide pegar el código completo de cada archivo en la respuesta. Con ~150 archivos eso no cabe ni se puede verificar; se escribe directamente en el repo, un bloque por rama, y la evidencia es `./mvnw test` + `./gradlew assembleResidenteDebug assembleAdministracionDebug` verdes por bloque.

## 8. Decisiones que necesito del usuario

1. ¿Aceptas partir V16 en V16+V17 (y que portería/pagos sean V18 y personal V19)?
2. ¿Tienes claves sandbox de Wompi (public key, integrity secret, events secret)? Sin ellas B6 queda probado solo con firmas calculadas en tests.
3. ¿Confirmas poner `SECURITY_ENFORCE=true` en Render al desplegar B2?
