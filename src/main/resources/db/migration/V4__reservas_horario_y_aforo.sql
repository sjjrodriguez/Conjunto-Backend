-- P4 bloque 5: reservas con hora de fin, asistentes, aforo y horario de cada zona.

-- Hasta dónde va la reserva y cuántas personas asisten. Las anteriores quedan sin hora de fin:
-- para ellas el backend sigue usando la duración fija (app.reservas.duracion-horas).
ALTER TABLE reserva ADD COLUMN hora_fin TIME;
ALTER TABLE reserva ADD COLUMN asistentes INTEGER;
ALTER TABLE reserva ADD CONSTRAINT ck_reserva_asistentes CHECK (asistentes IS NULL OR asistentes >= 1);

-- Aforo y horario de la zona. Null = sin límite (como antes de P4), hasta que el
-- administrador los configure.
ALTER TABLE zona_comun ADD COLUMN aforo INTEGER;
ALTER TABLE zona_comun ADD COLUMN hora_apertura TIME;
ALTER TABLE zona_comun ADD COLUMN hora_cierre TIME;
ALTER TABLE zona_comun ADD CONSTRAINT ck_zona_comun_aforo CHECK (aforo IS NULL OR aforo >= 1);
