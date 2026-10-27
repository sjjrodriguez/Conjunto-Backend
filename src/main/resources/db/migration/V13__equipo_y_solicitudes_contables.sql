-- P5 bloque 7: equipo de la administración y aprobación de lo que manda el contador.
-- El administrador principal (el más antiguo) es el único que maneja el equipo; las demás
-- cuentas de administración son asistentes. Cada cuenta lleva sus datos personales.

ALTER TABLE administrador ADD COLUMN principal BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE administrador ADD COLUMN nombre_completo VARCHAR(150);
ALTER TABLE administrador ADD COLUMN documento VARCHAR(30);
ALTER TABLE administrador ADD COLUMN telefono VARCHAR(30);
ALTER TABLE administrador ADD COLUMN correo VARCHAR(150);

UPDATE administrador SET principal = TRUE
WHERE id = (SELECT MIN(id) FROM administrador WHERE rol = 'ADMIN');

-- Cuántas cuentas de cada rol puede crear el principal (sin fila = sin límite)
CREATE TABLE cupo_equipo (
    rol    VARCHAR(20) PRIMARY KEY,
    maximo INTEGER     NOT NULL,
    CONSTRAINT ck_cupo_equipo_rol CHECK (rol IN ('ADMIN', 'CONTADOR')),
    CONSTRAINT ck_cupo_equipo_maximo CHECK (maximo >= 0)
);

-- Facturación del mes y pagos sin comprobante que registra el contador: esperan al administrador
CREATE TABLE solicitud_contable (
    id             BIGSERIAL PRIMARY KEY,
    tipo           VARCHAR(20)   NOT NULL,
    estado         VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    periodo        VARCHAR(7),
    valor          NUMERIC(12,2),
    fecha_limite   DATE,
    cuota_id       BIGINT REFERENCES cuota(id) ON DELETE CASCADE,
    solicitada_por VARCHAR(100)  NOT NULL,
    creada_en      TIMESTAMP     NOT NULL,
    revisada_en    TIMESTAMP,
    motivo_rechazo TEXT,
    CONSTRAINT ck_solicitud_tipo CHECK (tipo IN ('FACTURACION', 'PAGO_MANUAL')),
    CONSTRAINT ck_solicitud_estado CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA'))
);
CREATE INDEX idx_solicitud_estado ON solicitud_contable (estado);
