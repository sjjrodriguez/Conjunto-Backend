-- P11 bloque 1: por qué la administración aprobó o rechazó una reserva (lo ve el residente)
ALTER TABLE reserva ADD COLUMN motivo_respuesta VARCHAR(300);
