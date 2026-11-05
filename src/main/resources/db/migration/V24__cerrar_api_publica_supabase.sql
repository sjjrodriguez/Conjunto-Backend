-- Seguridad: Supabase publica una API REST (PostgREST) sobre el esquema public para los roles
-- anon y authenticated. La app no la usa (el backend se conecta directo como dueño de las
-- tablas), así que se cierra: RLS activado sin políticas en todas las tablas y sin permisos para
-- esos roles. El dueño de las tablas se salta RLS, así que el backend no cambia. Idempotente;
-- en una base sin esos roles (Postgres de las pruebas) solo activa RLS. flyway_schema_history
-- queda fuera: Flyway la tiene bloqueada mientras migra y el ALTER o el REVOKE se quedarían
-- esperando; en Supabase se cerró aparte, con el SQL directo.

DO $$
DECLARE
    t RECORD;
    roles BOOLEAN := EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')
                     AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated');
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history' LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);
        IF roles THEN
            EXECUTE format('REVOKE ALL ON public.%I FROM anon, authenticated', t.tablename);
        END IF;
    END LOOP;

    IF roles THEN
        REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;
        REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM anon, authenticated;
        -- Las tablas que creen las migraciones siguientes tampoco quedan expuestas
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM anon, authenticated;
    END IF;
END $$;
