-- P10: teléfono de la portería de cada conjunto. Desde la alerta de una visita el residente puede
-- llamar para preguntar quién es antes de aceptarla o rechazarla. Lo cambia la administración.
ALTER TABLE conjunto ADD COLUMN telefono_porteria VARCHAR(20);
