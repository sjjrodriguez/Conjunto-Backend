-- P11 bloque 5: el parqueadero se asigna a un apartamento; el vehículo es opcional
ALTER TABLE parqueadero ADD COLUMN apartamento_id BIGINT;
ALTER TABLE parqueadero ADD CONSTRAINT fk_parqueadero_apartamento
    FOREIGN KEY (apartamento_id) REFERENCES apartamento (id) ON DELETE SET NULL;
CREATE INDEX idx_parqueadero_apartamento ON parqueadero (apartamento_id);
-- Los que ya tenían vehículo quedan con el apartamento de ese vehículo
UPDATE parqueadero SET apartamento_id = (SELECT v.apartamento_id FROM vehiculo v WHERE v.id = parqueadero.vehiculo_id)
    WHERE vehiculo_id IS NOT NULL;
