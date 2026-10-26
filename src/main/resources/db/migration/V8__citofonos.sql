-- P5 bloque 2: los dos teléfonos del citófono de cada apartamento. Null = sin configurar
-- (el primero se muestra con el teléfono del propietario hasta que lo cambien).
ALTER TABLE apartamento ADD COLUMN citofono1 VARCHAR(30);
ALTER TABLE apartamento ADD COLUMN citofono2 VARCHAR(30);
