-- P12: cuándo se le preguntó por última vez al residente si su visitante sigue adentro (para no
-- repetirlo antes de tiempo). Las fotos viejas se borran con una tarea diaria (sin columnas nuevas:
-- al borrar la foto la ruta queda en NULL).
ALTER TABLE visita_porteria ADD COLUMN IF NOT EXISTS recordatorio_en TIMESTAMP;
