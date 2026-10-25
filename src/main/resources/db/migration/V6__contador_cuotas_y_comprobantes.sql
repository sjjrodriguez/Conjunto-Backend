-- P4 bloque 7: contador, facturación mensual y comprobantes de pago.

-- Cuentas de la administración: ADMIN (todo) o CONTADOR (solo la cartera)
ALTER TABLE administrador ADD COLUMN rol VARCHAR(20) NOT NULL DEFAULT 'ADMIN';
ALTER TABLE administrador ADD CONSTRAINT ck_administrador_rol CHECK (rol IN ('ADMIN', 'CONTADOR'));

-- Cuota de administración de un apartamento en un mes ("2026-11"). El residente sube el
-- comprobante (queda EN_REVISION) y el contador la marca PAGADA o RECHAZADA.
-- La tabla pago sigue siendo el resumen de la cartera: el backend la recalcula con las cuotas.
CREATE TABLE cuota (
    id                    BIGSERIAL PRIMARY KEY,
    apartamento_id        BIGINT        NOT NULL,
    periodo               VARCHAR(7)    NOT NULL,
    valor                 NUMERIC(12,2) NOT NULL,
    fecha_limite          DATE          NOT NULL,
    estado                VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    comprobante_ruta      VARCHAR(500),
    comprobante_tipo      VARCHAR(100),
    comprobante_subido_en TIMESTAMP,
    motivo_rechazo        TEXT,
    pagada_en             TIMESTAMP,
    CONSTRAINT fk_cuota_apartamento FOREIGN KEY (apartamento_id) REFERENCES apartamento (id),
    CONSTRAINT uk_cuota_apartamento_periodo UNIQUE (apartamento_id, periodo),
    CONSTRAINT ck_cuota_valor CHECK (valor > 0),
    CONSTRAINT ck_cuota_estado CHECK (estado IN ('PENDIENTE', 'EN_REVISION', 'PAGADA', 'RECHAZADA'))
);
CREATE INDEX idx_cuota_periodo ON cuota (periodo);
CREATE INDEX idx_cuota_estado ON cuota (estado);
