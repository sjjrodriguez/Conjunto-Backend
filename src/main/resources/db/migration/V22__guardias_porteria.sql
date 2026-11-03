-- P7 bloque 4: guardias de la recepción. El celular entra con la cuenta de portería y cada guardia
-- abre su turno con su PIN; la visita guarda el nombre de quien la registró. Idempotente.

CREATE TABLE IF NOT EXISTS guardia_porteria (
    id          BIGSERIAL PRIMARY KEY,
    conjunto_id BIGINT       NOT NULL,
    nombre      VARCHAR(150) NOT NULL,
    documento   VARCHAR(30)  NOT NULL,
    pin         VARCHAR(255) NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guardia_porteria_conjunto FOREIGN KEY (conjunto_id) REFERENCES conjunto (id),
    CONSTRAINT uk_guardia_documento UNIQUE (conjunto_id, documento)
);

CREATE INDEX IF NOT EXISTS ix_guardia_porteria_conjunto ON guardia_porteria (conjunto_id);

ALTER TABLE visita_porteria ADD COLUMN IF NOT EXISTS guardia_nombre VARCHAR(150);
