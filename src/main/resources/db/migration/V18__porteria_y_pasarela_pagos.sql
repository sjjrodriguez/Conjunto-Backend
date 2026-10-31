-- P6 bloques 5 y 6: visitas que registra la portería y transacciones de la pasarela de pagos (Wompi).
-- Incremental e idempotente. El rol PORTERIA ya quedó permitido en V15.

CREATE TABLE IF NOT EXISTS visita_porteria (
    id                  BIGSERIAL PRIMARY KEY,
    apartamento_id      BIGINT       NOT NULL,
    visitante_nombre    VARCHAR(150) NOT NULL,
    visitante_documento VARCHAR(40),
    placa               VARCHAR(20),
    motivo              VARCHAR(200),
    estado              VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    creada_en           TIMESTAMP    NOT NULL,
    vence_en            TIMESTAMP    NOT NULL,
    respondida_en       TIMESTAMP,
    respondida_por      VARCHAR(60),
    canal_respuesta     VARCHAR(20),
    registrada_por      VARCHAR(60),
    CONSTRAINT fk_visita_apartamento FOREIGN KEY (apartamento_id) REFERENCES apartamento (id) ON DELETE CASCADE,
    CONSTRAINT ck_visita_estado CHECK (estado IN ('PENDIENTE', 'AUTORIZADA', 'RECHAZADA', 'EXPIRADA')),
    CONSTRAINT ck_visita_canal CHECK (canal_respuesta IS NULL OR canal_respuesta IN ('APP', 'LLAMADA'))
);
CREATE INDEX IF NOT EXISTS idx_visita_apartamento_estado ON visita_porteria (apartamento_id, estado);
CREATE INDEX IF NOT EXISTS idx_visita_estado_vence ON visita_porteria (estado, vence_en);

CREATE TABLE IF NOT EXISTS transaccion_pago (
    id               BIGSERIAL PRIMARY KEY,
    cuota_id         BIGINT       NOT NULL,
    referencia       VARCHAR(80)  NOT NULL,
    monto_centavos   BIGINT       NOT NULL,
    moneda           VARCHAR(3)   NOT NULL DEFAULT 'COP',
    estado           VARCHAR(20)  NOT NULL DEFAULT 'CREADA',
    proveedor        VARCHAR(20)  NOT NULL DEFAULT 'WOMPI',
    id_proveedor     VARCHAR(80),
    firma_webhook    VARCHAR(128),
    creada_en        TIMESTAMP    NOT NULL,
    actualizada_en   TIMESTAMP,
    CONSTRAINT uk_transaccion_referencia UNIQUE (referencia),
    CONSTRAINT fk_transaccion_cuota FOREIGN KEY (cuota_id) REFERENCES cuota (id) ON DELETE CASCADE,
    CONSTRAINT ck_transaccion_estado CHECK (estado IN ('CREADA', 'APROBADA', 'RECHAZADA', 'ERROR', 'ANULADA'))
);
CREATE INDEX IF NOT EXISTS idx_transaccion_cuota ON transaccion_pago (cuota_id);

-- Reverso (manual):
-- DROP TABLE IF EXISTS transaccion_pago;
-- DROP TABLE IF EXISTS visita_porteria;
