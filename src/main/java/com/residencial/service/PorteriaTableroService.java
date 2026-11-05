package com.residencial.service;

import com.residencial.model.Apartamento;
import com.residencial.model.MinutaPorteria;
import com.residencial.model.Residente;
import com.residencial.model.Vehiculo;
import com.residencial.model.VisitaPorteria;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.MinutaPorteriaRepository;
import com.residencial.repository.ResidenteRepository;
import com.residencial.repository.VehiculoRepository;
import com.residencial.repository.VisitaPorteriaRepository;
import com.residencial.security.AccesoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * P8 bloque 5: lo que la portería consulta durante el turno, aparte de registrar visitas
 * (PorteriaService): el resumen de inicio, quién sigue dentro, el historial con búsqueda, el
 * directorio de apartamentos y la minuta del turno.
 */
@Service
@Transactional
public class PorteriaTableroService {

    /** Las visitas a pie sin salida se dejan de contar como "dentro" pasado este tiempo. */
    static final int HORAS_DENTRO = 24;
    static final int TAMANO_PAGINA = 30;
    static final int DIAS_HISTORIAL_MAX = 90;
    static final int PAGINAS_MAX = 20;

    public record Resumen(long visitasHoy, long esperandoRespuesta, long dentro, long vehiculosDentro,
                          long parqueaderosLibres, long parqueaderosTotal, long minutaHoy, String guardia) {}

    public record Pagina(List<PorteriaService.Visita> visitas, boolean hayMas) {}

    public record Habitante(String nombre, String tipo, String telefono) {}

    public record ApartamentoDirectorio(Long id, String torre, String apto, String ocupacion, String citofono1,
                                        String citofono2, List<Habitante> residentes, List<String> placas) {}

    public record Anotacion(Long id, String categoria, String texto, LocalDateTime creadaEn, String guardia) {}

    public record NuevaAnotacion(String categoria, String texto) {}

    private final VisitaPorteriaRepository visitas;
    private final PorteriaService porteria;
    private final ApartamentoRepository apartamentos;
    private final ResidenteRepository residentes;
    private final VehiculoRepository vehiculos;
    private final MinutaPorteriaRepository minuta;
    private final GuardiaService guardias;
    private final AccesoService acceso;
    private final ZoneId zona;

    public PorteriaTableroService(VisitaPorteriaRepository visitas, PorteriaService porteria, ApartamentoRepository apartamentos,
                                  ResidenteRepository residentes, VehiculoRepository vehiculos, MinutaPorteriaRepository minuta,
                                  GuardiaService guardias, AccesoService acceso,
                                  @Value("${app.zona-horaria:America/Bogota}") String zona) {
        this.visitas = visitas;
        this.porteria = porteria;
        this.apartamentos = apartamentos;
        this.residentes = residentes;
        this.vehiculos = vehiculos;
        this.minuta = minuta;
        this.guardias = guardias;
        this.acceso = acceso;
        this.zona = ZoneId.of(zona);
    }

    public Resumen resumen() {
        LocalDateTime ahora = LocalDateTime.now(zona);
        visitas.expirar(ahora);
        LocalDateTime hoy = ahora.toLocalDate().atStartOfDay();
        List<VisitaPorteria> adentro = visitas.dentro(ahora.minusHours(HORAS_DENTRO));
        List<PorteriaService.ParqueaderoInvitados> puestos = porteria.parqueaderosInvitados();
        return new Resumen(
                visitas.countByCreadaEnGreaterThanEqual(hoy),
                visitas.countByEstadoAndVenceEnAfter(VisitaPorteria.PENDIENTE, ahora),
                adentro.size(),
                adentro.stream().filter(v -> VisitaPorteria.VEHICULO.equals(v.getTipoIngreso())).count(),
                puestos.stream().filter(PorteriaService.ParqueaderoInvitados::libre).count(),
                puestos.size(),
                minuta.countByCreadaEnGreaterThanEqual(hoy),
                guardias.delTurno().map(g -> g.getNombre()).orElse(null));
    }

    /** Autorizadas sin salida: las de las últimas 24 horas y las que ocupan un parqueadero (de cuando sea). */
    public List<PorteriaService.Visita> dentro() {
        LocalDateTime ahora = LocalDateTime.now(zona);
        visitas.expirar(ahora);
        return visitas.dentro(ahora.minusHours(HORAS_DENTRO)).stream().map(porteria::aDTO).toList();
    }

    /**
     * Visitas de los últimos [dias] (hasta 90), de la más reciente a la más vieja, de a 30. [q]
     * busca en el nombre, el documento, la placa y el apartamento ("5-502", "502").
     */
    public Pagina historial(String q, Integer dias, Integer pagina) {
        int d = dias == null ? 30 : Math.max(1, Math.min(DIAS_HISTORIAL_MAX, dias));
        int p = pagina == null ? 0 : Math.max(0, Math.min(PAGINAS_MAX, pagina));
        String patron = "%" + (q == null ? "" : sinTildes(q.trim()).replace("%", "").replace("_", "")) + "%";
        LocalDateTime desde = LocalDateTime.now(zona).toLocalDate().minusDays(d - 1L).atStartOfDay();
        // Hasta el final de esta página y una más, para saber si hay otra (PageRequest no deja
        // pedir 31 desde la 30)
        List<VisitaPorteria> encontradas = visitas.historial(desde, patron, PageRequest.of(0, (p + 1) * TAMANO_PAGINA + 1));
        boolean hayMas = encontradas.size() > (p + 1) * TAMANO_PAGINA;
        return new Pagina(encontradas.stream().skip((long) p * TAMANO_PAGINA).limit(TAMANO_PAGINA).map(porteria::aDTO).toList(), hayMas);
    }

    /** Apartamentos con sus residentes aprobados (nombre, si es propietario o arrendatario y teléfono), citófonos y placas. */
    public List<ApartamentoDirectorio> directorio(String q) {
        Map<Long, List<Residente>> porApto = residentes.findByEstadoOrderByIdAsc(Residente.APROBADO).stream()
                .collect(Collectors.groupingBy(r -> r.getApartamento().getId()));
        Map<Long, List<String>> placas = vehiculos.findByEstadoOrderByIdAsc(Vehiculo.APROBADO).stream()
                .collect(Collectors.groupingBy(v -> v.getApartamento().getId(), Collectors.mapping(Vehiculo::getPlaca, Collectors.toList())));
        String buscado = q == null ? "" : sinTildes(q.trim());
        return apartamentos.findAll().stream()
                .sorted(Comparator.comparing((Apartamento a) -> orden(a.getTorre())).thenComparing(a -> orden(a.getApto())))
                .map(a -> new ApartamentoDirectorio(a.getId(), a.getTorre(), a.getApto(), a.getTipoOcupacion(),
                        vacio(a.getCitofono1()), vacio(a.getCitofono2()),
                        porApto.getOrDefault(a.getId(), List.of()).stream()
                                .map(r -> new Habitante(r.getNombreCompleto(), r.getTipoHabitante(), vacio(r.getTelefono()))).toList(),
                        placas.getOrDefault(a.getId(), List.of())))
                .filter(a -> buscado.isEmpty() || coincide(a, buscado))
                .toList();
    }

    public List<Anotacion> minuta(Integer dias) {
        int d = dias == null ? 7 : Math.max(1, Math.min(DIAS_HISTORIAL_MAX, dias));
        LocalDateTime desde = LocalDateTime.now(zona).toLocalDate().minusDays(d - 1L).atStartOfDay();
        return minuta.desde(desde).stream().map(this::aAnotacion).toList();
    }

    public Anotacion anotar(NuevaAnotacion n) {
        String texto = n.texto() == null ? "" : n.texto().trim();
        if (texto.isEmpty()) throw invalido("Escribe qué pasó");
        if (texto.length() > 1000) throw invalido("La anotación es muy larga (máximo 1000 caracteres)");
        String categoria = n.categoria() == null || n.categoria().isBlank() ? MinutaPorteria.NOVEDAD
                : n.categoria().trim().toUpperCase(Locale.ROOT);
        if (!MinutaPorteria.CATEGORIAS.contains(categoria)) throw invalido("Categoría no válida");
        MinutaPorteria m = new MinutaPorteria();
        m.setCategoria(categoria);
        m.setTexto(texto);
        m.setCreadaEn(LocalDateTime.now(zona));
        m.setRegistradaPor(acceso.tokenActual().map(Jwt::getSubject).orElse(null));
        m.setGuardiaNombre(guardias.delTurno().map(g -> g.getNombre()).orElse(null));
        return aAnotacion(minuta.save(m));
    }

    private Anotacion aAnotacion(MinutaPorteria m) {
        return new Anotacion(m.getId(), m.getCategoria(), m.getTexto(), m.getCreadaEn(), m.getGuardiaNombre());
    }

    private static boolean coincide(ApartamentoDirectorio a, String q) {
        if (sinTildes(a.torre() + "-" + a.apto()).contains(q) || sinTildes(a.apto()).equals(q)
                || sinTildes("torre " + a.torre() + " apto " + a.apto()).contains(q)) return true;
        if (a.placas().stream().anyMatch(p -> sinTildes(p).contains(q.replace(" ", "")))) return true;
        return a.residentes().stream().anyMatch(r -> sinTildes(r.nombre()).contains(q));
    }

    /** "10" después de "9": números como números, el resto como texto. */
    private static String orden(String s) {
        String t = s == null ? "" : s.trim();
        return t.matches("\\d+") ? String.format("%010d", Long.parseLong(t)) : t.toLowerCase(Locale.ROOT);
    }

    static String sinTildes(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
