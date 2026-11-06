package com.residencial.service;

import com.residencial.dto.PlataformaDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * P8 bloque 5 de la plataforma (B6): el panel con las cifras de todos los conjuntos, el detalle
 * de uno (cuántos apartamentos, residentes, vehículos, guardias, celulares y visitas tiene) y la
 * auditoría con filtros. Solo lee: lo que cambia sigue en PlataformaService, que lo audita.
 * Consulta con JDBC porque cuenta en todos los conjuntos a la vez (sin el filtro por conjunto).
 */
@Service
public class PlataformaPanelService {

    static final int EVENTOS_POR_PAGINA = 50;
    private static final Set<String> ACCIONES = Set.of("INICIO_SESION", "CREAR_CONJUNTO", "EDITAR_CONJUNTO",
            "SUSPENDER_CONJUNTO", "REACTIVAR_CONJUNTO", "RESTABLECER_PRINCIPAL");

    private final JdbcTemplate jdbc;
    private final PlataformaService plataforma;
    private final com.residencial.repository.ConjuntoRepository conjuntos;
    private final com.residencial.security.CodigoConjunto codigos;
    private final ZoneId zona;

    public PlataformaPanelService(JdbcTemplate jdbc, PlataformaService plataforma,
                                  com.residencial.repository.ConjuntoRepository conjuntos,
                                  com.residencial.security.CodigoConjunto codigos,
                                  @Value("${app.zona-horaria:America/Bogota}") String zona) {
        this.jdbc = jdbc;
        this.plataforma = plataforma;
        this.conjuntos = conjuntos;
        this.codigos = codigos;
        this.zona = ZoneId.of(zona);
    }

    public PlataformaDTO.Panel panel() {
        LocalDateTime ahora = LocalDateTime.now(zona);
        Timestamp inicioMes = Timestamp.valueOf(ahora.toLocalDate().withDayOfMonth(1).atStartOfDay());
        Timestamp hace7 = Timestamp.valueOf(ahora.minusDays(7));
        long activos = contar("select count(*) from conjunto where activo = true");
        long suspendidos = contar("select count(*) from conjunto where activo = false");
        List<PlataformaDTO.PorDepartamento> porDepartamento = jdbc.query("""
                select departamento, count(*) as conjuntos, sum(case when activo then 1 else 0 end) as activos
                from conjunto group by departamento order by count(*) desc, departamento
                """, (rs, i) -> new PlataformaDTO.PorDepartamento(rs.getString("departamento"), rs.getLong("conjuntos"),
                rs.getLong("activos")));
        return new PlataformaDTO.Panel(
                activos, suspendidos,
                contar("select count(*) from conjunto where creado_en >= ?", inicioMes),
                contar("select count(*) from apartamento"),
                contar("select count(*) from residente where estado = 'APROBADO'"),
                contar("select count(*) from administrador where activo = true"),
                contar("select count(*) from dispositivo_cuenta where sesion is not null"),
                contar("select count(*) from visita_porteria where creada_en >= ?", hace7),
                porDepartamento,
                // Los conjuntos activos sin administrador activo no tienen quién los maneje
                jdbc.query("""
                        select c.id, c.nombre, c.municipio from conjunto c
                        where c.activo = true and not exists (
                            select 1 from administrador d where d.conjunto_id = c.id and d.activo = true and d.rol = 'ADMIN')
                        order by c.nombre
                        """, (rs, i) -> new PlataformaDTO.Pendiente(rs.getLong("id"), rs.getString("nombre"),
                        rs.getString("municipio"), "Sin administrador activo")),
                eventos(null, null, null, null, null, 0).eventos().stream().limit(5).toList());
    }

    public PlataformaDTO.DetalleConjunto detalle(long id) {
        PlataformaDTO.ConjuntoResumen resumen = plataforma.conjunto(id);
        LocalDateTime ahora = LocalDateTime.now(zona);
        Timestamp hace30 = Timestamp.valueOf(ahora.minusDays(30));
        PlataformaDTO.Cifras cifras = new PlataformaDTO.Cifras(
                resumen.apartamentos(),
                contar("select count(*) from residente where conjunto_id = ? and estado = 'APROBADO' and tipo_habitante = 'PROPIETARIO'", id),
                contar("select count(*) from residente where conjunto_id = ? and estado = 'APROBADO' and tipo_habitante = 'ARRENDATARIO'", id),
                contar("select count(*) from residente where conjunto_id = ? and estado = 'PENDIENTE'", id),
                contar("select count(*) from vehiculo where conjunto_id = ? and estado = 'APROBADO'", id),
                contar("select count(*) from guardia_porteria where conjunto_id = ? and activo = true", id),
                contar("select count(*) from dispositivo_cuenta where conjunto_id = ? and sesion is not null", id),
                contar("select count(*) from visita_porteria where conjunto_id = ? and creada_en >= ?", id, hace30),
                contar("select count(*) from parqueadero where conjunto_id = ?", id));
        Timestamp ultimo = jdbc.queryForObject("select max(sesion_desde) from dispositivo_cuenta where conjunto_id = ?",
                Timestamp.class, id);
        String codigo = conjuntos.findById(id).map(codigos::codigoDeHoy).orElse(null);
        return new PlataformaDTO.DetalleConjunto(resumen, cifras, plataforma.cuentas(id),
                ultimo == null ? null : ultimo.toLocalDateTime(),
                eventos(id, null, null, null, null, 0).eventos().stream().limit(10).toList(),
                codigo, codigos.cambiaEn());
    }

    /**
     * La auditoría, de la más reciente a la más vieja, de a 50. Todos los filtros son opcionales:
     * conjunto, acción, desde y hasta (fechas, incluidas) y texto en el operador o el detalle.
     */
    public PlataformaDTO.PaginaEventos eventos(Long conjunto, String accion, LocalDate desde, LocalDate hasta, String q, Integer pagina) {
        StringBuilder sql = new StringBuilder("""
                select e.id, e.operador_usuario, e.accion, e.conjunto_id, e.detalle, e.fecha, c.nombre as conjunto_nombre
                from auditoria_plataforma e left join conjunto c on c.id = e.conjunto_id where 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (conjunto != null) { sql.append(" and e.conjunto_id = ?"); params.add(conjunto); }
        if (accion != null && !accion.isBlank()) {
            String a = accion.trim().toUpperCase(Locale.ROOT);
            if (!ACCIONES.contains(a)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acción no válida");
            sql.append(" and e.accion = ?");
            params.add(a);
        }
        if (desde != null) { sql.append(" and e.fecha >= ?"); params.add(Timestamp.valueOf(desde.atStartOfDay())); }
        if (hasta != null) { sql.append(" and e.fecha < ?"); params.add(Timestamp.valueOf(hasta.plusDays(1).atStartOfDay())); }
        if (q != null && !q.isBlank()) {
            String patron = "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
            sql.append(" and (lower(e.operador_usuario) like ? or lower(coalesce(e.detalle, '')) like ? or lower(coalesce(c.nombre, '')) like ?)");
            params.add(patron);
            params.add(patron);
            params.add(patron);
        }
        int p = pagina == null ? 0 : Math.max(0, Math.min(100, pagina));
        sql.append(" order by e.id desc limit ? offset ?");
        params.add(EVENTOS_POR_PAGINA + 1);
        params.add(p * EVENTOS_POR_PAGINA);
        List<PlataformaDTO.EventoDetalle> lista = jdbc.query(sql.toString(), (rs, i) -> {
            Timestamp f = rs.getTimestamp("fecha");
            // wasNull() se pregunta enseguida: después de leer otra columna ya no se refiere a esta
            long cid = rs.getLong("conjunto_id");
            Long conjuntoId = rs.wasNull() ? null : cid;
            return new PlataformaDTO.EventoDetalle(rs.getLong("id"), rs.getString("operador_usuario"), rs.getString("accion"),
                    conjuntoId, rs.getString("conjunto_nombre"), rs.getString("detalle"),
                    f == null ? null : f.toLocalDateTime());
        }, params.toArray());
        boolean hayMas = lista.size() > EVENTOS_POR_PAGINA;
        return new PlataformaDTO.PaginaEventos(hayMas ? lista.subList(0, EVENTOS_POR_PAGINA) : lista, hayMas);
    }

    private long contar(String sql, Object... params) {
        Long n = jdbc.queryForObject(sql, Long.class, params);
        return n == null ? 0 : n;
    }
}
