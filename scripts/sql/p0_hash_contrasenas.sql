-- =====================================================================
-- P0 - Migrar contraseñas en texto plano a BCrypt
-- =====================================================================
-- Cuándo: una sola vez, después de desplegar la fase A del backend.
-- Dónde:  editor SQL de Supabase.
-- Antes:  HACER COPIA DE SEGURIDAD de las tablas apartamento y administrador.
--
-- Es idempotente: solo convierte los valores que aún no son un hash BCrypt,
-- así que ejecutarlo dos veces no cambia nada.
--
-- No es imprescindible: el backend ya migra cada contraseña a BCrypt la
-- primera vez que su dueño inicia sesión. Este script cubre las cuentas
-- que no vuelvan a iniciar sesión, para no dejar ninguna en texto plano.
--
-- pgcrypto genera hashes "$2a$", compatibles con BCryptPasswordEncoder de Spring.
-- En Supabase la extensión vive en el esquema "extensions".
-- =====================================================================

create extension if not exists pgcrypto with schema extensions;

-- 1. Revisar cuántas contraseñas siguen en texto plano (antes)
select 'apartamento' as tabla, count(*) as en_texto_plano
from apartamento where contrasena !~ '^\$2[aby]?\$\d{2}\$'
union all
select 'administrador', count(*)
from administrador where contrasena !~ '^\$2[aby]?\$\d{2}\$';

-- 2. Convertir
begin;

update apartamento
set contrasena = extensions.crypt(contrasena, extensions.gen_salt('bf', 10))
where contrasena !~ '^\$2[aby]?\$\d{2}\$';

update administrador
set contrasena = extensions.crypt(contrasena, extensions.gen_salt('bf', 10))
where contrasena !~ '^\$2[aby]?\$\d{2}\$';

commit;

-- 3. Verificar (después): ambas cuentas deben ser 0
select 'apartamento' as tabla, count(*) as en_texto_plano
from apartamento where contrasena !~ '^\$2[aby]?\$\d{2}\$'
union all
select 'administrador', count(*)
from administrador where contrasena !~ '^\$2[aby]?\$\d{2}\$';
