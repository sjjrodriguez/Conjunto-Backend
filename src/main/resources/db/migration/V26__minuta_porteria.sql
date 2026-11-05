-- P8 bloque 5: la minuta de la portería. Lo que pasa en cada turno (rondas, novedades, paquetes,
-- incidentes), anotado por el guardia del turno. No se edita ni se borra desde la app: es el libro
-- del turno. Idempotente.

CREATE TABLE IF NOT EXISTS minuta_porteria (
    id             BIGSERIAL PRIMARY KEY,
    conjunto_id    BIGINT        NOT NULL,
    categoria      VARCHAR(20)   NOT NULL DEFAULT 'NOVEDAD',
    texto          VARCHAR(1000) NOT NULL,
    creada_en      TIMESTAMP     NOT NULL,
    guardia_nombre VARCHAR(150),
    registrada_por VARCHAR(60),
    CONSTRAINT fk_minuta_conjunto FOREIGN KEY (conjunto_id) REFERENCES conjunto (id),
    CONSTRAINT ck_minuta_categoria CHECK (categoria IN ('NOVEDAD', 'RONDA', 'PAQUETE', 'INCIDENTE', 'TURNO'))
);

CREATE INDEX IF NOT EXISTS idx_minuta_conjunto_fecha ON minuta_porteria (conjunto_id, creada_en);

-- Como el resto de tablas (V24): sin acceso por la API pública de Supabase
ALTER TABLE minuta_porteria ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON minuta_porteria FROM anon;
        REVOKE ALL ON SEQUENCE minuta_porteria_id_seq FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON minuta_porteria FROM authenticated;
        REVOKE ALL ON SEQUENCE minuta_porteria_id_seq FROM authenticated;
    END IF;
END $$;

-- El historial busca por fecha
CREATE INDEX IF NOT EXISTS idx_visita_conjunto_creada ON visita_porteria (conjunto_id, creada_en);
