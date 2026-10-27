-- P5 bloque 5: disponibilidad de cada zona común por día de la semana y fechas bloqueadas.
-- Si una zona no tiene horario semanal, rige su horario general (hora_apertura/hora_cierre).

CREATE TABLE zona_horario (
    id          BIGSERIAL PRIMARY KEY,
    zona_id     BIGINT   NOT NULL,
    dia_semana  INTEGER  NOT NULL,  -- 1 = lunes ... 7 = domingo
    apertura    TIME     NOT NULL,
    cierre      TIME     NOT NULL,
    CONSTRAINT fk_zona_horario FOREIGN KEY (zona_id) REFERENCES zona_comun (id) ON DELETE CASCADE,
    CONSTRAINT uk_zona_horario_dia UNIQUE (zona_id, dia_semana),
    CONSTRAINT ck_zona_horario_dia CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_zona_horario_horas CHECK (cierre > apertura)
);

CREATE TABLE zona_bloqueo (
    id      BIGSERIAL PRIMARY KEY,
    zona_id BIGINT       NOT NULL,
    fecha   DATE         NOT NULL,
    motivo  VARCHAR(200),
    CONSTRAINT fk_zona_bloqueo FOREIGN KEY (zona_id) REFERENCES zona_comun (id) ON DELETE CASCADE,
    CONSTRAINT uk_zona_bloqueo_fecha UNIQUE (zona_id, fecha)
);
