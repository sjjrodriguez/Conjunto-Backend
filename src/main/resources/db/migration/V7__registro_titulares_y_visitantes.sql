-- P5 bloque 1: registro desde la app. Cada apartamento tiene un titular propietario y, si está
-- arrendado, un titular arrendatario: son los que entran a la app (torre + apto + rol + contraseña).
-- Las demás personas del hogar son residentes sin cuenta.

ALTER TABLE residente ADD COLUMN correo VARCHAR(255);
ALTER TABLE residente ADD COLUMN titular BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE residente ADD COLUMN terminos_aceptados_en TIMESTAMP;
-- Solo para el arrendatario: también lo aprueba el propietario. Null = no aplica
ALTER TABLE residente ADD COLUMN aprobado_propietario BOOLEAN;

-- Los que ya entraban con su documento (P4) quedan como titulares
UPDATE residente SET titular = TRUE WHERE contrasena IS NOT NULL;
-- Los arrendatarios de P4 los agregó el propietario: ya cuentan con su aprobación
UPDATE residente SET aprobado_propietario = TRUE WHERE tipo_habitante = 'ARRENDATARIO';

CREATE INDEX idx_residente_titular ON residente (apartamento_id, titular);

-- Visitantes frecuentes de cada apartamento (los ve la portería; se gestionan en Mi hogar)
CREATE TABLE visitante_frecuente (
    id             BIGSERIAL PRIMARY KEY,
    apartamento_id BIGINT       NOT NULL,
    nombre_completo VARCHAR(255) NOT NULL,
    identificacion VARCHAR(50),
    telefono       VARCHAR(50),
    creado_en      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_visitante_apartamento FOREIGN KEY (apartamento_id) REFERENCES apartamento (id)
);
CREATE INDEX idx_visitante_apartamento ON visitante_frecuente (apartamento_id);
