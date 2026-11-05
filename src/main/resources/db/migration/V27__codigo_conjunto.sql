-- P9 bloque 2: el código diario de cada conjunto. Con este secreto (que nunca sale del servidor)
-- y la fecha de Colombia se calcula un código de 6 dígitos que cambia cada día; la app de
-- residentes lo pide después de elegir el conjunto. Idempotente.

ALTER TABLE conjunto ADD COLUMN IF NOT EXISTS codigo_secreto VARCHAR(64);

-- Los conjuntos que ya existen reciben su secreto (64 caracteres hexadecimales al azar)
UPDATE conjunto
SET codigo_secreto = md5(random()::text || clock_timestamp()::text || id::text)
                  || md5(random()::text || clock_timestamp()::text)
WHERE codigo_secreto IS NULL;

ALTER TABLE conjunto ALTER COLUMN codigo_secreto SET NOT NULL;
