package com.residencial.serviceimpl;

import java.util.Set;
import com.residencial.security.TokenService;
import com.residencial.repository.SolicitudContableRepository;
import com.residencial.model.SolicitudContable;
import com.residencial.almacen.AlmacenArchivos;
import com.residencial.dto.CuotaDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Catalogo;
import com.residencial.model.Cuota;
import com.residencial.model.Pago;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.CuotaRepository;
import com.residencial.repository.PagoRepository;
import com.residencial.security.AccesoService;
import com.residencial.service.CuotaService;
import com.residencial.service.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * La cuota mensual la genera el contador (o el administrador) para todos los apartamentos;
 * el residente sube el comprobante y el contador la aprueba o la rechaza. El resumen de
 * cartera (tabla pago, lo que ya usaba la app) se recalcula con las cuotas en cada cambio.
 */
@Service
@Transactional
public class CuotaServiceImpl implements CuotaService {

    private static final Logger log = LoggerFactory.getLogger(CuotaServiceImpl.class);
    static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
    static final Duration VIGENCIA_ENLACE = Duration.ofMinutes(10);
    private static final Pattern PERIODO = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final Map<String, String> EXTENSIONES = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "application/pdf", "pdf");
    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");
    private static final int DIA_LIMITE_POR_DEFECTO = 10;

    private final CuotaRepository repo;
    private final ApartamentoRepository aptoRepo;
    private final PagoRepository pagoRepo;
    private final AccesoService acceso;
    private final NotificacionService notificaciones;
    private final AlmacenArchivos almacen;
    private final ZoneId zonaHoraria;
    private final SolicitudContableRepository solicitudes;
    private final com.residencial.security.AccesoArrendatario arrendatario;

    public CuotaServiceImpl(CuotaRepository repo, ApartamentoRepository aptoRepo, PagoRepository pagoRepo,
                            AccesoService acceso, NotificacionService notificaciones, AlmacenArchivos almacen,
                            @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria,
                            SolicitudContableRepository solicitudes,
                            com.residencial.security.AccesoArrendatario arrendatario) {
        this.solicitudes = solicitudes;
        this.arrendatario = arrendatario;
        this.repo = repo;
        this.aptoRepo = aptoRepo;
        this.pagoRepo = pagoRepo;
        this.acceso = acceso;
        this.notificaciones = notificaciones;
        this.almacen = almacen;
        this.zonaHoraria = ZoneId.of(zonaHoraria);
    }

    private CuotaDTO toDTO(Cuota c) {
        Apartamento a = c.getApartamento();
        return new CuotaDTO(c.getId(), a.getId(), a.getTorre(), a.getApto(), c.getPeriodo(), c.getValor(),
                c.getFechaLimite(), c.getEstado(), vencida(c), c.getComprobanteRuta() != null, c.getComprobanteTipo(),
                c.getComprobanteSubidoEn(), c.getMotivoRechazo(), c.getPagadaEn());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuotaDTO> listar(String periodo, String estado) {
        String mes = periodo == null || periodo.isBlank() ? null : validarPeriodo(periodo);
        String est = estado == null || estado.isBlank() ? null : Catalogo.ESTADO_CUOTA.normalizar(estado);
        List<Cuota> cuotas;
        if (mes != null && est != null) cuotas = repo.findByPeriodoAndEstado(mes, est);
        else if (mes != null) cuotas = repo.findByPeriodo(mes);
        else if (est != null) cuotas = repo.findByEstado(est);
        else throw invalido("Indica el mes (periodo=2026-11) o el estado");
        Set<Long> porAprobar = solicitudes.cuotasConPagoPorAprobar();
        return cuotas.stream()
                .sorted(Comparator.comparing(Cuota::getPeriodo).reversed()
                        .thenComparing(c -> c.getApartamento().getTorre(), CuotaServiceImpl::natural)
                        .thenComparing(c -> c.getApartamento().getApto(), CuotaServiceImpl::natural))
                .map(c -> {
                    CuotaDTO dto = toDTO(c);
                    if (porAprobar.contains(c.getId())) dto.setPagoPorAprobar(true);
                    return dto;
                }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuotaDTO> listarPorApartamento(Long apartamentoId) {
        acceso.verificarApartamentoOCartera(apartamentoId);
        arrendatario.exigirVerCartera(apartamentoId);
        return repo.findByApartamentoIdOrderByPeriodoDesc(apartamentoId).stream().map(this::toDTO).toList();
    }

    @Override
    public CuotaDTO.Generadas generar(CuotaDTO.Generar s) {
        if (s == null || s.periodo() == null) throw invalido("El mes es obligatorio (periodo=2026-11)");
        String periodo = validarPeriodo(s.periodo());
        if (s.valor() == null || s.valor().signum() <= 0) throw invalido("El valor de la cuota debe ser mayor que cero");
        if (s.valor().scale() > 2) throw invalido("El valor admite máximo dos decimales");
        YearMonth mes = YearMonth.parse(periodo);
        LocalDate limite = s.fechaLimite() != null ? s.fechaLimite() : mes.atDay(DIA_LIMITE_POR_DEFECTO);
        if (limite.isBefore(mes.atDay(1))) throw invalido("La fecha límite no puede ser antes del mes de la cuota");

        // P5: la del contador espera al administrador
        if (esContador()) {
            if (solicitudes.existsByTipoAndEstadoAndPeriodo(SolicitudContable.FACTURACION, SolicitudContable.PENDIENTE, periodo)) {
                throw invalido("La facturación de ese mes ya está esperando la aprobación del administrador");
            }
            SolicitudContable sol = new SolicitudContable();
            sol.setTipo(SolicitudContable.FACTURACION);
            sol.setPeriodo(periodo);
            sol.setValor(s.valor());
            sol.setFechaLimite(limite);
            sol.setSolicitadaPor(usuarioActual());
            sol.setCreadaEn(LocalDateTime.now(zonaHoraria));
            SolicitudContable guardada = solicitudes.save(sol);
            notificaciones.avisarSolicitudContable(guardada, "Facturación de " + nombreMes(periodo) + " — "
                    + pesos(s.valor()) + " por apartamento");
            return new CuotaDTO.Generadas(periodo, 0, repo.apartamentosConPeriodo(periodo).size(), true, guardada.getId());
        }
        return generarAhora(periodo, s.valor(), limite);
    }

    private CuotaDTO.Generadas generarAhora(String periodo, BigDecimal valor, LocalDate limite) {
        // Los que ya la tienen se dejan como están: generar dos veces no duplica
        Set<Long> yaTienen = repo.apartamentosConPeriodo(periodo);
        List<Cuota> nuevas = aptoRepo.findAll().stream()
                .filter(a -> !yaTienen.contains(a.getId()))
                .map(a -> new Cuota(a, periodo, valor, limite))
                .toList();
        repo.saveAll(nuevas);
        for (Cuota c : nuevas) {
            recalcular(c.getApartamento());
            notificaciones.avisarCuotaNueva(c, nombreMes(periodo));
        }
        return new CuotaDTO.Generadas(periodo, nuevas.size(), yaTienen.size());
    }

    @Override
    public CuotaDTO subirComprobante(Long id, MultipartFile archivo) {
        Cuota c = buscar(id);
        acceso.verificarApartamento(c.getApartamento().getId());
        arrendatario.exigirPoderPagar(c.getApartamento().getId());
        if (c.pagada()) throw invalido("Esta cuota ya está pagada");
        if (archivo == null || archivo.isEmpty()) throw invalido("Adjunta el comprobante");
        if (archivo.getSize() > TAMANO_MAXIMO) throw invalido("El comprobante no puede pasar de 5 MB");
        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw invalido("No se pudo leer el comprobante");
        }
        String tipo = tipoReal(datos);
        if (tipo == null) throw invalido("El comprobante debe ser una foto (JPG, PNG, WEBP) o un PDF");

        String anterior = c.getComprobanteRuta();
        String ruta = "apartamento-" + c.getApartamento().getId() + "/" + c.getPeriodo() + "-cuota-" + c.getId()
                + "-" + System.currentTimeMillis() + "." + EXTENSIONES.get(tipo);
        almacen.guardar(ruta, datos, tipo);
        c.setComprobanteRuta(ruta);
        c.setComprobanteTipo(tipo);
        c.setComprobanteSubidoEn(LocalDateTime.now(zonaHoraria));
        c.setEstado(Cuota.EN_REVISION);
        c.setMotivoRechazo(null);
        Cuota guardada = repo.save(c);
        if (anterior != null) borrarSinFallar(anterior);
        recalcular(guardada.getApartamento());
        notificaciones.avisarComprobante(guardada, nombreMes(guardada.getPeriodo()));
        return toDTO(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public CuotaDTO.Enlace enlaceComprobante(Long id) {
        Cuota c = buscar(id);
        acceso.verificarApartamentoOCartera(c.getApartamento().getId());
        arrendatario.exigirVerCartera(c.getApartamento().getId());
        if (c.getComprobanteRuta() == null) throw new RecursoNoEncontradoException("Esta cuota no tiene comprobante");
        return new CuotaDTO.Enlace(almacen.enlace(c.getComprobanteRuta(), VIGENCIA_ENLACE), c.getComprobanteTipo(),
                LocalDateTime.now(zonaHoraria).plus(VIGENCIA_ENLACE));
    }

    @Override
    public CuotaDTO revisar(Long id, String estado, String motivo) {
        String nuevo = Catalogo.ESTADO_CUOTA.normalizar(estado);
        Cuota c = buscar(id);
        String razon = motivo == null || motivo.isBlank() ? null : motivo.trim();
        switch (nuevo) {
            case Cuota.PAGADA -> {
                // También sin comprobante: pagos en efectivo o en la oficina. P5: el del contador sin
                // comprobante espera al administrador; la cuota no cambia hasta entonces
                if (esContador() && c.getComprobanteRuta() == null && !c.pagada()) return pagoPorAprobar(c);
                c.setPagadaEn(LocalDateTime.now(zonaHoraria));
                c.setMotivoRechazo(null);
            }
            case Cuota.RECHAZADA -> {
                if (c.getComprobanteRuta() == null || c.pagada()) {
                    throw invalido("Solo se rechaza un comprobante que está en revisión");
                }
                c.setMotivoRechazo(razon);
            }
            default -> throw invalido("Indica si la cuota queda pagada o si rechazas el comprobante");
        }
        c.setEstado(nuevo);
        Cuota guardada = repo.save(c);
        recalcular(guardada.getApartamento());
        notificaciones.avisarCuotaRevisada(guardada, nombreMes(guardada.getPeriodo()));
        return toDTO(guardada);
    }

    @Override
    public CuotaDTO actualizar(Long id, CuotaDTO.Cambio cambio) {
        Cuota c = buscar(id);
        if (c.pagada()) throw invalido("Una cuota pagada no se puede cambiar");
        if (cambio.valor() != null) {
            if (cambio.valor().signum() <= 0) throw invalido("El valor de la cuota debe ser mayor que cero");
            if (cambio.valor().scale() > 2) throw invalido("El valor admite máximo dos decimales");
            c.setValor(cambio.valor());
        }
        if (cambio.fechaLimite() != null) {
            if (cambio.fechaLimite().isBefore(YearMonth.parse(c.getPeriodo()).atDay(1))) {
                throw invalido("La fecha límite no puede ser antes del mes de la cuota");
            }
            c.setFechaLimite(cambio.fechaLimite());
        }
        Cuota guardada = repo.save(c);
        recalcular(guardada.getApartamento());
        return toDTO(guardada);
    }

    /** Una cuota generada por error. Las pagadas se conservan: son el historial de pagos. */
    @Override
    public void eliminar(Long id) {
        Cuota c = buscar(id);
        if (c.pagada()) throw invalido("Una cuota pagada no se puede eliminar");
        Apartamento a = c.getApartamento();
        if (c.getComprobanteRuta() != null) borrarSinFallar(c.getComprobanteRuta());
        repo.delete(c);
        repo.flush();
        recalcular(a);
    }

    private CuotaDTO pagoPorAprobar(Cuota c) {
        if (solicitudes.cuotasConPagoPorAprobar().contains(c.getId())) {
            throw invalido("Ese pago ya está esperando la aprobación del administrador");
        }
        SolicitudContable sol = new SolicitudContable();
        sol.setTipo(SolicitudContable.PAGO_MANUAL);
        sol.setCuota(c);
        sol.setPeriodo(c.getPeriodo());
        sol.setValor(c.getValor());
        sol.setSolicitadaPor(usuarioActual());
        sol.setCreadaEn(LocalDateTime.now(zonaHoraria));
        SolicitudContable guardada = solicitudes.save(sol);
        notificaciones.avisarSolicitudContable(guardada, "Pago en efectivo de " + nombreMes(c.getPeriodo()) + " — "
                + pesos(c.getValor()) + " · Torre " + c.getApartamento().getTorre() + " Apto " + c.getApartamento().getApto());
        CuotaDTO dto = toDTO(c);
        dto.setPagoPorAprobar(true);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuotaDTO.Solicitud> listarSolicitudes(String estado) {
        String est = estado == null || estado.isBlank() ? null : estado.trim().toUpperCase(Locale.ROOT);
        List<SolicitudContable> lista = est == null ? solicitudes.findAllByOrderByCreadaEnDesc()
                : solicitudes.findByEstadoOrderByCreadaEnDesc(est);
        return lista.stream().map(this::solicitudDTO).toList();
    }

    /** Administrador: aprobar ejecuta lo que pidió el contador (generar el mes o marcar la cuota pagada). */
    @Override
    public CuotaDTO.Solicitud revisarSolicitud(Long id, boolean aprobada, String motivo) {
        SolicitudContable sol = solicitudes.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada: " + id));
        if (!SolicitudContable.PENDIENTE.equals(sol.getEstado())) throw invalido("Esa solicitud ya fue revisada");
        if (aprobada) {
            if (SolicitudContable.FACTURACION.equals(sol.getTipo())) {
                generarAhora(sol.getPeriodo(), sol.getValor(), sol.getFechaLimite());
            } else {
                Cuota c = sol.getCuota();
                if (c == null) throw invalido("La cuota de esa solicitud ya no existe");
                if (!c.pagada()) {
                    c.setPagadaEn(LocalDateTime.now(zonaHoraria));
                    c.setMotivoRechazo(null);
                    c.setEstado(Cuota.PAGADA);
                    Cuota guardada = repo.save(c);
                    recalcular(guardada.getApartamento());
                    notificaciones.avisarCuotaRevisada(guardada, nombreMes(guardada.getPeriodo()));
                }
            }
        }
        sol.setEstado(aprobada ? SolicitudContable.APROBADA : SolicitudContable.RECHAZADA);
        sol.setMotivoRechazo(aprobada || motivo == null || motivo.isBlank() ? null : motivo.trim());
        sol.setRevisadaEn(LocalDateTime.now(zonaHoraria));
        return solicitudDTO(solicitudes.save(sol));
    }

    /** Cuotas del mes por estado, lo facturado y lo recaudado, y cómo está la cartera de cada apartamento. */
    @Override
    @Transactional(readOnly = true)
    public CuotaDTO.Estadisticas estadisticas(String periodo) {
        String mes = periodo == null || periodo.isBlank() ? YearMonth.now(zonaHoraria).toString() : validarPeriodo(periodo);
        List<Cuota> cuotas = repo.findByPeriodo(mes);
        int pagadas = (int) cuotas.stream().filter(Cuota::pagada).count();
        int enRevision = (int) cuotas.stream().filter(c -> Cuota.EN_REVISION.equals(c.getEstado())).count();
        int rechazadas = (int) cuotas.stream().filter(c -> Cuota.RECHAZADA.equals(c.getEstado())).count();
        int vencidas = (int) cuotas.stream().filter(this::vencida).count();
        BigDecimal facturado = cuotas.stream().map(Cuota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recaudado = cuotas.stream().filter(Cuota::pagada).map(Cuota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        int porcentaje = facturado.signum() == 0 ? 0
                : recaudado.multiply(BigDecimal.valueOf(100)).divide(facturado, 0, java.math.RoundingMode.DOWN).intValue();
        List<Pago> pagos = pagoRepo.findAll();
        int apartamentos = (int) aptoRepo.count();
        int enMora = (int) pagos.stream().filter(p -> "En Mora".equals(p.getEstadoCuenta())).count();
        BigDecimal cartera = pagos.stream().map(p -> BigDecimal.valueOf(p.getSaldoPendiente() == null ? 0 : p.getSaldoPendiente()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, java.math.RoundingMode.HALF_UP);
        return new CuotaDTO.Estadisticas(mes, cuotas.size(), pagadas, enRevision, cuotas.size() - pagadas - enRevision - rechazadas,
                vencidas, rechazadas, facturado, recaudado, facturado.subtract(recaudado), porcentaje,
                apartamentos, apartamentos - enMora, enMora, cartera, solicitudes.countByEstado(SolicitudContable.PENDIENTE));
    }

    private CuotaDTO.Solicitud solicitudDTO(SolicitudContable s) {
        Cuota c = s.getCuota();
        return new CuotaDTO.Solicitud(s.getId(), s.getTipo(), s.getEstado(), s.getPeriodo(), s.getValor(), s.getFechaLimite(),
                c == null ? null : c.getId(), c == null ? null : c.getApartamento().getTorre(),
                c == null ? null : c.getApartamento().getApto(), s.getSolicitadaPor(), s.getCreadaEn(), s.getRevisadaEn(),
                s.getMotivoRechazo());
    }

    private boolean esContador() {
        return acceso.tokenActual().map(t -> TokenService.ROL_CONTADOR.equals(t.getClaimAsString(TokenService.CLAIM_ROL))).orElse(false);
    }

    private String usuarioActual() {
        return acceso.tokenActual().map(t -> {
            String u = t.getClaimAsString("usuario");
            return u != null ? u : t.getSubject();
        }).orElse("contador");
    }

    @Override
    public void recalcularCartera(Long apartamentoId) {
        recalcular(aptoRepo.findById(apartamentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado: " + apartamentoId)));
    }

    /** Saldo = lo que falta por pagar; "En Mora" si alguna cuota sin pagar ya venció. */
    private void recalcular(Apartamento a) {
        List<Cuota> pendientes = repo.findByApartamentoIdOrderByPeriodoDesc(a.getId()).stream()
                .filter(c -> !c.pagada()).toList();
        BigDecimal saldo = pendientes.stream().map(Cuota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean mora = pendientes.stream().anyMatch(this::vencida);
        Pago pago = pagoRepo.findByApartamentoId(a.getId()).orElseGet(() -> new Pago(null, 0.0, "Al Día", null, a));
        pago.setSaldoPendiente(saldo.doubleValue());
        pago.setEstadoCuenta(mora ? "En Mora" : "Al Día");
        pago.setObservaciones(pendientes.isEmpty() ? "Sin cuotas pendientes"
                : "Debe " + pendientes.stream().sorted(Comparator.comparing(Cuota::getPeriodo))
                        .map(c -> nombreMes(c.getPeriodo())).collect(Collectors.joining(", ")));
        pagoRepo.save(pago);
    }

    private boolean vencida(Cuota c) {
        return !c.pagada() && c.getFechaLimite().isBefore(LocalDate.now(zonaHoraria));
    }

    private Cuota buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cuota no encontrada: " + id));
    }

    private void borrarSinFallar(String ruta) {
        try {
            almacen.borrar(ruta);
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar el comprobante anterior {}", ruta);
        }
    }

    private static String validarPeriodo(String periodo) {
        String p = periodo.trim();
        if (!PERIODO.matcher(p).matches()) throw invalido("El mes debe tener la forma 2026-11");
        return p;
    }

    /** "2026-11" → "noviembre 2026". */
    private static String pesos(BigDecimal valor) {
        return "$" + java.text.NumberFormat.getIntegerInstance(ESPANOL).format(valor);
    }

    static String nombreMes(String periodo) {
        YearMonth m = YearMonth.parse(periodo);
        return m.getMonth().getDisplayName(TextStyle.FULL, ESPANOL) + " " + m.getYear();
    }

    // Por el contenido y no por lo que dice el celular: el nombre o el tipo se pueden falsear
    private static String tipoReal(byte[] d) {
        if (empieza(d, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (empieza(d, 0x89, 'P', 'N', 'G')) return "image/png";
        if (empieza(d, '%', 'P', 'D', 'F')) return "application/pdf";
        if (empieza(d, 'R', 'I', 'F', 'F') && d.length > 12 && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static boolean empieza(byte[] d, int... firma) {
        if (d.length < firma.length) return false;
        for (int i = 0; i < firma.length; i++) if ((d[i] & 0xFF) != firma[i]) return false;
        return true;
    }

    // "2" antes que "10" (torres y aptos numéricos); si no son números, alfabético
    private static int natural(String a, String b) {
        try {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
