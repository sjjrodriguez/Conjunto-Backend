-- P6 bloque 7: personal del conjunto (vigilantes, aseo, mantenimiento...), empresas que lo prestan,
-- turnos, nómina mensual y presupuesto por cargo. Solo lo maneja el administrador; el contador
-- lee el resumen del presupuesto. Incremental e idempotente.

CREATE TABLE IF NOT EXISTS empresa_prestadora (
    id        BIGSERIAL PRIMARY KEY,
    nombre    VARCHAR(150) NOT NULL,
    nit       VARCHAR(40),
    contacto  VARCHAR(150),
    telefono  VARCHAR(40),
    activa    BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_empresa_prestadora_nombre UNIQUE (nombre)
);

CREATE TABLE IF NOT EXISTS trabajador_conjunto (
    id              BIGSERIAL PRIMARY KEY,
    nombre_completo VARCHAR(150) NOT NULL,
    documento       VARCHAR(40)  NOT NULL,
    telefono        VARCHAR(40),
    cargo           VARCHAR(20)  NOT NULL,
    vinculo         VARCHAR(20)  NOT NULL,
    empresa_id      BIGINT,
    salario_base    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    fecha_ingreso   DATE,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_trabajador_conjunto_documento UNIQUE (documento),
    CONSTRAINT fk_trabajador_empresa FOREIGN KEY (empresa_id) REFERENCES empresa_prestadora (id),
    CONSTRAINT ck_trabajador_cargo CHECK (cargo IN ('VIGILANTE', 'ASEO', 'JARDINERIA', 'MANTENIMIENTO', 'ADMINISTRATIVO', 'OTRO')),
    CONSTRAINT ck_trabajador_vinculo CHECK (vinculo IN ('DIRECTO', 'EMPRESA')),
    CONSTRAINT ck_trabajador_empresa CHECK ((vinculo = 'EMPRESA' AND empresa_id IS NOT NULL) OR (vinculo = 'DIRECTO' AND empresa_id IS NULL)),
    CONSTRAINT ck_trabajador_salario CHECK (salario_base >= 0)
);
CREATE INDEX IF NOT EXISTS idx_trabajador_cargo ON trabajador_conjunto (cargo, activo);

CREATE TABLE IF NOT EXISTS turno_trabajador (
    id            BIGSERIAL PRIMARY KEY,
    trabajador_id BIGINT NOT NULL,
    fecha         DATE   NOT NULL,
    hora_inicio   TIME   NOT NULL,
    hora_fin      TIME   NOT NULL,
    CONSTRAINT fk_turno_trabajador FOREIGN KEY (trabajador_id) REFERENCES trabajador_conjunto (id) ON DELETE CASCADE,
    CONSTRAINT ck_turno_horas CHECK (hora_inicio < hora_fin)
);
CREATE INDEX IF NOT EXISTS idx_turno_trabajador_fecha ON turno_trabajador (trabajador_id, fecha);

CREATE TABLE IF NOT EXISTS nomina_trabajador (
    id            BIGSERIAL PRIMARY KEY,
    trabajador_id BIGINT      NOT NULL,
    periodo       VARCHAR(7)  NOT NULL,
    salario_base  NUMERIC(12, 2) NOT NULL,
    bonificaciones NUMERIC(12, 2) NOT NULL DEFAULT 0,
    descuentos    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    neto          NUMERIC(12, 2) NOT NULL,
    estado        VARCHAR(20) NOT NULL DEFAULT 'GENERADA',
    CONSTRAINT uk_nomina_trabajador_periodo UNIQUE (trabajador_id, periodo),
    CONSTRAINT fk_nomina_trabajador FOREIGN KEY (trabajador_id) REFERENCES trabajador_conjunto (id),
    CONSTRAINT ck_nomina_estado CHECK (estado IN ('GENERADA', 'PAGADA'))
);

CREATE TABLE IF NOT EXISTS presupuesto_personal (
    id                BIGSERIAL PRIMARY KEY,
    periodo           VARCHAR(7)  NOT NULL,
    cargo             VARCHAR(20) NOT NULL,
    monto_presupuestado NUMERIC(14, 2) NOT NULL,
    CONSTRAINT uk_presupuesto_periodo_cargo UNIQUE (periodo, cargo),
    CONSTRAINT ck_presupuesto_monto CHECK (monto_presupuestado >= 0)
);

-- Reverso (manual):
-- DROP TABLE IF EXISTS presupuesto_personal;
-- DROP TABLE IF EXISTS nomina_trabajador;
-- DROP TABLE IF EXISTS turno_trabajador;
-- DROP TABLE IF EXISTS trabajador_conjunto;
-- DROP TABLE IF EXISTS empresa_prestadora;
