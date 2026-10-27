-- P6 bloque 2: ocupación del apartamento, lo que puede ver el arrendatario, rol de portería y
-- versión de sesión (cerrar sesión invalida los tokens anteriores).

-- Cómo está ocupado el apartamento y qué le deja ver/hacer la administración al arrendatario
ALTER TABLE apartamento ADD COLUMN IF NOT EXISTS tipo_ocupacion VARCHAR(20) NOT NULL DEFAULT 'PROPIETARIO';
ALTER TABLE apartamento ADD COLUMN IF NOT EXISTS arrendatario_ve_cartera BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE apartamento ADD COLUMN IF NOT EXISTS arrendatario_puede_pagar BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE apartamento ADD COLUMN IF NOT EXISTS arrendatario_ve_obras BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE apartamento ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE apartamento DROP CONSTRAINT IF EXISTS ck_apartamento_ocupacion;
ALTER TABLE apartamento ADD CONSTRAINT ck_apartamento_ocupacion
    CHECK (tipo_ocupacion IN ('PROPIETARIO', 'ARRENDATARIO', 'DESOCUPADO'));

-- Los apartamentos que hoy tienen un arrendatario titular aprobado quedan como arrendados
UPDATE apartamento SET tipo_ocupacion = 'ARRENDATARIO'
WHERE tipo_ocupacion = 'PROPIETARIO' AND id IN (
    SELECT apartamento_id FROM residente
    WHERE titular = TRUE AND tipo_habitante = 'ARRENDATARIO' AND estado = 'APROBADO'
      AND contrasena IS NOT NULL AND (aprobado_propietario IS NULL OR aprobado_propietario = TRUE));

-- Administración: rol de portería, versión de sesión y baja sin borrar la cuenta
ALTER TABLE administrador DROP CONSTRAINT IF EXISTS ck_administrador_rol;
ALTER TABLE administrador ADD CONSTRAINT ck_administrador_rol CHECK (rol IN ('ADMIN', 'CONTADOR', 'PORTERIA'));
ALTER TABLE administrador ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE administrador ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;

-- El principal también puede fijar cuántas cuentas de portería crea
ALTER TABLE cupo_equipo DROP CONSTRAINT IF EXISTS ck_cupo_equipo_rol;
ALTER TABLE cupo_equipo ADD CONSTRAINT ck_cupo_equipo_rol CHECK (rol IN ('ADMIN', 'CONTADOR', 'PORTERIA'));

ALTER TABLE residente ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

-- Ajustes del conjunto que cambia la administración (clave/valor)
CREATE TABLE IF NOT EXISTS configuracion_conjunto (
    clave VARCHAR(60)  PRIMARY KEY,
    valor VARCHAR(255) NOT NULL
);
INSERT INTO configuracion_conjunto (clave, valor) VALUES ('vehiculos_alta_solo_admin', 'false')
ON CONFLICT (clave) DO NOTHING;

-- Reversa (manual):
-- DROP TABLE IF EXISTS configuracion_conjunto;
-- ALTER TABLE residente DROP COLUMN IF EXISTS token_version;
-- ALTER TABLE cupo_equipo DROP CONSTRAINT IF EXISTS ck_cupo_equipo_rol;
-- ALTER TABLE cupo_equipo ADD CONSTRAINT ck_cupo_equipo_rol CHECK (rol IN ('ADMIN', 'CONTADOR'));
-- ALTER TABLE administrador DROP COLUMN IF EXISTS activo;
-- ALTER TABLE administrador DROP COLUMN IF EXISTS token_version;
-- ALTER TABLE administrador DROP CONSTRAINT IF EXISTS ck_administrador_rol;
-- ALTER TABLE administrador ADD CONSTRAINT ck_administrador_rol CHECK (rol IN ('ADMIN', 'CONTADOR'));  -- falla si hay PORTERIA
-- ALTER TABLE apartamento DROP CONSTRAINT IF EXISTS ck_apartamento_ocupacion;
-- ALTER TABLE apartamento DROP COLUMN IF EXISTS token_version, DROP COLUMN IF EXISTS arrendatario_ve_obras,
--     DROP COLUMN IF EXISTS arrendatario_puede_pagar, DROP COLUMN IF EXISTS arrendatario_ve_cartera,
--     DROP COLUMN IF EXISTS tipo_ocupacion;
