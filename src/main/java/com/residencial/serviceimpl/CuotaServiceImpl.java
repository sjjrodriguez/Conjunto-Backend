package com.residencial.serviceimpl;

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

    public CuotaServiceImpl(CuotaRepository repo, ApartamentoRepository aptoRepo, PagoRepository pagoRepo,
                            AccesoService acceso, NotificacionService notificaciones, AlmacenArchivos almacen,
                            @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
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
        return cuotas.stream()
                .sorted(Comparator.comparing(Cuota::getPeriodo).reversed()
                        .thenComparing(c -> c.getApartamento().getTorre(), CuotaServiceImpl::natural)
                        .thenComparing(c -> c.getApartamento().getApto(), CuotaServiceImpl::natural))
                .map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuotaDTO> listarPorApartamento(Long apartamentoId) {
        acceso.verificarApartamentoOCartera(apartamentoId);
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

        // Los que ya la tienen se dejan como están: generar dos veces no duplica
        Set<Long> yaTienen = repo.apartamentosConPeriodo(periodo);
        List<Cuota> nuevas = aptoRepo.findAll().stream()
                .filter(a -> !yaTienen.contains(a.getId()))
                .map(a -> new Cuota(a, periodo, s.valor(), limite))
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
                // También sin comprobante: pagos en efectivo o en la oficina
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
