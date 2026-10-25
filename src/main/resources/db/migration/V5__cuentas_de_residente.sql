-- P4 bloque 6: cuenta personal de cada residente (documento + contraseña propia) y
-- arrendatarios que agrega el propietario y aprueba el administrador.

-- Null = el residente aún no tiene acceso con su documento (sigue entrando con el del apartamento)
ALTER TABLE residente ADD COLUMN contrasena VARCHAR(255);

-- Los residentes existentes ya estaban aprobados: los registró el administrador
ALTER TABLE residente ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'APROBADO';
ALTER TABLE residente ADD CONSTRAINT ck_residente_estado
    CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO'));

-- El login por documento busca por aquí
CREATE INDEX idx_residente_identificacion ON residente (identificacion);
