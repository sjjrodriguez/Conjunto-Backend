-- P5 bloque 4: parqueaderos numerados por categoría (y para invitados) y cupos de vehículos
-- por apartamento (si no tiene, rige el cupo general de cupo_vehiculo).

CREATE TABLE parqueadero (
    id          BIGSERIAL PRIMARY KEY,
    numero      VARCHAR(20) NOT NULL,
    tipo        VARCHAR(40) NOT NULL,   -- Carro, Moto, Bicicleta... o Invitados
    vehiculo_id BIGINT,                 -- null = libre
    CONSTRAINT uk_parqueadero_numero UNIQUE (numero),
    CONSTRAINT uk_parqueadero_vehiculo UNIQUE (vehiculo_id),
    CONSTRAINT fk_parqueadero_vehiculo FOREIGN KEY (vehiculo_id) REFERENCES vehiculo (id) ON DELETE SET NULL
);
CREATE INDEX idx_parqueadero_tipo ON parqueadero (tipo);

CREATE TABLE cupo_apartamento (
    apartamento_id BIGINT      NOT NULL,
    tipo           VARCHAR(40) NOT NULL,
    maximo         INTEGER     NOT NULL,
    CONSTRAINT pk_cupo_apartamento PRIMARY KEY (apartamento_id, tipo),
    CONSTRAINT fk_cupo_apartamento FOREIGN KEY (apartamento_id) REFERENCES apartamento (id) ON DELETE CASCADE,
    CONSTRAINT ck_cupo_apartamento_maximo CHECK (maximo >= 0)
);
