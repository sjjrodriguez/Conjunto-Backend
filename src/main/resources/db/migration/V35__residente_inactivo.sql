-- P11 bloque 9: el arrendatario que se va puede quedar desactivado (no entra, pero se puede
-- volver a activar) en vez de eliminarse.
ALTER TABLE residente DROP CONSTRAINT ck_residente_estado;
ALTER TABLE residente ADD CONSTRAINT ck_residente_estado
    CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'INACTIVO'));
