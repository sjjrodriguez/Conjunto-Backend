-- P11 bloques 6 y 7: el contador maneja la cartera de cada apartamento y la facturación, y todo
-- lo que cambia va como solicitud al administrador, con versiones e historial.

-- Coeficiente de copropiedad de cada apartamento (para facturar por coeficiente)
ALTER TABLE apartamento ADD COLUMN coeficiente NUMERIC(9,6);

-- Una cuota ahora tiene concepto (administración, multa, parqueadero...), referencia de pago fija
-- (la usa Wompi), la solicitud que la creó y, si se pagó en la oficina, cómo se pagó
ALTER TABLE cuota ADD COLUMN concepto VARCHAR(80) NOT NULL DEFAULT 'Administración';
ALTER TABLE cuota ADD COLUMN descripcion VARCHAR(300);
ALTER TABLE cuota ADD COLUMN referencia_pago VARCHAR(40);
ALTER TABLE cuota ADD COLUMN solicitud_id BIGINT;
ALTER TABLE cuota ADD COLUMN medio_pago VARCHAR(30);
ALTER TABLE cuota ADD COLUMN pago_referencia VARCHAR(80);
ALTER TABLE cuota ADD COLUMN pago_nota VARCHAR(300);
UPDATE cuota SET referencia_pago = 'MC' || conjunto_id || '-' || id WHERE referencia_pago IS NULL;
CREATE UNIQUE INDEX uk_cuota_referencia_pago ON cuota (referencia_pago);
CREATE INDEX idx_cuota_solicitud ON cuota (solicitud_id);
-- Varias cuotas del mismo mes si son de distinto concepto
ALTER TABLE cuota DROP CONSTRAINT IF EXISTS uk_cuota_apartamento_periodo;
ALTER TABLE cuota ADD CONSTRAINT uk_cuota_apartamento_periodo_concepto UNIQUE (apartamento_id, periodo, concepto);

-- Solicitudes: más tipos, devueltas para corregir, versiones y el detalle completo de lo pedido
ALTER TABLE solicitud_contable DROP CONSTRAINT IF EXISTS ck_solicitud_tipo;
ALTER TABLE solicitud_contable ADD CONSTRAINT ck_solicitud_tipo
    CHECK (tipo IN ('FACTURACION', 'PAGO_MANUAL', 'CUOTA_NUEVA', 'CUOTA_CAMBIO', 'CUOTA_ANULAR'));
ALTER TABLE solicitud_contable ADD COLUMN apartamento_id BIGINT REFERENCES apartamento (id) ON DELETE CASCADE;
ALTER TABLE solicitud_contable ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE solicitud_contable ADD COLUMN edicion BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE solicitud_contable ADD COLUMN detalle TEXT;
ALTER TABLE solicitud_contable ADD COLUMN detalle_aprobado TEXT;
ALTER TABLE solicitud_contable ADD COLUMN nota VARCHAR(500);
ALTER TABLE solicitud_contable ADD COLUMN revisada_por VARCHAR(100);
ALTER TABLE solicitud_contable ADD COLUMN actualizada_en TIMESTAMP;
CREATE INDEX idx_solicitud_apartamento ON solicitud_contable (apartamento_id);

-- Lo que pasó con cada solicitud: enviada, corregida, editada, aprobada o rechazada (con su versión)
CREATE TABLE solicitud_contable_historial (
    id            BIGSERIAL PRIMARY KEY,
    conjunto_id   BIGINT       NOT NULL REFERENCES conjunto (id),
    solicitud_id  BIGINT       NOT NULL REFERENCES solicitud_contable (id) ON DELETE CASCADE,
    version       INT          NOT NULL,
    accion        VARCHAR(20)  NOT NULL,
    detalle       TEXT,
    motivo        TEXT,
    autor         VARCHAR(100),
    creado_en     TIMESTAMP    NOT NULL,
    CONSTRAINT ck_historial_accion CHECK (accion IN ('ENVIADA', 'CORREGIDA', 'EDITADA', 'APROBADA', 'RECHAZADA'))
);
CREATE INDEX idx_historial_solicitud ON solicitud_contable_historial (solicitud_id, id);

ALTER TABLE solicitud_contable_historial ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON solicitud_contable_historial FROM anon;
        REVOKE ALL ON SEQUENCE solicitud_contable_historial_id_seq FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON solicitud_contable_historial FROM authenticated;
        REVOKE ALL ON SEQUENCE solicitud_contable_historial_id_seq FROM authenticated;
    END IF;
END $$;
