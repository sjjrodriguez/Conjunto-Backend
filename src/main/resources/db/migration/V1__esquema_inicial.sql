-- Esquema inicial: las tablas tal como estaban en Supabase al pasar a Flyway (antes las creaba
-- Hibernate con ddl-auto=update). Mismos nombres de columnas, restricciones e índices.
--
-- En la base de Supabase, que ya tiene estas tablas, NO se ejecuta: Flyway la marca como
-- versión 1 (spring.flyway.baseline-on-migrate) y desde ahí aplica solo V2, V3...
-- En una base vacía (otra instalación, pruebas) crea todo desde cero.
-- No editar este archivo: cada cambio nuevo va en una migración nueva.

CREATE TABLE administrador (
    id         BIGSERIAL PRIMARY KEY,
    usuario    VARCHAR(255) NOT NULL,
    contrasena VARCHAR(255) NOT NULL,
    CONSTRAINT uk_r0jus8ywt1dl6snd357ie37j9 UNIQUE (usuario)
);

CREATE TABLE apartamento (
    id         BIGSERIAL PRIMARY KEY,
    torre      VARCHAR(255) NOT NULL,
    apto       VARCHAR(255) NOT NULL,
    contrasena VARCHAR(255) NOT NULL
);

CREATE TABLE zona_comun (
    id                 BIGSERIAL PRIMARY KEY,
    nombre             VARCHAR(255) NOT NULL,
    estado_recibido    VARCHAR(255) NOT NULL,
    descripcion_estado TEXT,
    es_reservable      BOOLEAN DEFAULT FALSE
);

CREATE TABLE notificacion (
    id      BIGSERIAL PRIMARY KEY,
    tipo    VARCHAR(255) NOT NULL,
    mensaje TEXT NOT NULL,
    fecha   TIMESTAMP NOT NULL,
    leida   BOOLEAN NOT NULL
);

CREATE TABLE obra (
    id                     BIGSERIAL PRIMARY KEY,
    descripcion_adecuacion TEXT NOT NULL,
    estado_obra            VARCHAR(255) NOT NULL,
    fecha_inicio           DATE NOT NULL,
    fecha_fin_estimada     DATE NOT NULL,
    apartamento_id         BIGINT NOT NULL,
    CONSTRAINT fk95binhcx9iq710wtkfjp82eml FOREIGN KEY (apartamento_id) REFERENCES apartamento (id)
);
CREATE INDEX idx_obra_apartamento ON obra (apartamento_id);

CREATE TABLE pago (
    id              BIGSERIAL PRIMARY KEY,
    saldo_pendiente DOUBLE PRECISION NOT NULL,
    estado_cuenta   VARCHAR(255) NOT NULL,
    observaciones   TEXT,
    apartamento_id  BIGINT NOT NULL,
    CONSTRAINT uk_2fvjst86ywxovrce4f1t5jarf UNIQUE (apartamento_id),
    CONSTRAINT fkalaynu3yl7d4n3bb9ug4p6r2t FOREIGN KEY (apartamento_id) REFERENCES apartamento (id)
);

CREATE TABLE personal_obra (
    id                BIGSERIAL PRIMARY KEY,
    nombre_trabajador VARCHAR(255) NOT NULL,
    cedula            VARCHAR(255) NOT NULL,
    arl_estado        BOOLEAN NOT NULL,
    obra_id           BIGINT NOT NULL,
    CONSTRAINT fkbvjidv2im4cy91le5k59msjbh FOREIGN KEY (obra_id) REFERENCES obra (id)
);
CREATE INDEX idx_personal_obra_obra ON personal_obra (obra_id);

CREATE TABLE reserva (
    id             BIGSERIAL PRIMARY KEY,
    fecha          DATE NOT NULL,
    hora           TIME NOT NULL,
    estado         VARCHAR(255) NOT NULL,
    zona_comun_id  BIGINT NOT NULL,
    apartamento_id BIGINT NOT NULL,
    CONSTRAINT fke1x9ogh9wr1l5rlmwbvktge2c FOREIGN KEY (zona_comun_id) REFERENCES zona_comun (id),
    CONSTRAINT fkc5mobtjdvcc3eqscg2fl5jyeh FOREIGN KEY (apartamento_id) REFERENCES apartamento (id)
);
CREATE INDEX idx_reserva_apartamento ON reserva (apartamento_id);
CREATE INDEX idx_reserva_zona_fecha ON reserva (zona_comun_id, fecha);

CREATE TABLE residente (
    id              BIGSERIAL PRIMARY KEY,
    nombre_completo VARCHAR(255) NOT NULL,
    telefono        VARCHAR(255),
    identificacion  VARCHAR(255) NOT NULL,
    tipo_habitante  VARCHAR(255) NOT NULL,
    apartamento_id  BIGINT NOT NULL,
    CONSTRAINT fk6v5dlvv81df9fgxmooo71xpyu FOREIGN KEY (apartamento_id) REFERENCES apartamento (id)
);
CREATE INDEX idx_residente_apartamento ON residente (apartamento_id);

CREATE TABLE vehiculo (
    id             BIGSERIAL PRIMARY KEY,
    placa          VARCHAR(255) NOT NULL,
    marca          VARCHAR(255),
    color          VARCHAR(255),
    tipo           VARCHAR(255) NOT NULL,
    apartamento_id BIGINT NOT NULL,
    CONSTRAINT fkf5tbeva33pa0emq5p31ccvicq FOREIGN KEY (apartamento_id) REFERENCES apartamento (id)
);
CREATE INDEX idx_vehiculo_apartamento ON vehiculo (apartamento_id);
