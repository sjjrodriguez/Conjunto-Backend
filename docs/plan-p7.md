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
