-- P7 bloque 1: varios conjuntos en la misma base. Cada fila pertenece a un conjunto (conjunto_id)
-- y Hibernate filtra por él (@TenantId en EntidadConjunto). Todo lo que ya existe pasa al
-- conjunto 1, sin perder datos. Los textos legales y los consentimientos siguen siendo de toda la
-- plataforma. Incremental e idempotente.

CREATE TABLE IF NOT EXISTS conjunto (
    id           BIGSERIAL PRIMARY KEY,
    nombre       VARCHAR(120) NOT NULL,
    departamento VARCHAR(60)  NOT NULL,
    municipio    VARCHAR(80)  NOT NULL,
    direccion    VARCHAR(160),
    activo       BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- El conjunto que ya existía. Nombre y ubicación se corrigen desde la app de plataforma (bloque 3)
INSERT INTO conjunto (id, nombre, departamento, municipio)
VALUES (1, 'Conjunto principal', 'Bogotá D.C.', 'Bogotá D.C.')
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('conjunto', 'id'), (SELECT MAX(id) FROM conjunto));

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'administrador', 'apartamento', 'conversacion', 'credenciales_dispositivo', 'cuota',
        'cupo_apartamento', 'cupo_equipo', 'cupo_vehiculo', 'dispositivo', 'empresa_prestadora',
        'mensaje_chat', 'nomina_trabajador', 'notificacion', 'obra', 'pago', 'parqueadero',
        'personal_obra', 'presupuesto_personal', 'publicacion', 'publicacion_adjunto',
        'publicacion_lectura', 'reserva', 'residente', 'solicitud_contable', 'solicitudes_datos',
        'trabajador_conjunto', 'transaccion_pago', 'turno_trabajador', 'vehiculo', 'visita_porteria',
        'visitante_frecuente', 'zona_bloqueo', 'zona_comun', 'zona_horario', 'configuracion_conjunto']
    LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS conjunto_id BIGINT NOT NULL DEFAULT 1', t);
        -- Sin valor por defecto: una fila nueva sin conjunto es un error, no del conjunto 1
        EXECUTE format('ALTER TABLE %I ALTER COLUMN conjunto_id DROP DEFAULT', t);
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_' || t || '_conjunto') THEN
            EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (conjunto_id) REFERENCES conjunto (id)',
                           t, 'fk_' || t || '_conjunto');
        END IF;
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (conjunto_id)', 'ix_' || t || '_conjunto', t);
    END LOOP;
END $$;

-- Lo que era único en el conjunto ahora es único dentro de cada conjunto.
-- El usuario del administrador venía de Hibernate con un nombre generado: se busca por columna
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conname FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attname = 'usuario'
        WHERE t.relname = 'administrador' AND c.contype = 'u' AND c.conkey = ARRAY[a.attnum]::SMALLINT[]
    LOOP
        EXECUTE format('ALTER TABLE administrador DROP CONSTRAINT %I', r.conname);
    END LOOP;

    -- Los ajustes: la clave se repite en cada conjunto
    FOR r IN
        SELECT c.conname FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE t.relname = 'configuracion_conjunto' AND c.contype = 'p'
    LOOP
        EXECUTE format('ALTER TABLE configuracion_conjunto DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;
ALTER TABLE configuracion_conjunto ADD CONSTRAINT pk_configuracion_conjunto PRIMARY KEY (conjunto_id, clave);

ALTER TABLE administrador DROP CONSTRAINT IF EXISTS uk_administrador_usuario;
ALTER TABLE administrador ADD CONSTRAINT uk_administrador_usuario UNIQUE (conjunto_id, usuario);

ALTER TABLE parqueadero DROP CONSTRAINT IF EXISTS uk_parqueadero_numero;
ALTER TABLE parqueadero ADD CONSTRAINT uk_parqueadero_numero UNIQUE (conjunto_id, numero);

ALTER TABLE empresa_prestadora DROP CONSTRAINT IF EXISTS uk_empresa_prestadora_nombre;
ALTER TABLE empresa_prestadora ADD CONSTRAINT uk_empresa_prestadora_nombre UNIQUE (conjunto_id, nombre);

ALTER TABLE trabajador_conjunto DROP CONSTRAINT IF EXISTS uk_trabajador_conjunto_documento;
ALTER TABLE trabajador_conjunto ADD CONSTRAINT uk_trabajador_conjunto_documento UNIQUE (conjunto_id, documento);

ALTER TABLE presupuesto_personal DROP CONSTRAINT IF EXISTS uk_presupuesto_periodo_cargo;
ALTER TABLE presupuesto_personal ADD CONSTRAINT uk_presupuesto_periodo_cargo UNIQUE (conjunto_id, periodo, cargo);

-- Reversa (manual): quitar las columnas conjunto_id (y sus llaves e índices), devolver las
-- unicidades anteriores (sin conjunto_id), la llave de configuracion_conjunto a (clave) y
-- DROP TABLE conjunto. Solo es seguro mientras exista un único conjunto.
