-- P12: quién dio la salida de la visita (PORTERIA o RESIDENTE, si fue el residente desde su app).
ALTER TABLE visita_porteria ADD COLUMN salida_por VARCHAR(20);
