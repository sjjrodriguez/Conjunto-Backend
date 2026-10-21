-- =====================================================================
-- P1 bloque 2 - Revisar los valores de estado y tipo guardados
-- =====================================================================
-- Desde el bloque 2 el backend solo acepta estos valores al crear o editar:
--   reserva.estado          PENDIENTE, APROBADA, RECHAZADA
--   obra.estado_obra        EN_PROCESO, FINALIZADA
--   residente.tipo_habitante PROPIETARIO, ARRENDATARIO
--   vehiculo.tipo           Carro, Moto
--   pago.estado_cuenta      Al Día, En Mora
--
-- Los registros antiguos con otros valores siguen leyéndose sin error, pero
-- conviene corregirlos para que filtros y comparaciones funcionen
-- (por ejemplo, "Pendiente" no cuenta como PENDIENTE al validar choques de reservas).
--
-- 1. Ejecutar primero solo esta consulta (solo lectura) en el editor SQL de Supabase.
--    Las filas con valido = false son las que hay que corregir.
-- =====================================================================

select 'reserva.estado' as campo, estado as valor, count(*) as filas,
       estado in ('PENDIENTE', 'APROBADA', 'RECHAZADA') as valido
from reserva group by estado
union all
select 'obra.estado_obra', estado_obra, count(*),
       estado_obra in ('EN_PROCESO', 'FINALIZADA')
from obra group by estado_obra
union all
select 'residente.tipo_habitante', tipo_habitante, count(*),
       tipo_habitante in ('PROPIETARIO', 'ARRENDATARIO')
from residente group by tipo_habitante
union all
select 'vehiculo.tipo', tipo, count(*),
       tipo in ('Carro', 'Moto')
from vehiculo group by tipo
union all
select 'pago.estado_cuenta', estado_cuenta, count(*),
       estado_cuenta in ('Al Día', 'En Mora')
from pago group by estado_cuenta
order by campo, valor;

-- =====================================================================
-- 2. OPCIONAL - Normalizar variantes de mayúsculas/espacios.
--    Revisar el resultado de la consulta 1 antes de ejecutar y hacer copia
--    de seguridad. Los valores que no encajen en ninguna variante
--    (por ejemplo "dueño") hay que corregirlos a mano.
-- =====================================================================
-- begin;
-- update reserva   set estado = upper(trim(estado))
--   where upper(trim(estado)) in ('PENDIENTE', 'APROBADA', 'RECHAZADA') and estado <> upper(trim(estado));
-- update obra      set estado_obra = replace(upper(trim(estado_obra)), ' ', '_')
--   where replace(upper(trim(estado_obra)), ' ', '_') in ('EN_PROCESO', 'FINALIZADA')
--     and estado_obra <> replace(upper(trim(estado_obra)), ' ', '_');
-- update residente set tipo_habitante = upper(trim(tipo_habitante))
--   where upper(trim(tipo_habitante)) in ('PROPIETARIO', 'ARRENDATARIO') and tipo_habitante <> upper(trim(tipo_habitante));
-- update vehiculo  set tipo = initcap(lower(trim(tipo)))
--   where lower(trim(tipo)) in ('carro', 'moto') and tipo <> initcap(lower(trim(tipo)));
-- update pago      set estado_cuenta = 'Al Día'
--   where lower(trim(estado_cuenta)) in ('al dia', 'al día') and estado_cuenta <> 'Al Día';
-- update pago      set estado_cuenta = 'En Mora'
--   where lower(trim(estado_cuenta)) = 'en mora' and estado_cuenta <> 'En Mora';
-- commit;
