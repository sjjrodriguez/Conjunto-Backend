-- P9 bloque 3: correspondencia de la portería. Paquetes, cartas, documentos y recibos públicos que
-- llegan a la portería para un apartamento, y quién los recogió. Un recibo "para todos" es una fila
-- por apartamento con el mismo lote. Idempotente.

CREATE TABLE IF NOT EXISTS correspondencia (
    id             BIGSERIAL PRIMARY KEY,
    conjunto_id    BIGINT       NOT NULL,
    apartamento_id BIGINT       NOT NULL,
    tipo           VARCHAR(20)  NOT NULL,
    remitente      VARCHAR(150),
    nota           VARCHAR(300),
    lote           VARCHAR(40),
    estado         VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    recibida_en    TIMESTAMP    NOT NULL,
    guardia_nombre VARCHAR(150),
    registrada_por VARCHAR(60),
    entregada_en   TIMESTAMP,
    entregada_a    VARCHAR(150),
    entregada_por  VARCHAR(150),
    CONSTRAINT fk_correspondencia_conjunto FOREIGN KEY (conjunto_id) REFERENCES conjunto (id),
    CONSTRAINT fk_correspondencia_apartamento FOREIGN KEY (apartamento_id) REFERENCES apartamento (id) ON DELETE CASCADE,
    CONSTRAINT ck_correspondencia_tipo CHECK (tipo IN ('PAQUETE', 'CARTA', 'DOCUMENTO', 'RECIBO')),
    CONSTRAINT ck_correspondencia_estado CHECK (estado IN ('PENDIENTE', 'ENTREGADA'))
);

CREATE INDEX IF NOT EXISTS idx_correspondencia_conjunto_estado ON correspondencia (conjunto_id, estado, recibida_en);
CREATE INDEX IF NOT EXISTS idx_correspondencia_apartamento ON correspondencia (apartamento_id, recibida_en);

-- Como el resto de tablas (V24): sin acceso por la API pública de Supabase
ALTER TABLE correspondencia ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON correspondencia FROM anon;
        REVOKE ALL ON SEQUENCE correspondencia_id_seq FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON correspondencia FROM authenticated;
        REVOKE ALL ON SEQUENCE correspondencia_id_seq FROM authenticated;
    END IF;
END $$;
