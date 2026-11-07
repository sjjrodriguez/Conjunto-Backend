package com.residencial.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.residencial.dto.CuotaDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.*;
import com.residencial.repository.*;
import com.residencial.security.AccesoService;
import com.residencial.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * P11 bloques 6 y 7: lo que el contador cambia en la cartera (facturación del mes, una cuota
 * nueva, un cambio, anular una cuota o un pago en la oficina) va como solicitud al administrador
 * con todo el detalle. Si la rechaza, vuelve al contador con el motivo para corregirla y
 * reenviarla; si ya estaba aprobada, el contador puede editarla y la edición vuelve a aprobación.
 * Cada paso queda en el historial con su versión.
 */
@Service
@Transactional
public class ContabilidadService {

    // --- Lo que se pide en cada tipo (el JSON de "detalle") ---
    /** modo FIJO: [valor] a cada apartamento; COEFICIENTE: [total] repartido por coeficiente. */
    public record Facturacion(String periodo, String concepto, String modo, BigDecimal valor, BigDecimal total,
                              LocalDate fechaLimite, BigDecimal interesMora, String descripcion, List<Long> excluidos) {}
    public record CuotaNueva(Long apartamentoId, String periodo, String concepto, BigDecimal valor, LocalDate fechaLimite,
                             String descripcion) {}
    public record CuotaCambio(Long cuotaId, BigDecimal valor, LocalDate fechaLimite, String concepto, String descripcion) {}
    public record CuotaAnular(Long cuotaId, String motivo) {}
    public record PagoManual(Long cuotaId, String medio, LocalDate fechaPago, String referencia, String nota) {}

    // --- Peticiones ---
    public record Envio(String tipo, JsonNode detalle, String nota) {}
    public record Propuesta(JsonNode detalle, String nota) {}
    public record Coeficiente(BigDecimal coeficiente) {}

    // --- Respuestas ---
    public record Solicitud(Long id, String tipo, String estado, int version, boolean edicion, String resumen,
                            String periodo, BigDecimal valor, LocalDate fechaLimite, Long cuotaId, Long apartamentoId,
                            String torre, String apto, String solicitadaPor, LocalDateTime creadaEn,
                            LocalDateTime actualizadaEn, LocalDateTime revisadaEn, String revisadaPor,
                            String motivoRechazo, String nota) {}
    public record Linea(String etiqueta, String valor) {}
    public record PasoHistorial(int version, String accion, String autor, LocalDateTime cuando, String motivo, List<Linea> lineas) {}
    public record ValorApto(Long apartamentoId, String torre, String apto, BigDecimal valor) {}
    public record VistaPrevia(int apartamentos, BigDecimal total, BigDecimal minimo, BigDecimal maximo, int yaTienen,
                              int sinCoeficiente, List<ValorApto> valores, List<String> avisos) {}
    public record Detalle(Solicitud solicitud, JsonNode detalle, List<Linea> lineas, List<Linea> lineasAprobadas,
                          VistaPrevia vistaPrevia, List<PasoHistorial> historial, boolean puedeEditar, boolean puedeRevisar) {}
    public record AptoCartera(Long apartamentoId, String torre, String apto, BigDecimal saldo, String estadoCuenta,
                              int pendientes, int vencidas, BigDecimal coeficiente, int solicitudesAbiertas) {}
    public record CarteraApartamento(AptoCartera resumen, List<CuotaDTO> cuotas, List<Solicitud> solicitudes) {}
    public record Recaudo(String periodo, BigDecimal facturado, BigDecimal recaudado) {}
    public record Panel(CuotaDTO.Estadisticas mes, List<AptoCartera> morosos, long misPendientes, long misDevueltas,
                        long pendientes, List<Recaudo> recaudo) {}

    public static final String FIJO = "FIJO";
    public static final String COEFICIENTE = "COEFICIENTE";
    private static final Pattern PERIODO = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");
    private static final Set<String> MEDIOS = Set.of("EFECTIVO", "TRANSFERENCIA", "CONSIGNACION", "CHEQUE", "OTRO");
    private static final Set<String> TIPOS = Set.of(SolicitudContable.FACTURACION, SolicitudContable.PAGO_MANUAL,
            SolicitudContable.CUOTA_NUEVA, SolicitudContable.CUOTA_CAMBIO, SolicitudContable.CUOTA_ANULAR);

    private final SolicitudContableRepository solicitudes;
    private final SolicitudContableHistorialRepository historial;
    private final CuotaRepository cuotas;
    private final ApartamentoRepository apartamentos;
    private final PagoRepository pagos;
    private final CuotaService cuotaService;
    private final NotificacionService notificaciones;
    private final AccesoService acceso;
    private final ObjectMapper json;
    private final ZoneId zona;

    public ContabilidadService(SolicitudContableRepository solicitudes, SolicitudContableHistorialRepository historial,
                               CuotaRepository cuotas, ApartamentoRepository apartamentos, PagoRepository pagos,
                               CuotaService cuotaService, NotificacionService notificaciones, AccesoService acceso,
                               ObjectMapper json, @Value("${app.zona-horaria:America/Bogota}") String zona) {
        this.solicitudes = solicitudes;
        this.historial = historial;
        this.cuotas = cuotas;
        this.apartamentos = apartamentos;
        this.pagos = pagos;
        this.cuotaService = cuotaService;
        this.notificaciones = notificaciones;
        this.acceso = acceso;
        this.json = json;
        this.zona = ZoneId.of(zona);
    }

    // =====================================================================================
    // Solicitudes
    // =====================================================================================

    /** El contador manda algo a aprobar. Si lo hace el administrador, queda aprobado de una vez. */
    public Solicitud enviar(Envio e) {
        if (e == null || e.tipo() == null) throw invalido("Indica qué se pide (tipo)");
        String tipo = e.tipo().trim().toUpperCase(Locale.ROOT);
        if (!TIPOS.contains(tipo)) throw invalido("Tipo de solicitud desconocido: " + e.tipo());
        if (e.detalle() == null || !e.detalle().isObject()) throw invalido("Falta el detalle de la solicitud");
        SolicitudContable s = new SolicitudContable();
        s.setTipo(tipo);
        s.setSolicitadaPor(usuario());
        LocalDateTime ahora = LocalDateTime.now(zona);
        s.setCreadaEn(ahora);
        s.setActualizadaEn(ahora);
        s.setNota(nota(e.nota()));
        validarYPoner(s, e.detalle(), false);
        s = solicitudes.save(s);
        anotar(s, SolicitudContableHistorial.ENVIADA, null);
        if (!esContador()) return aprobarComoAdministrador(s);
        notificaciones.avisarSolicitudContable(s, resumen(s));
        return dto(s);
    }

    /** El contador corrige (pendiente o devuelta) o edita algo ya aprobado: vuelve a aprobación. */
    public Solicitud proponer(Long id, Propuesta p) {
        if (!esContador()) throw new AccessDeniedException("Solo el contador cambia sus solicitudes");
        SolicitudContable s = buscar(id);
        if (p == null || p.detalle() == null || !p.detalle().isObject()) throw invalido("Falta el detalle de la solicitud");
        String accion;
        switch (s.getEstado()) {
            case SolicitudContable.PENDIENTE, SolicitudContable.RECHAZADA -> accion = SolicitudContableHistorial.CORREGIDA;
            case SolicitudContable.APROBADA -> {
                if (SolicitudContable.CUOTA_ANULAR.equals(s.getTipo())) throw invalido("Una cuota anulada no se puede editar");
                s.setEdicion(true);
                accion = SolicitudContableHistorial.EDITADA;
            }
            default -> throw invalido("Esta solicitud no se puede cambiar");
        }
        validarYPoner(s, p.detalle(), s.isEdicion());
        s.setNota(nota(p.nota()));
        s.setEstado(SolicitudContable.PENDIENTE);
        s.setVersion(s.getVersion() + 1);
        s.setActualizadaEn(LocalDateTime.now(zona));
        s.setMotivoRechazo(null);
        s = solicitudes.save(s);
        anotar(s, accion, null);
        notificaciones.avisarSolicitudContable(s, (s.isEdicion() ? "Edición (versión " : "Corregida (versión ") + s.getVersion() + ") — " + resumen(s));
        return dto(s);
    }

    /** El administrador aprueba (se aplica lo pedido) o la devuelve con el motivo. */
    public Solicitud revisar(Long id, boolean aprobada, String motivo) {
        SolicitudContable s = buscar(id);
        if (!SolicitudContable.PENDIENTE.equals(s.getEstado())) throw invalido("Esa solicitud ya fue revisada");
        String razon = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (!aprobada && razon == null) throw invalido("Escribe el motivo: el contador lo verá para corregirla");
        if (razon != null && razon.length() > 1000) throw invalido("El motivo puede tener máximo 1000 caracteres");
        if (aprobada) {
            aplicar(s);
            s.setDetalleAprobado(s.getDetalle());
        }
        s.setEstado(aprobada ? SolicitudContable.APROBADA : SolicitudContable.RECHAZADA);
        s.setMotivoRechazo(aprobada ? null : razon);
        s.setRevisadaEn(LocalDateTime.now(zona));
        s.setRevisadaPor(usuario());
        s = solicitudes.save(s);
        anotar(s, aprobada ? SolicitudContableHistorial.APROBADA : SolicitudContableHistorial.RECHAZADA, razon);
        notificaciones.avisarSolicitudRevisada(s, resumen(s));
        return dto(s);
    }

    private Solicitud aprobarComoAdministrador(SolicitudContable s) {
        aplicar(s);
        s.setDetalleAprobado(s.getDetalle());
        s.setEstado(SolicitudContable.APROBADA);
        s.setRevisadaEn(LocalDateTime.now(zona));
        s.setRevisadaPor(usuario());
        s = solicitudes.save(s);
        anotar(s, SolicitudContableHistorial.APROBADA, null);
        return dto(s);
    }

    /** estado: PENDIENTE, APROBADA, RECHAZADA (o DEVUELTA); sin estado, todas. Las más recientes primero. */
    @Transactional(readOnly = true)
    public List<Solicitud> listar(String estado) {
        String est = estado == null || estado.isBlank() ? null : estado.trim().toUpperCase(Locale.ROOT);
        if ("DEVUELTA".equals(est) || "DEVUELTAS".equals(est)) est = SolicitudContable.RECHAZADA;
        List<SolicitudContable> lista = est == null ? solicitudes.findAllByOrderByCreadaEnDesc()
                : solicitudes.findByEstadoOrderByCreadaEnDesc(est);
        return lista.stream()
                .sorted(Comparator.comparing((SolicitudContable x) -> x.getActualizadaEn() == null ? x.getCreadaEn() : x.getActualizadaEn()).reversed())
                .map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public Detalle detalle(Long id) {
        SolicitudContable s = buscar(id);
        JsonNode d = leer(s.getDetalle());
        List<PasoHistorial> pasos = historial.findBySolicitudIdOrderByIdAsc(id).stream()
                .map(h -> new PasoHistorial(h.getVersion(), h.getAccion(), h.getAutor(), h.getCreadoEn(), h.getMotivo(),
                        h.getDetalle() == null ? List.of() : lineas(s.getTipo(), leer(h.getDetalle()))))
                .toList();
        VistaPrevia vista = SolicitudContable.FACTURACION.equals(s.getTipo()) && d != null
                ? vistaPrevia(json.convertValue(d, Facturacion.class), s.isEdicion() ? s.getId() : null) : null;
        boolean contador = esContador();
        boolean puedeEditar = contador && (SolicitudContable.PENDIENTE.equals(s.getEstado())
                || SolicitudContable.RECHAZADA.equals(s.getEstado())
                || (SolicitudContable.APROBADA.equals(s.getEstado()) && !SolicitudContable.CUOTA_ANULAR.equals(s.getTipo())));
        boolean puedeRevisar = !contador && SolicitudContable.PENDIENTE.equals(s.getEstado());
        return new Detalle(dto(s), d, d == null ? List.of() : lineas(s.getTipo(), d),
                s.isEdicion() && s.getDetalleAprobado() != null ? lineas(s.getTipo(), leer(s.getDetalleAprobado())) : List.of(),
                vista, pasos, puedeEditar, puedeRevisar);
    }

    // =====================================================================================
    // Validar lo pedido y dejarlo listo en la solicitud
    // =====================================================================================

    private void validarYPoner(SolicitudContable s, JsonNode detalle, boolean edicion) {
        LocalDate hoy = LocalDate.now(zona);
        switch (s.getTipo()) {
            case SolicitudContable.FACTURACION -> {
                Facturacion f = leer(detalle, Facturacion.class);
                String periodo = periodo(f.periodo());
                String concepto = concepto(f.concepto());
                String modo = f.modo() == null || f.modo().isBlank() ? FIJO : f.modo().trim().toUpperCase(Locale.ROOT);
                if (!FIJO.equals(modo) && !COEFICIENTE.equals(modo)) throw invalido("El modo debe ser FIJO o COEFICIENTE");
                BigDecimal valor = null;
                BigDecimal total = null;
                if (FIJO.equals(modo)) valor = dinero(f.valor(), "El valor por apartamento");
                else total = dinero(f.total(), "El total a repartir");
                YearMonth mes = YearMonth.parse(periodo);
                LocalDate limite = f.fechaLimite() != null ? f.fechaLimite() : mes.atDay(10);
                if (limite.isBefore(mes.atDay(1))) throw invalido("La fecha límite no puede ser antes del mes de la cuota");
                BigDecimal interes = f.interesMora();
                if (interes != null && (interes.signum() < 0 || interes.compareTo(new BigDecimal("10")) > 0)) {
                    throw invalido("El interés de mora debe estar entre 0 y 10 % mensual");
                }
                String descripcion = texto(f.descripcion(), 300, "La descripción");
                List<Long> excluidos = f.excluidos() == null ? List.of() : f.excluidos().stream().filter(Objects::nonNull).distinct().toList();
                // Después de aprobada, el mes y el concepto quedan: las cuotas ya existen con ellos
                if (edicion && s.getDetalleAprobado() != null) {
                    Facturacion antes = leer(leer(s.getDetalleAprobado()), Facturacion.class);
                    if (!periodo.equals(antes.periodo()) || !concepto.equals(antes.concepto())) {
                        throw invalido("El mes y el concepto no se cambian después de aprobada; anula esas cuotas y factura de nuevo");
                    }
                } else if (!SolicitudContable.APROBADA.equals(s.getEstado())) {
                    boolean otra = solicitudes.findByEstadoOrderByCreadaEnDesc(SolicitudContable.PENDIENTE).stream()
                            .filter(x -> !x.getId().equals(s.getId()) && SolicitudContable.FACTURACION.equals(x.getTipo()))
                            .anyMatch(x -> periodo.equals(x.getPeriodo()) && concepto.equals(leer(leer(x.getDetalle()), Facturacion.class).concepto()));
                    if (otra) throw invalido("La facturación de " + nombreMes(periodo) + " (" + concepto + ") ya está esperando al administrador");
                }
                Facturacion limpio = new Facturacion(periodo, concepto, modo, valor, total, limite, interes, descripcion, excluidos);
                if (COEFICIENTE.equals(modo)) {
                    VistaPrevia v = vistaPrevia(limpio, null);
                    if (v.sinCoeficiente() > 0) {
                        throw invalido(v.sinCoeficiente() + " apartamentos no tienen coeficiente. Ponlo en su cartera o exclúyelos");
                    }
                }
                s.setPeriodo(periodo);
                s.setValor(valor != null ? valor : total);
                s.setFechaLimite(limite);
                s.setDetalle(escribir(limpio));
            }
            case SolicitudContable.CUOTA_NUEVA -> {
                CuotaNueva c = leer(detalle, CuotaNueva.class);
                Apartamento a = apartamento(c.apartamentoId());
                String periodo = periodo(c.periodo());
                String concepto = concepto(c.concepto());
                BigDecimal valor = dinero(c.valor(), "El valor");
                YearMonth mes = YearMonth.parse(periodo);
                LocalDate limite = c.fechaLimite() != null ? c.fechaLimite() : mes.atDay(10);
                if (limite.isBefore(mes.atDay(1))) throw invalido("La fecha límite no puede ser antes del mes de la cuota");
                String descripcion = texto(c.descripcion(), 300, "La descripción");
                boolean yaExiste = cuotas.existsByApartamentoIdAndPeriodoAndConcepto(a.getId(), periodo, concepto);
                // En una edición, la cuota que ya existe es la que creó esta misma solicitud
                boolean esLaSuya = edicion && cuotas.findBySolicitudId(s.getId()).stream()
                        .anyMatch(x -> x.getPeriodo().equals(periodo) && x.getConcepto().equals(concepto) && x.getApartamento().getId().equals(a.getId()));
                if (yaExiste && !esLaSuya) throw invalido("Ese apartamento ya tiene una cuota de " + concepto + " en " + nombreMes(periodo));
                s.setApartamento(a);
                s.setPeriodo(periodo);
                s.setValor(valor);
                s.setFechaLimite(limite);
                s.setDetalle(escribir(new CuotaNueva(a.getId(), periodo, concepto, valor, limite, descripcion)));
            }
            case SolicitudContable.CUOTA_CAMBIO -> {
                CuotaCambio c = leer(detalle, CuotaCambio.class);
                Cuota cuota = cuotaAbierta(c.cuotaId(), s);
                BigDecimal valor = c.valor() == null ? null : dinero(c.valor(), "El valor");
                LocalDate limite = c.fechaLimite();
                if (limite != null && limite.isBefore(YearMonth.parse(cuota.getPeriodo()).atDay(1))) {
                    throw invalido("La fecha límite no puede ser antes del mes de la cuota");
                }
                String concepto = c.concepto() == null || c.concepto().isBlank() ? null : concepto(c.concepto());
                String descripcion = texto(c.descripcion(), 300, "La descripción");
                if (valor == null && limite == null && concepto == null && descripcion == null) throw invalido("No hay nada que cambiar");
                s.setCuota(cuota);
                s.setApartamento(cuota.getApartamento());
                s.setPeriodo(cuota.getPeriodo());
                s.setValor(valor != null ? valor : cuota.getValor());
                s.setFechaLimite(limite != null ? limite : cuota.getFechaLimite());
                s.setDetalle(escribir(new CuotaCambio(cuota.getId(), valor, limite, concepto, descripcion)));
            }
            case SolicitudContable.CUOTA_ANULAR -> {
                CuotaAnular c = leer(detalle, CuotaAnular.class);
                Cuota cuota = cuotaAbierta(c.cuotaId(), s);
                String motivo = texto(c.motivo(), 300, "El motivo");
                if (motivo == null) throw invalido("Escribe por qué se anula la cuota");
                s.setCuota(cuota);
                s.setApartamento(cuota.getApartamento());
                s.setPeriodo(cuota.getPeriodo());
                s.setValor(cuota.getValor());
                s.setDetalle(escribir(new CuotaAnular(cuota.getId(), motivo)));
            }
            case SolicitudContable.PAGO_MANUAL -> {
                PagoManual pm = leer(detalle, PagoManual.class);
                Cuota cuota = cuotas.findById(pm.cuotaId() == null ? -1 : pm.cuotaId())
                        .orElseThrow(() -> new RecursoNoEncontradoException("Cuota no encontrada"));
                if (!edicion && cuota.pagada()) throw invalido("Esa cuota ya está pagada");
                if (!edicion && pendienteDeOtra(cuota.getId(), s)) throw invalido("Esa cuota ya tiene algo esperando al administrador");
                String medio = pm.medio() == null || pm.medio().isBlank() ? "EFECTIVO" : pm.medio().trim().toUpperCase(Locale.ROOT);
                if (!MEDIOS.contains(medio)) throw invalido("Medio de pago: efectivo, transferencia, consignación, cheque u otro");
                LocalDate fecha = pm.fechaPago() != null ? pm.fechaPago() : hoy;
                if (fecha.isAfter(hoy)) throw invalido("La fecha del pago no puede ser futura");
                s.setCuota(cuota);
                s.setApartamento(cuota.getApartamento());
                s.setPeriodo(cuota.getPeriodo());
                s.setValor(cuota.getValor());
                s.setDetalle(escribir(new PagoManual(cuota.getId(), medio, fecha, texto(pm.referencia(), 80, "La referencia"),
                        texto(pm.nota(), 300, "La nota"))));
            }
            default -> throw invalido("Tipo de solicitud desconocido");
        }
    }

    /** Una cuota sin pagar y sin otro cambio esperando (aparte de esta misma solicitud). */
    private Cuota cuotaAbierta(Long cuotaId, SolicitudContable s) {
        Cuota c = cuotas.findById(cuotaId == null ? -1 : cuotaId).orElseThrow(() -> new RecursoNoEncontradoException("Cuota no encontrada"));
        if (c.pagada()) throw invalido("Una cuota pagada no se puede cambiar ni anular");
        if (pendienteDeOtra(c.getId(), s)) throw invalido("Esa cuota ya tiene algo esperando al administrador");
        return c;
    }

    private boolean pendienteDeOtra(Long cuotaId, SolicitudContable s) {
        return solicitudes.findByEstadoOrderByCreadaEnDesc(SolicitudContable.PENDIENTE).stream()
                .anyMatch(x -> !x.getId().equals(s.getId()) && x.getCuota() != null && x.getCuota().getId().equals(cuotaId));
    }

    // =====================================================================================
    // Aplicar lo aprobado
    // =====================================================================================

    private void aplicar(SolicitudContable s) {
        JsonNode d = leer(s.getDetalle());
        if (d == null) throw invalido("La solicitud no tiene detalle");
        Set<Long> tocados = new HashSet<>();
        switch (s.getTipo()) {
            case SolicitudContable.FACTURACION -> aplicarFacturacion(s, leer(d, Facturacion.class), tocados);
            case SolicitudContable.CUOTA_NUEVA -> {
                CuotaNueva c = leer(d, CuotaNueva.class);
                Cuota cuota = s.isEdicion() ? cuotas.findBySolicitudId(s.getId()).stream().findFirst().orElse(null) : null;
                if (cuota != null && cuota.pagada()) throw invalido("Esa cuota ya se pagó: no se puede editar");
                boolean nueva = cuota == null;
                if (nueva) {
                    cuota = new Cuota(apartamento(c.apartamentoId()), c.periodo(), c.valor(), c.fechaLimite());
                    cuota.setSolicitudId(s.getId());
                }
                cuota.setPeriodo(c.periodo());
                cuota.setConcepto(c.concepto());
                cuota.setValor(c.valor());
                cuota.setFechaLimite(c.fechaLimite());
                cuota.setDescripcion(c.descripcion());
                cuota = cuotas.save(cuota);
                if (nueva) notificaciones.avisarCuotaNueva(cuota, nombreMes(cuota.getPeriodo()));
                tocados.add(cuota.getApartamento().getId());
            }
            case SolicitudContable.CUOTA_CAMBIO -> {
                CuotaCambio c = leer(d, CuotaCambio.class);
                Cuota cuota = cuotas.findById(c.cuotaId()).orElseThrow(() -> invalido("La cuota de esa solicitud ya no existe"));
                if (cuota.pagada()) throw invalido("Esa cuota ya se pagó: no se puede cambiar");
                if (c.valor() != null) cuota.setValor(c.valor());
                if (c.fechaLimite() != null) cuota.setFechaLimite(c.fechaLimite());
                if (c.concepto() != null) cuota.setConcepto(c.concepto());
                if (c.descripcion() != null) cuota.setDescripcion(c.descripcion());
                cuotas.save(cuota);
                tocados.add(cuota.getApartamento().getId());
            }
            case SolicitudContable.CUOTA_ANULAR -> {
                CuotaAnular c = leer(d, CuotaAnular.class);
                Cuota cuota = cuotas.findById(c.cuotaId()).orElseThrow(() -> invalido("La cuota de esa solicitud ya no existe"));
                if (cuota.pagada()) throw invalido("Esa cuota ya se pagó: no se puede anular");
                // La solicitud deja de apuntar a la cuota antes de borrarla (si no, se borraría con ella)
                s.setCuota(null);
                solicitudes.saveAndFlush(s);
                cuotaService.eliminar(cuota.getId());
            }
            case SolicitudContable.PAGO_MANUAL -> {
                PagoManual p = leer(d, PagoManual.class);
                Cuota cuota = cuotas.findById(p.cuotaId()).orElseThrow(() -> invalido("La cuota de esa solicitud ya no existe"));
                boolean nuevaPagada = !cuota.pagada();
                if (nuevaPagada) {
                    cuota.setEstado(Cuota.PAGADA);
                    cuota.setPagadaEn(p.fechaPago().atTime(12, 0));
                    cuota.setMotivoRechazo(null);
                }
                cuota.setMedioPago(p.medio());
                cuota.setPagoReferencia(p.referencia());
                cuota.setPagoNota(p.nota());
                cuota = cuotas.save(cuota);
                if (nuevaPagada) notificaciones.avisarCuotaRevisada(cuota, nombreMes(cuota.getPeriodo()));
                tocados.add(cuota.getApartamento().getId());
            }
            default -> throw invalido("Tipo de solicitud desconocido");
        }
        cuotas.flush();
        tocados.forEach(cuotaService::recalcularCartera);
    }

    /** Crea (o, en una edición, ajusta) las cuotas del mes. Las ya pagadas no se tocan. */
    private void aplicarFacturacion(SolicitudContable s, Facturacion f, Set<Long> tocados) {
        Map<Long, BigDecimal> valores = calcular(f).stream().collect(Collectors.toMap(ValorApto::apartamentoId, ValorApto::valor));
        Map<Long, Cuota> suyas = new HashMap<>();
        if (s.isEdicion()) {
            for (Cuota c : cuotas.findBySolicitudId(s.getId())) {
                Long apto = c.getApartamento().getId();
                if (c.pagada()) { suyas.put(apto, c); continue; }
                BigDecimal v = valores.get(apto);
                if (v == null) {
                    // Ahora está excluido: se quita si no se ha pagado
                    cuotaService.eliminar(c.getId());
                } else {
                    c.setValor(v);
                    c.setFechaLimite(f.fechaLimite());
                    c.setDescripcion(f.descripcion());
                    cuotas.save(c);
                    suyas.put(apto, c);
                }
                tocados.add(apto);
            }
        }
        Set<Long> yaTienen = cuotas.apartamentosConPeriodoYConcepto(f.periodo(), f.concepto());
        Map<Long, Apartamento> porId = apartamentos.findAll().stream().collect(Collectors.toMap(Apartamento::getId, Function.identity()));
        List<Cuota> nuevas = new ArrayList<>();
        valores.forEach((apto, v) -> {
            if (suyas.containsKey(apto) || yaTienen.contains(apto)) return;
            Cuota c = new Cuota(porId.get(apto), f.periodo(), v, f.fechaLimite());
            c.setConcepto(f.concepto());
            c.setDescripcion(f.descripcion());
            c.setSolicitudId(s.getId());
            nuevas.add(c);
        });
        cuotas.saveAll(nuevas);
        String mes = nombreMes(f.periodo());
        for (Cuota c : nuevas) {
            notificaciones.avisarCuotaNueva(c, mes);
            tocados.add(c.getApartamento().getId());
        }
    }

    /** Lo que le toca a cada apartamento incluido. Por coeficiente, redondeado a pesos. */
    private List<ValorApto> calcular(Facturacion f) {
        Set<Long> fuera = new HashSet<>(f.excluidos() == null ? List.of() : f.excluidos());
        List<Apartamento> incluidos = apartamentos.findAll().stream().filter(a -> !fuera.contains(a.getId()))
                .sorted(Comparator.comparing(Apartamento::getTorre, ContabilidadService::natural).thenComparing(Apartamento::getApto, ContabilidadService::natural))
                .toList();
        if (!COEFICIENTE.equals(f.modo())) {
            return incluidos.stream().map(a -> new ValorApto(a.getId(), a.getTorre(), a.getApto(), f.valor())).toList();
        }
        List<Apartamento> conCoef = incluidos.stream().filter(a -> a.getCoeficiente() != null && a.getCoeficiente().signum() > 0).toList();
        BigDecimal suma = conCoef.stream().map(Apartamento::getCoeficiente).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.signum() == 0) return List.of();
        return conCoef.stream().map(a -> new ValorApto(a.getId(), a.getTorre(), a.getApto(),
                f.total().multiply(a.getCoeficiente()).divide(suma, 0, RoundingMode.HALF_UP))).toList();
    }

    /** Cuántos apartamentos, cuánto en total y a cada uno, y lo que hay que revisar antes de enviar. */
    @Transactional(readOnly = true)
    public VistaPrevia vistaPrevia(Facturacion f, Long solicitudEditada) {
        String modo = f.modo() == null ? FIJO : f.modo().trim().toUpperCase(Locale.ROOT);
        Facturacion n = new Facturacion(f.periodo(), f.concepto() == null || f.concepto().isBlank() ? Cuota.ADMINISTRACION : f.concepto().trim(),
                modo, f.valor(), f.total(), f.fechaLimite(), f.interesMora(), f.descripcion(), f.excluidos());
        List<String> avisos = new ArrayList<>();
        if (FIJO.equals(modo) && (n.valor() == null || n.valor().signum() <= 0)) avisos.add("Falta el valor por apartamento");
        if (COEFICIENTE.equals(modo) && (n.total() == null || n.total().signum() <= 0)) avisos.add("Falta el total a repartir");
        Set<Long> fuera = new HashSet<>(n.excluidos() == null ? List.of() : n.excluidos());
        List<Apartamento> incluidos = apartamentos.findAll().stream().filter(a -> !fuera.contains(a.getId())).toList();
        int sinCoef = COEFICIENTE.equals(modo) ? (int) incluidos.stream()
                .filter(a -> a.getCoeficiente() == null || a.getCoeficiente().signum() <= 0).count() : 0;
        if (sinCoef > 0) avisos.add(sinCoef + " apartamentos no tienen coeficiente");
        List<ValorApto> valores = avisos.stream().anyMatch(a -> a.startsWith("Falta")) ? List.of() : calcular(n);
        int yaTienen = 0;
        if (n.periodo() != null && PERIODO.matcher(n.periodo().trim()).matches()) {
            Set<Long> con = cuotas.apartamentosConPeriodoYConcepto(n.periodo().trim(), n.concepto());
            if (solicitudEditada != null) cuotas.findBySolicitudId(solicitudEditada).forEach(c -> con.remove(c.getApartamento().getId()));
            yaTienen = (int) valores.stream().filter(v -> con.contains(v.apartamentoId())).count();
            if (yaTienen > 0) avisos.add(yaTienen + " apartamentos ya tienen esa cuota ese mes: no se les vuelve a cobrar");
        }
        BigDecimal total = valores.stream().map(ValorApto::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal min = valores.stream().map(ValorApto::valor).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal max = valores.stream().map(ValorApto::valor).max(Comparator.naturalOrder()).orElse(null);
        if (!fuera.isEmpty()) avisos.add(fuera.size() + " apartamentos excluidos");
        return new VistaPrevia(valores.size(), total, min, max, yaTienen, sinCoef, valores, avisos);
    }

    // =====================================================================================
    // Cartera del contador: por torre, por apartamento y el panel
    // =====================================================================================

    @Transactional(readOnly = true)
    public List<AptoCartera> aptos(String torre) {
        String t = torre == null || torre.isBlank() ? null : torre.trim();
        Map<Long, List<Cuota>> porApto = cuotas.findAll().stream().collect(Collectors.groupingBy(c -> c.getApartamento().getId()));
        Map<Long, Pago> pagoDe = pagos.findAll().stream().filter(p -> p.getApartamento() != null)
                .collect(Collectors.toMap(p -> p.getApartamento().getId(), Function.identity(), (a, b) -> a));
        Map<Long, Long> abiertas = solicitudes.findByEstadoOrderByCreadaEnDesc(SolicitudContable.PENDIENTE).stream()
                .map(this::apartamentoDe).filter(Objects::nonNull).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return apartamentos.findAll().stream().filter(a -> t == null || t.equals(a.getTorre()))
                .sorted(Comparator.comparing(Apartamento::getTorre, ContabilidadService::natural).thenComparing(Apartamento::getApto, ContabilidadService::natural))
                .map(a -> resumenApto(a, porApto.getOrDefault(a.getId(), List.of()), pagoDe.get(a.getId()),
                        abiertas.getOrDefault(a.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CarteraApartamento apartamentoCartera(Long id) {
        Apartamento a = apartamento(id);
        List<CuotaDTO> lista = cuotaService.listarPorApartamento(id);
        List<SolicitudContable> suyas = solicitudes.findByApartamentoIdOrderByCreadaEnDesc(id);
        int abiertas = (int) suyas.stream().filter(s -> SolicitudContable.PENDIENTE.equals(s.getEstado())).count();
        AptoCartera r = resumenApto(a, cuotas.findByApartamentoIdOrderByPeriodoDesc(id), pagos.findByApartamentoId(id).orElse(null), abiertas);
        return new CarteraApartamento(r, lista, suyas.stream().map(this::dto).toList());
    }

    /** Administración y contador: el coeficiente se pone directo (no mueve dinero). */
    public AptoCartera ponerCoeficiente(Long id, BigDecimal coeficiente) {
        Apartamento a = apartamento(id);
        if (coeficiente != null && (coeficiente.signum() < 0 || coeficiente.compareTo(new BigDecimal("100")) > 0)) {
            throw invalido("El coeficiente debe estar entre 0 y 100");
        }
        if (coeficiente != null && coeficiente.scale() > 6) throw invalido("El coeficiente admite máximo seis decimales");
        a.setCoeficiente(coeficiente);
        apartamentos.save(a);
        return resumenApto(a, cuotas.findByApartamentoIdOrderByPeriodoDesc(id), pagos.findByApartamentoId(id).orElse(null), 0);
    }

    @Transactional(readOnly = true)
    public Panel panel() {
        CuotaDTO.Estadisticas mes = cuotaService.estadisticas(null);
        List<AptoCartera> morosos = aptos(null).stream().filter(a -> a.vencidas() > 0)
                .sorted(Comparator.comparing(AptoCartera::saldo).reversed()).limit(10).toList();
        String yo = usuario();
        long misPendientes = solicitudes.countByEstadoAndSolicitadaPor(SolicitudContable.PENDIENTE, yo);
        long misDevueltas = solicitudes.countByEstadoAndSolicitadaPor(SolicitudContable.RECHAZADA, yo);
        YearMonth actual = YearMonth.now(zona);
        List<Recaudo> recaudo = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            String p = actual.minusMonths(i).toString();
            List<Cuota> delMes = cuotas.findByPeriodo(p);
            recaudo.add(new Recaudo(p, delMes.stream().map(Cuota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add),
                    delMes.stream().filter(Cuota::pagada).map(Cuota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        return new Panel(mes, morosos, misPendientes, misDevueltas, solicitudes.countByEstado(SolicitudContable.PENDIENTE), recaudo);
    }

    private AptoCartera resumenApto(Apartamento a, List<Cuota> suyas, Pago pago, int abiertas) {
        LocalDate hoy = LocalDate.now(zona);
        List<Cuota> pendientes = suyas.stream().filter(c -> !c.pagada()).toList();
        int vencidas = (int) pendientes.stream().filter(c -> c.getFechaLimite().isBefore(hoy)).count();
        BigDecimal saldo = pendientes.stream().map(Cuota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        String estado = pago != null && pago.getEstadoCuenta() != null ? pago.getEstadoCuenta() : vencidas > 0 ? "En Mora" : "Al Día";
        return new AptoCartera(a.getId(), a.getTorre(), a.getApto(), saldo, estado, pendientes.size(), vencidas,
                a.getCoeficiente(), abiertas);
    }

    // =====================================================================================
    // Textos: resumen y líneas legibles del detalle
    // =====================================================================================

    public String resumen(SolicitudContable s) {
        JsonNode d = leer(s.getDetalle());
        String apto = apartamentoTexto(s);
        try {
            return switch (s.getTipo()) {
                case SolicitudContable.FACTURACION -> {
                    Facturacion f = leer(d, Facturacion.class);
                    yield "Facturación de " + nombreMes(f.periodo()) + " · " + f.concepto() + " — "
                            + (COEFICIENTE.equals(f.modo()) ? pesos(f.total()) + " por coeficiente" : pesos(f.valor()) + " por apartamento");
                }
                case SolicitudContable.CUOTA_NUEVA -> {
                    CuotaNueva c = leer(d, CuotaNueva.class);
                    yield "Cuota nueva · " + c.concepto() + " de " + nombreMes(c.periodo()) + " — " + pesos(c.valor()) + apto;
                }
                case SolicitudContable.CUOTA_CAMBIO -> "Cambio de cuota · " + cuotaTexto(s) + apto;
                case SolicitudContable.CUOTA_ANULAR -> "Anular cuota · " + cuotaTexto(s) + apto;
                case SolicitudContable.PAGO_MANUAL -> {
                    String medio = d == null ? "EFECTIVO" : d.path("medio").asText("EFECTIVO");
                    yield "Pago en " + medio(medio).toLowerCase(ESPANOL) + " de " + nombreMes(s.getPeriodo()) + " — " + pesos(s.getValor()) + apto;
                }
                default -> s.getTipo();
            };
        } catch (RuntimeException e) {
            // Solicitudes de antes de P11 (sin detalle)
            return SolicitudContable.FACTURACION.equals(s.getTipo())
                    ? "Facturación de " + nombreMes(s.getPeriodo()) + " — " + pesos(s.getValor()) + " por apartamento"
                    : "Pago en efectivo de " + nombreMes(s.getPeriodo()) + " — " + pesos(s.getValor()) + apto;
        }
    }

    private List<Linea> lineas(String tipo, JsonNode d) {
        List<Linea> l = new ArrayList<>();
        if (d == null || !d.isObject()) return l;
        switch (tipo) {
            case SolicitudContable.FACTURACION -> {
                Facturacion f = leer(d, Facturacion.class);
                l.add(new Linea("Mes", nombreMes(f.periodo())));
                l.add(new Linea("Concepto", f.concepto()));
                l.add(new Linea("Cómo se cobra", COEFICIENTE.equals(f.modo()) ? "Por coeficiente de copropiedad" : "Valor fijo por apartamento"));
                if (COEFICIENTE.equals(f.modo())) l.add(new Linea("Total a repartir", pesos(f.total())));
                else l.add(new Linea("Valor por apartamento", pesos(f.valor())));
                l.add(new Linea("Fecha límite", fecha(f.fechaLimite())));
                if (f.interesMora() != null && f.interesMora().signum() > 0) {
                    l.add(new Linea("Interés de mora", f.interesMora().stripTrailingZeros().toPlainString() + " % mensual"));
                }
                if (f.descripcion() != null) l.add(new Linea("Descripción", f.descripcion()));
                if (f.excluidos() != null && !f.excluidos().isEmpty()) {
                    Map<Long, Apartamento> porId = apartamentos.findAllById(f.excluidos()).stream()
                            .collect(Collectors.toMap(Apartamento::getId, Function.identity()));
                    l.add(new Linea("Excluidos", f.excluidos().stream().map(porId::get).filter(Objects::nonNull)
                            .map(a -> a.getTorre() + "-" + a.getApto()).collect(Collectors.joining(", "))));
                }
            }
            case SolicitudContable.CUOTA_NUEVA -> {
                CuotaNueva c = leer(d, CuotaNueva.class);
                apartamentos.findById(c.apartamentoId()).ifPresent(a -> l.add(new Linea("Apartamento", "Torre " + a.getTorre() + " · Apto " + a.getApto())));
                l.add(new Linea("Concepto", c.concepto()));
                l.add(new Linea("Mes", nombreMes(c.periodo())));
                l.add(new Linea("Valor", pesos(c.valor())));
                l.add(new Linea("Fecha límite", fecha(c.fechaLimite())));
                if (c.descripcion() != null) l.add(new Linea("Descripción", c.descripcion()));
            }
            case SolicitudContable.CUOTA_CAMBIO -> {
                CuotaCambio c = leer(d, CuotaCambio.class);
                cuotas.findById(c.cuotaId()).ifPresent(q -> {
                    l.add(new Linea("Cuota", q.getConcepto() + " de " + nombreMes(q.getPeriodo()) + " · Torre "
                            + q.getApartamento().getTorre() + " Apto " + q.getApartamento().getApto()));
                    l.add(new Linea("Valor actual", pesos(q.getValor())));
                });
                if (c.valor() != null) l.add(new Linea("Valor nuevo", pesos(c.valor())));
                if (c.fechaLimite() != null) l.add(new Linea("Fecha límite nueva", fecha(c.fechaLimite())));
                if (c.concepto() != null) l.add(new Linea("Concepto nuevo", c.concepto()));
                if (c.descripcion() != null) l.add(new Linea("Descripción", c.descripcion()));
            }
            case SolicitudContable.CUOTA_ANULAR -> {
                CuotaAnular c = leer(d, CuotaAnular.class);
                cuotas.findById(c.cuotaId()).ifPresent(q -> l.add(new Linea("Cuota", q.getConcepto() + " de " + nombreMes(q.getPeriodo())
                        + " — " + pesos(q.getValor()) + " · Torre " + q.getApartamento().getTorre() + " Apto " + q.getApartamento().getApto())));
                l.add(new Linea("Motivo", c.motivo()));
            }
            case SolicitudContable.PAGO_MANUAL -> {
                PagoManual p = leer(d, PagoManual.class);
                cuotas.findById(p.cuotaId()).ifPresent(q -> l.add(new Linea("Cuota", q.getConcepto() + " de " + nombreMes(q.getPeriodo())
                        + " — " + pesos(q.getValor()) + " · Torre " + q.getApartamento().getTorre() + " Apto " + q.getApartamento().getApto())));
                l.add(new Linea("Medio de pago", medio(p.medio())));
                l.add(new Linea("Fecha del pago", fecha(p.fechaPago())));
                if (p.referencia() != null) l.add(new Linea("Referencia o soporte", p.referencia()));
                if (p.nota() != null) l.add(new Linea("Nota", p.nota()));
            }
            default -> { }
        }
        return l;
    }

    // =====================================================================================
    // Ayudas
    // =====================================================================================

    /** Para las solicitudes de antes de P11 (sin detalle) que crea CuotaServiceImpl. */
    public void completarDetalleFacturacion(SolicitudContable s) {
        if (s.getDetalle() != null) return;
        s.setDetalle(escribir(new Facturacion(s.getPeriodo(), Cuota.ADMINISTRACION, FIJO, s.getValor(), null, s.getFechaLimite(),
                null, null, List.of())));
    }

    public void completarDetallePago(SolicitudContable s) {
        if (s.getDetalle() != null || s.getCuota() == null) return;
        s.setApartamento(s.getCuota().getApartamento());
        s.setDetalle(escribir(new PagoManual(s.getCuota().getId(), "EFECTIVO", LocalDate.now(zona), null, null)));
    }

    public void anotarEnvio(SolicitudContable s) {
        anotar(s, SolicitudContableHistorial.ENVIADA, null);
    }

    private void anotar(SolicitudContable s, String accion, String motivo) {
        historial.save(new SolicitudContableHistorial(s.getId(), s.getVersion(), accion,
                SolicitudContableHistorial.APROBADA.equals(accion) || SolicitudContableHistorial.RECHAZADA.equals(accion) ? null : s.getDetalle(),
                motivo, usuario(), LocalDateTime.now(zona)));
    }

    private Solicitud dto(SolicitudContable s) {
        Long aptoId = apartamentoDe(s);
        Apartamento a = s.getApartamento() != null ? s.getApartamento() : s.getCuota() != null ? s.getCuota().getApartamento() : null;
        return new Solicitud(s.getId(), s.getTipo(), s.getEstado(), s.getVersion(), s.isEdicion(), resumen(s), s.getPeriodo(),
                s.getValor(), s.getFechaLimite(), s.getCuota() == null ? null : s.getCuota().getId(), aptoId,
                a == null ? null : a.getTorre(), a == null ? null : a.getApto(), s.getSolicitadaPor(), s.getCreadaEn(),
                s.getActualizadaEn(), s.getRevisadaEn(), s.getRevisadaPor(), s.getMotivoRechazo(), s.getNota());
    }

    private Long apartamentoDe(SolicitudContable s) {
        if (s.getApartamento() != null) return s.getApartamento().getId();
        return s.getCuota() == null ? null : s.getCuota().getApartamento().getId();
    }

    private String apartamentoTexto(SolicitudContable s) {
        Apartamento a = s.getApartamento() != null ? s.getApartamento() : s.getCuota() != null ? s.getCuota().getApartamento() : null;
        return a == null ? "" : " · Torre " + a.getTorre() + " Apto " + a.getApto();
    }

    private String cuotaTexto(SolicitudContable s) {
        Cuota c = s.getCuota();
        return c == null ? nombreMes(s.getPeriodo()) : c.getConcepto() + " de " + nombreMes(c.getPeriodo());
    }

    private SolicitudContable buscar(Long id) {
        return solicitudes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada: " + id));
    }

    private Apartamento apartamento(Long id) {
        return apartamentos.findById(id == null ? -1 : id).orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado"));
    }

    private boolean esContador() {
        return acceso.tokenActual().map(t -> TokenService.ROL_CONTADOR.equals(t.getClaimAsString(TokenService.CLAIM_ROL))).orElse(false);
    }

    private String usuario() {
        return acceso.tokenActual().map(t -> {
            String u = t.getClaimAsString("usuario");
            return u != null ? u : t.getSubject();
        }).orElse("sistema");
    }

    private JsonNode leer(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return json.readTree(texto);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T leer(JsonNode d, Class<T> tipo) {
        try {
            return json.convertValue(d, tipo);
        } catch (IllegalArgumentException e) {
            throw invalido("El detalle no es válido: revisa los valores y las fechas");
        }
    }

    private String escribir(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String periodo(String p) {
        if (p == null || !PERIODO.matcher(p.trim()).matches()) throw invalido("El mes debe tener la forma 2026-11");
        return p.trim();
    }

    private static String concepto(String c) {
        String t = c == null || c.isBlank() ? Cuota.ADMINISTRACION : c.trim();
        if (t.length() > 80) throw invalido("El concepto puede tener máximo 80 caracteres");
        return t;
    }

    private static BigDecimal dinero(BigDecimal v, String que) {
        if (v == null || v.signum() <= 0) throw invalido(que + " debe ser mayor que cero");
        if (v.stripTrailingZeros().scale() > 2) throw invalido(que + " admite máximo dos decimales");
        if (v.compareTo(new BigDecimal("9999999999")) > 0) throw invalido(que + " es demasiado grande");
        return v.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String texto(String s, int max, String que) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.length() > max) throw invalido(que + " puede tener máximo " + max + " caracteres");
        return t;
    }

    private static String nota(String s) {
        return texto(s, 500, "La nota");
    }

    static String nombreMes(String periodo) {
        if (periodo == null) return "";
        YearMonth m = YearMonth.parse(periodo);
        return m.getMonth().getDisplayName(TextStyle.FULL, ESPANOL) + " " + m.getYear();
    }

    private static String pesos(BigDecimal v) {
        return v == null ? "—" : "$" + NumberFormat.getIntegerInstance(ESPANOL).format(v);
    }

    private static String fecha(LocalDate d) {
        return d == null ? "—" : d.getDayOfMonth() + " de " + d.getMonth().getDisplayName(TextStyle.FULL, ESPANOL) + " de " + d.getYear();
    }

    private static String medio(String m) {
        return switch (m == null ? "" : m) {
            case "TRANSFERENCIA" -> "Transferencia";
            case "CONSIGNACION" -> "Consignación";
            case "CHEQUE" -> "Cheque";
            case "OTRO" -> "Otro";
            default -> "Efectivo";
        };
    }

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
