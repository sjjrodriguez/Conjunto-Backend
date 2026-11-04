-- P7 bloque 5: una sesión por celular, límite de celulares por cuenta y código por correo en
-- cada celular nuevo. Idempotente.

ALTER TABLE conjunto ADD COLUMN IF NOT EXISTS max_dispositivos INTEGER NOT NULL DEFAULT 3;

-- Los celulares de cada cuenta ("admin:ID" o "residente:ID"). sesion es el id de la sesión
-- abierta en ese celular (claim sid del token); null = sin sesión
CREATE TABLE IF NOT EXISTS dispositivo_cuenta (
    id            BIGSERIAL PRIMARY KEY,
    conjunto_id   BIGINT      NOT NULL,
    sujeto        VARCHAR(40) NOT NULL,
    dispositivo   VARCHAR(64) NOT NULL,
    nombre        VARCHAR(80),
    tipo_app      VARCHAR(20),
    verificado_en TIMESTAMP,
    sesion        VARCHAR(64),
    sesion_desde  TIMESTAMP,
    creado_en     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dispositivo_cuenta_conjunto FOREIGN KEY (conjunto_id) REFERENCES conjunto (id),
    CONSTRAINT uk_dispositivo_cuenta UNIQUE (conjunto_id, sujeto, dispositivo),
    CONSTRAINT uk_dispositivo_cuenta_sesion UNIQUE (sesion)
);

CREATE INDEX IF NOT EXISTS ix_dispositivo_cuenta_sujeto ON dispositivo_cuenta (sujeto);

-- Código enviado por correo para verificar un celular nuevo. id es aleatorio (lo recibe la app)
CREATE TABLE IF NOT EXISTS codigo_verificacion (
    id          VARCHAR(36)  PRIMARY KEY,
    conjunto_id BIGINT       NOT NULL,
    sujeto      VARCHAR(40)  NOT NULL,
    dispositivo VARCHAR(64)  NOT NULL,
    nombre      VARCHAR(80),
    tipo_app    VARCHAR(20),
    codigo      VARCHAR(100) NOT NULL,
    intentos    INTEGER      NOT NULL DEFAULT 0,
    envios      INTEGER      NOT NULL DEFAULT 1,
    enviado_en  TIMESTAMP    NOT NULL,
    vence       TIMESTAMP    NOT NULL,
    usado       BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_codigo_verificacion_conjunto FOREIGN KEY (conjunto_id) REFERENCES conjunto (id)
);

CREATE INDEX IF NOT EXISTS ix_codigo_verificacion_sujeto ON codigo_verificacion (sujeto);
