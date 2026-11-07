-- P10 bloque 7: la foto del visitante y la del paquete (ruta en el bucket privado) y el chat
-- temporal entre el residente y la portería cuando rechazan una visita. Idempotente.

ALTER TABLE visita_porteria ADD COLUMN IF NOT EXISTS foto_ruta VARCHAR(200);
ALTER TABLE correspondencia ADD COLUMN IF NOT EXISTS foto_ruta VARCHAR(200);

CREATE TABLE IF NOT EXISTS mensaje_visita (
    id            BIGSERIAL PRIMARY KEY,
    conjunto_id   BIGINT       NOT NULL,
    visita_id     BIGINT       NOT NULL,
    de_residente  BOOLEAN      NOT NULL,
    autor         VARCHAR(150),
    texto         VARCHAR(500) NOT NULL,
    creado_en     TIMESTAMP    NOT NULL,
    CONSTRAINT fk_mensaje_visita_conjunto FOREIGN KEY (conjunto_id) REFERENCES conjunto (id),
    CONSTRAINT fk_mensaje_visita_visita FOREIGN KEY (visita_id) REFERENCES visita_porteria (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mensaje_visita_visita ON mensaje_visita (visita_id, creado_en);

-- Como el resto de tablas (V24): sin acceso por la API pública de Supabase
ALTER TABLE mensaje_visita ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON mensaje_visita FROM anon;
        REVOKE ALL ON SEQUENCE mensaje_visita_id_seq FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON mensaje_visita FROM authenticated;
        REVOKE ALL ON SEQUENCE mensaje_visita_id_seq FROM authenticated;
    END IF;
END $$;
