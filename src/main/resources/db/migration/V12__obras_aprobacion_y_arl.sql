-- P5 bloque 6: las obras y su personal esperan la aprobación de la administración (que
-- verifica la ARL); el certificado ARL se sube como archivo. Obras del conjunto: sin apartamento.

ALTER TABLE obra ADD COLUMN aprobacion VARCHAR(20) NOT NULL DEFAULT 'APROBADA';
ALTER TABLE obra ADD COLUMN motivo_rechazo TEXT;
ALTER TABLE obra ADD CONSTRAINT ck_obra_aprobacion CHECK (aprobacion IN ('PENDIENTE', 'APROBADA', 'RECHAZADA'));
ALTER TABLE obra ALTER COLUMN apartamento_id DROP NOT NULL;

ALTER TABLE personal_obra ADD COLUMN aprobacion VARCHAR(20) NOT NULL DEFAULT 'APROBADO';
ALTER TABLE personal_obra ADD COLUMN motivo_rechazo TEXT;
ALTER TABLE personal_obra ADD COLUMN arl_ruta VARCHAR(500);
ALTER TABLE personal_obra ADD COLUMN arl_tipo VARCHAR(100);
ALTER TABLE personal_obra ADD COLUMN arl_subido_en TIMESTAMP;
ALTER TABLE personal_obra ADD CONSTRAINT ck_personal_aprobacion CHECK (aprobacion IN ('PENDIENTE', 'APROBADO', 'RECHAZADO'));
