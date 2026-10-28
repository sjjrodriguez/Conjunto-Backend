-- P6 bloque 3: publicaciones con título, categoría, audiencia, torre, fijado y marcas de lectura.
-- Incremental e idempotente: las publicaciones de P5 quedan como GENERAL para TODOS, sin título.
ALTER TABLE publicacion ADD COLUMN IF NOT EXISTS titulo    VARCHAR(150);
ALTER TABLE publicacion ADD COLUMN IF NOT EXISTS categoria VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE publicacion ADD COLUMN IF NOT EXISTS audiencia VARCHAR(20) NOT NULL DEFAULT 'TODOS';
ALTER TABLE publicacion ADD COLUMN IF NOT EXISTS torre     VARCHAR(20);
ALTER TABLE publicacion ADD COLUMN IF NOT EXISTS fijado    BOOLEAN     NOT NULL DEFAULT FALSE;

ALTER TABLE publicacion DROP CONSTRAINT IF EXISTS ck_publicacion_categoria;
ALTER TABLE publicacion ADD CONSTRAINT ck_publicacion_categoria
    CHECK (categoria IN ('GENERAL', 'URGENTE', 'MANTENIMIENTO', 'EVENTO', 'FINANZAS'));
ALTER TABLE publicacion DROP CONSTRAINT IF EXISTS ck_publicacion_audiencia;
ALTER TABLE publicacion ADD CONSTRAINT ck_publicacion_audiencia
    CHECK (audiencia IN ('TODOS', 'PROPIETARIOS', 'ARRENDATARIOS'));

CREATE INDEX IF NOT EXISTS idx_publicacion_fijado ON publicacion (fijado, creada_en);

-- Quién ya la leyó: sujeto = el del token (admin:ID, residente:ID, apartamento:ID)
CREATE TABLE IF NOT EXISTS publicacion_lectura (
    publicacion_id BIGINT      NOT NULL,
    sujeto         VARCHAR(60) NOT NULL,
    leido_en       TIMESTAMP   NOT NULL,
    PRIMARY KEY (publicacion_id, sujeto),
    CONSTRAINT fk_lectura_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion (id) ON DELETE CASCADE
);

-- Reverso (manual):
-- DROP TABLE IF EXISTS publicacion_lectura;
-- DROP INDEX IF EXISTS idx_publicacion_fijado;
-- ALTER TABLE publicacion DROP CONSTRAINT IF EXISTS ck_publicacion_audiencia;
-- ALTER TABLE publicacion DROP CONSTRAINT IF EXISTS ck_publicacion_categoria;
-- ALTER TABLE publicacion DROP COLUMN IF EXISTS fijado, DROP COLUMN IF EXISTS torre, DROP COLUMN IF EXISTS audiencia,
--     DROP COLUMN IF EXISTS categoria, DROP COLUMN IF EXISTS titulo;
