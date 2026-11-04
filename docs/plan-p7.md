# Plan P7: varios conjuntos, plataforma, recepción y dispositivos

Aprobado el 2026-11-01. Ramas apiladas `p7/bN-...` en los dos repos; merge solo cuando se pida.
Cada bloque: compilar, pruebas del backend y commit. Una sola prueba en el emulador al final.

| Bloque | Qué | Depende de |
|---|---|---|
| B1 | Multi-conjunto en el backend: tabla `conjunto`, `conjunto_id` en todo, token y filtros por conjunto | — |
| B2 | Selector departamento → municipio → conjunto en las apps, editable desde Cuenta | B1 |
| B3 | App Plataforma: crear, editar y suspender conjuntos y su administrador principal, con auditoría | B1 |
| B4 | App Recepción: tercer flavor, PIN por guardia, bloqueo por inactividad | — |
| B5 | Límite de dispositivos por cuenta y código de verificación en cada dispositivo nuevo | Llave de Brevo (lista) |

## B1: cómo funciona

- `EntidadConjunto` (superclase de todas las entidades salvo textos legales, consentimientos y
  `ConfiguracionConjunto`) tiene `conjunto_id` con `@TenantId`. Hibernate lo llena al guardar y
  filtra por él todas las consultas JPQL y derivadas.
- Hibernate 6.4 **no** filtra la carga por id (`em.find`): `RepositorioPorConjunto` (base de todos
  los repositorios) descarta en `findById` lo que es de otro conjunto. `getReferenceById` no se
  revisa: úsalo solo con ids que salen del token.
- `FiltroConjunto` fija el conjunto de cada petición: con sesión, el claim `cid` del token (la
  cabecera se ignora); sin sesión (login, registro), la cabecera `X-Conjunto`. Sin ninguno, el
  conjunto 1 (la app anterior sigue funcionando igual).
- `ConjuntoContexto.en(id, ...)` corre algo en otro conjunto; `comoRaiz(...)` ve todos (solo para
  tareas internas, nunca para guardar). La sesión de Hibernate se abre con el conjunto que haya al
  empezar la transacción: cambiarlo dentro de una transacción no tiene efecto.
- SQL directo (`JdbcTemplate`) no se filtra: se usa a propósito para la versión de sesión, el
  token de un celular que cambia de conjunto y buscar el conjunto de un evento de Wompi.
- Los avisos `@Async` heredan el conjunto (TaskDecorator). La expiración de visitas corre como raíz.
- Únicos por conjunto: usuario de administrador, número de parqueadero, empresa prestadora,
  cédula de trabajador, presupuesto por periodo y cargo, y la clave de cada ajuste.
- Público: `GET /api/conjuntos/ubicaciones` (departamentos con sus municipios que tienen conjuntos
  activos) y `GET /api/conjuntos?departamento=&municipio=`.
- El login devuelve además `conjuntoId` y `conjuntoNombre`.

Pendiente después de desplegar V20: el conjunto 1 queda como "Conjunto principal" en Bogotá D.C.;
cambiar nombre, departamento y municipio por los reales (en B3 desde la app de plataforma o con un
UPDATE en Supabase).

## B3: cómo funciona

- `OperadorPlataforma` (tabla `operador_plataforma`, V21) no es de ningún conjunto. El primero se
  crea al arrancar con `PLATAFORMA_USUARIO` y `PLATAFORMA_CONTRASENA` (mínimo 10 caracteres) si
  aún no existe; en la demo, `plataforma` / `plataforma-demo`.
- Su token (rol `PLATAFORMA`, sujeto `plataforma:ID`, 2 horas, sin `cid`) solo sirve en
  `/api/plataforma/**`: `autenticado()` y `sesion()` lo rechazan, así que no ve datos de residentes.
- `/api/plataforma`: `login`, `logout`, `contrasena`, `departamentos`, `conjuntos` (lista con
  apartamentos, cuentas y principal), `conjuntos/{id}` (ver y editar), `conjuntos/{id}/estado`
  (suspender o reactivar, con motivo), `conjuntos/{id}/administracion` (cuentas, sin contraseñas),
  `conjuntos/{id}/principal/restablecer` y `auditoria?conjunto=`.
- Crear un conjunto crea también su administrador principal con una contraseña temporal de 12
  caracteres que se muestra una sola vez. Restablecerla cierra sus sesiones y borra su huella.
- Las cuentas de otro conjunto se escriben con SQL directo (el filtro de Hibernate se fija al abrir
  la sesión): conjunto y administrador quedan en la misma transacción.
- Suspender: `FiltroConjunto` responde 403 a las sesiones del conjunto y a sus logins
  (`/api/auth/**`); deja de aparecer para elegir. `ConjuntosActivos` guarda el estado 30 s y la
  plataforma lo olvida al cambiarlo.
- El departamento debe ser uno de los 33 (`PlataformaService.DEPARTAMENTOS`); no se repite el
  nombre de un conjunto en el mismo municipio.
- Auditoría (`auditoria_plataforma`): inicio de sesión, crear, editar, suspender, reactivar y
  restablecer, con el operador y el detalle. Se lee desde la app; no se edita.

## B4: cómo funciona

- El celular de la recepción entra con la cuenta de portería del conjunto (rol `PORTERIA`, la
  crea el principal en Mi equipo). Cada guardia (`guardia_porteria`, V22) tiene nombre, documento
  y un PIN de 4 a 6 dígitos (BCrypt; no se aceptan 1111 ni escaleras como 1234).
- La administración los maneja en `/api/porteria/guardias` (crear, editar, cambiar el PIN, dar de
  baja). La recepción solo ve `GET /api/porteria/turno/guardias` (id y nombre).
- `POST /api/porteria/turno {guardiaId, pin}`: solo la cuenta de portería. Devuelve un token nuevo
  con el claim `gid` y `guardiaNombre`. Límite de 5 intentos por minuto por guardia (429).
- Las visitas que se registran con ese token guardan `guardia_nombre`; la portería y la
  administración lo ven en `guardia`, el residente no. Si el guardia se da de baja, su token de
  turno ya no registra (403) y hay que abrir otro turno.
- Sin turno (la portería desde la app de administración) todo sigue igual, sin guardia.
- El bloqueo por inactividad lo hace la app de recepción: vuelve a la pantalla de turno.
- Demo: cuenta `porteria` / `porteria-demo`; guardias Carlos Ruiz (PIN 2580) y Ana Gómez (1357).

## B5: cómo funciona

- **Una sesión por celular.** Cada celular se identifica con el id que la app ya genera para la
  huella (cabeceras `X-Dispositivo` y `X-Dispositivo-Nombre`, p. ej. "Samsung SM-S921B"). Cada
  inicio de sesión crea una fila en `dispositivo_cuenta` (cuenta + celular) con un id de sesión
  aleatorio que va en el token (claim `sid`). `VersionTokenValidator` rechaza el token si esa
  sesión ya se cerró (se consulta y se guarda 30 s en memoria, como la versión de sesión).
- **Cerrar sesión** cierra solo la de ese celular (antes se cerraban todas las de la cuenta).
  Cambiar la contraseña, darla de baja o restablecerla cierra todas; quien cambió la contraseña
  sigue en su celular con un token nuevo.
- **Límite por cuenta:** `conjunto.max_dispositivos` (por defecto 3, de 1 a 10), lo cambia la
  plataforma en los datos del conjunto. Al entrar en uno más, se cierra la sesión del celular que
  la abrió hace más tiempo y la respuesta del login lo dice (`sesionCerradaEn`).
- **Código en un celular nuevo:** si la cuenta nunca verificó ese celular, el login no devuelve
  token sino `verificacionId` y el correo oculto (`ms***@gmail.com`). Se envía un código de 6
  dígitos por Brevo que vence en 10 minutos; 5 intentos por código y hasta 3 envíos (uno por
  minuto). `POST /api/auth/verificar` con el código devuelve la sesión y deja el celular verificado:
  la próxima vez no lo pide. La huella entra sin código (la credencial ya es de ese celular).
- **A quién aplica:** cuentas de la administración (administrador, asistente, contador, portería)
  y residentes con cuenta propia. No aplica a la clave compartida del apartamento (la usa toda la
  familia y no tiene correo) ni a la plataforma (no tiene correo; su token dura 2 horas).
  Una cuenta sin correo entra sin código (queda en el log) hasta que se le agregue uno.
- **Mis dispositivos** (Cuenta): `GET /api/auth/dispositivos` lista los celulares de la cuenta
  con su sesión; `DELETE /api/auth/dispositivos/{id}` cierra esa sesión y olvida el celular (si
  vuelve a entrar, pide código).
- **Apps anteriores:** un login sin `X-Dispositivo` funciona como antes (sin código ni límite) y
  los tokens sin `sid` siguen valiendo. Cuando todos tengan la app nueva, poner
  `SESIONES_EXIGIR_DISPOSITIVO=true` en Render: el login sin celular responde 426 "Actualiza la app".
- En la demo (`app.sesiones.registrar-codigo=true`) el código también queda en el log.
