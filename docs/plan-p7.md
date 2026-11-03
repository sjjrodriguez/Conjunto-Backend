# Plan P7: varios conjuntos, plataforma, recepción y dispositivos

Aprobado el 2026-11-01. Ramas apiladas `p7/bN-...` en los dos repos; merge solo cuando se pida.
Cada bloque: compilar, pruebas del backend y commit. Una sola prueba en el emulador al final.

| Bloque | Qué | Depende de |
|---|---|---|
| B1 | Multi-conjunto en el backend: tabla `conjunto`, `conjunto_id` en todo, token y filtros por conjunto | — |
| B2 | Selector departamento → municipio → conjunto en las apps, editable desde Cuenta | B1 |
| B3 | App Plataforma: crear, editar y suspender conjuntos y su administrador principal, con auditoría | B1 |
| B4 | App Recepción: tercer flavor, PIN por guardia, bloqueo por inactividad | — |
| B5 | Límite de dispositivos por cuenta y código de verificación en cada dispositivo nuevo | Llave de Brevo |

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
