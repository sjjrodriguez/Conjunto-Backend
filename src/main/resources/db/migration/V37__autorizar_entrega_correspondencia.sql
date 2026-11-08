-- P12 bloque 3: para entregar la correspondencia a alguien, el residente lo autoriza (como en la
-- citofonía): quién la recoge, su documento y foto, y la respuesta; si la rechaza, chat temporal
-- con la portería. Idempotente.

ALTER TABLE correspondencia ADD COLUMN IF NOT EXISTS solicitud_estado VARCHAR(20);
ALTER TABLE correspondencia ADD COLUMN IF NOT EXISTS solicitud_recoge VARCHAR(150);
ALTER TABLE correspondencia ADD COLUMN IF NOT EXISTS solicitud_documento VARCHAR(40);
ALTER TABLE correspondencia ADD COLUMN IF NOT EXISTS solicitud_foto_ruta VARCHAR(200);
ALTER TABLE correspondencia ADD COLUMN IF NOT EXISTS solicitud_creada_en TIMESTAMP;
ALTER TABLE correspondencia ADD COLUMN IF NOT EXISTS solicitud_vence_en TIMESTAMP;
ALTER TABLE correspondencia ADD COLUMN IF NOT EXISTS solicitud_respondida_en TIMESTAMP;
ALTER TABLE correspondencia ADD COLUMN IF NOT EXISTS solicitud_respondida_por VARCHAR(100);
ALTER TABLE correspondencia ADD COLUMN IF NOT EXISTS solicitud_canal VARCHAR(20);

CREATE TABLE IF NOT EXISTS mensaje_correspondencia (
    id                 BIGSERIAL PRIMARY KEY,
    conjunto_id        BIGINT       NOT NULL,
    correspondencia_id BIGINT       NOT NULL,
    de_residente       BOOLEAN      NOT NULL,
    autor              VARCHAR(150),
    texto              VARCHAR(500) NOT NULL,
    creado_en          TIMESTAMP    NOT NULL,
    CONSTRAINT fk_mensaje_corr_conjunto FOREIGN KEY (conjunto_id) REFERENCES conjunto (id),
    CONSTRAINT fk_mensaje_corr_correspondencia FOREIGN KEY (correspondencia_id) REFERENCES correspondencia (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mensaje_corr_correspondencia ON mensaje_correspondencia (correspondencia_id, creado_en);

-- Como el resto de tablas (V24): sin acceso por la API pública de Supabase
ALTER TABLE mensaje_correspondencia ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON mensaje_correspondencia FROM anon;
        REVOKE ALL ON SEQUENCE mensaje_correspondencia_id_seq FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON mensaje_correspondencia FROM authenticated;
        REVOKE ALL ON SEQUENCE mensaje_correspondencia_id_seq FROM authenticated;
    END IF;
END $$;
