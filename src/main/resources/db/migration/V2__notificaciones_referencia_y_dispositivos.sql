-- P4 bloque 2: notificaciones que llevan a su acción y avisos en el celular.

-- A qué se refiere cada notificación, para abrir la pantalla correspondiente:
-- la reserva o el vehículo (referencia_id) y el apartamento que la originó.
-- Sin llave foránea: si se borra la reserva o el apartamento, la notificación se conserva.
ALTER TABLE notificacion ADD COLUMN referencia_id BIGINT;
ALTER TABLE notificacion ADD COLUMN apartamento_id BIGINT;

-- Celulares con sesión iniciada que reciben avisos (Firebase Cloud Messaging).
-- rol: ADMIN o PROPIETARIO; apartamento_id solo para los residentes.
CREATE TABLE dispositivo (
    id             BIGSERIAL PRIMARY KEY,
    token          VARCHAR(512) NOT NULL,
    rol            VARCHAR(32)  NOT NULL,
    apartamento_id BIGINT,
    actualizado    TIMESTAMP    NOT NULL,
    CONSTRAINT uk_dispositivo_token UNIQUE (token),
    CONSTRAINT fk_dispositivo_apartamento FOREIGN KEY (apartamento_id) REFERENCES apartamento (id)
);
CREATE INDEX idx_dispositivo_rol ON dispositivo (rol);
