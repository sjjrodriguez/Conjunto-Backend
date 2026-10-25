-- P4 bloque 4: más tipos de vehículo, cupos por tipo y aprobación del administrador.

-- Estado del vehículo: los que ya estaban registrados quedan aprobados; los nuevos de los
-- residentes entran PENDIENTE hasta que el administrador los apruebe o rechace.
ALTER TABLE vehiculo ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'APROBADO';
ALTER TABLE vehiculo ADD COLUMN motivo_rechazo TEXT;
CREATE INDEX idx_vehiculo_estado ON vehiculo (estado);

-- Bicicletas y patinetas no tienen placa
ALTER TABLE vehiculo ALTER COLUMN placa DROP NOT NULL;

-- Cuántos vehículos de cada tipo puede tener cada apartamento (los edita el administrador).
-- Si falta un tipo, el backend usa su valor por defecto (CupoVehiculo.POR_DEFECTO).
CREATE TABLE cupo_vehiculo (
    tipo   VARCHAR(40) PRIMARY KEY,
    maximo INTEGER     NOT NULL,
    CONSTRAINT ck_cupo_vehiculo_maximo CHECK (maximo >= 0)
);
INSERT INTO cupo_vehiculo (tipo, maximo) VALUES
    ('Carro', 1),
    ('Moto', 1),
    ('Bicicleta', 2),
    ('Patineta', 2),
    ('Patineta eléctrica', 1),
    ('Otro', 1);

-- Notificaciones también para los residentes: a quién va cada una (null = administrador)
ALTER TABLE notificacion ADD COLUMN destinatario_apartamento_id BIGINT;
CREATE INDEX idx_notificacion_destinatario ON notificacion (destinatario_apartamento_id);

-- Los celulares de los residentes se buscan por apartamento al enviar sus avisos
CREATE INDEX idx_dispositivo_apartamento ON dispositivo (apartamento_id);
