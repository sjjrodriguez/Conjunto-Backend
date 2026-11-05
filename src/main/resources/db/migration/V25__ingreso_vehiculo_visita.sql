-- P8 bloque 4: la portería anota si el visitante entra a pie o en vehículo. Con vehículo lleva la
-- placa y ocupa un parqueadero de invitados hasta que la portería registra su salida (o hasta que
-- la visita se rechaza o expira). Idempotente.

ALTER TABLE visita_porteria ADD COLUMN IF NOT EXISTS tipo_ingreso VARCHAR(20) NOT NULL DEFAULT 'PEATONAL';
ALTER TABLE visita_porteria ADD COLUMN IF NOT EXISTS parqueadero_id BIGINT;
ALTER TABLE visita_porteria ADD COLUMN IF NOT EXISTS salida_en TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_visita_tipo_ingreso') THEN
        ALTER TABLE visita_porteria ADD CONSTRAINT ck_visita_tipo_ingreso CHECK (tipo_ingreso IN ('PEATONAL', 'VEHICULO'));
    END IF;
    -- Si el administrador borra el parqueadero, la visita queda sin él (el historial no se pierde)
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_visita_parqueadero') THEN
        ALTER TABLE visita_porteria ADD CONSTRAINT fk_visita_parqueadero
            FOREIGN KEY (parqueadero_id) REFERENCES parqueadero (id) ON DELETE SET NULL;
    END IF;
END $$;

-- Un parqueadero de invitados no puede tener dos visitas dentro a la vez (aunque dos porteros
-- lo asignen al mismo tiempo)
CREATE UNIQUE INDEX IF NOT EXISTS uk_visita_parqueadero_ocupado ON visita_porteria (parqueadero_id)
    WHERE parqueadero_id IS NOT NULL AND salida_en IS NULL AND estado IN ('PENDIENTE', 'AUTORIZADA');

-- Las visitas que siguen dentro, para el tablero de la portería
CREATE INDEX IF NOT EXISTS idx_visita_dentro ON visita_porteria (conjunto_id, salida_en, estado);
