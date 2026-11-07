-- P11 bloque 2: el residente y la portería quitan de su lista lo ya entregado (el registro se conserva)
ALTER TABLE correspondencia ADD COLUMN oculta_residente BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE correspondencia ADD COLUMN oculta_porteria BOOLEAN NOT NULL DEFAULT FALSE;
