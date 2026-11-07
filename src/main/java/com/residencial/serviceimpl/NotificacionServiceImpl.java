package com.residencial.serviceimpl;

import com.residencial.dto.NotificacionDTO;
import com.residencial.model.Apartamento;
import com.residencial.model.Cuota;
import com.residencial.model.Notificacion;
import com.residencial.model.Reserva;
import com.residencial.model.Residente;
import com.residencial.model.Vehiculo;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.push.AvisosPush;
import com.residencial.repository.NotificacionRepository;
import com.residencial.security.AccesoService;
import com.residencial.service.NotificacionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class NotificacionServiceImpl implements NotificacionService {

    public static final String TIPO_RESERVA = "RESERVA";
    public static final String TIPO_VEHICULO = "VEHICULO";
    public static final String TIPO_VEHICULO_APROBADO = "VEHICULO_APROBADO";
    public static final String TIPO_VEHICULO_RECHAZADO = "VEHICULO_RECHAZADO";
    public static final String TIPO_RESERVA_APROBADA = "RESERVA_APROBADA";
    public static final String TIPO_RESERVA_RECHAZADA = "RESERVA_RECHAZADA";
    public static final String TIPO_RESIDENTE = "RESIDENTE";
    public static final String TIPO_RESIDENTE_APROBADO = "RESIDENTE_APROBADO";
    public static final String TIPO_RESIDENTE_RECHAZADO = "RESIDENTE_RECHAZADO";
    public static final String TIPO_CUOTA = "CUOTA";
    public static final String TIPO_REGISTRO = "REGISTRO";
    public static final String TIPO_PUBLICACION = "PUBLICACION";
    public static final String TIPO_VISITA = "VISITA_PORTERIA";
    public static final String TIPO_VISITA_FRECUENTE = "VISITA_FRECUENTE";
    // P9 bloque 3
    public static final String TIPO_CORRESPONDENCIA = "CORRESPONDENCIA";
    public static final String TIPO_RECIBO = "RECIBO";
    public static final String TIPO_CORRESPONDENCIA_ENTREGADA = "CORRESPONDENCIA_ENTREGADA";
    public static final String TIPO_SOLICITUD_ARRENDATARIO = "SOLICITUD_ARRENDATARIO";
    public static final String TIPO_COMPROBANTE = "COMPROBANTE";
    public static final String TIPO_CUOTA_PAGADA = "CUOTA_PAGADA";
    public static final String TIPO_CUOTA_RECHAZADA = "CUOTA_RECHAZADA";
    public static final String TIPO_SOLICITUD_CONTABLE = "SOLICITUD_CONTABLE";
    public static final String TIPO_OBRA = "OBRA";
    public static final String TIPO_OBRA_APROBADA = "OBRA_APROBADA";
    public static final String TIPO_OBRA_RECHAZADA = "OBRA_RECHAZADA";
    public static final String TIPO_PERSONAL = "PERSONAL_OBRA";
    public static final String TIPO_PERSONAL_APROBADO = "PERSONAL_OBRA_APROBADO";
    public static final String TIPO_PERSONAL_RECHAZADO = "PERSONAL_OBRA_RECHAZADO";
    // P11: las respuestas del administrador a lo que mandó el contador
    public static final String TIPO_SOLICITUD_APROBADA = "SOLICITUD_CONTABLE_APROBADA";
    public static final String TIPO_SOLICITUD_RECHAZADA = "SOLICITUD_CONTABLE_RECHAZADA";
    /** P11: de las notificaciones de la administración, las únicas que ve (y le llegan al celular) el contador. */
    public static final java.util.Set<String> TIPOS_CONTADOR = java.util.Set.of(
            TIPO_COMPROBANTE, TIPO_SOLICITUD_APROBADA, TIPO_SOLICITUD_RECHAZADA);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificacionRepository repo;
    private final AccesoService acceso;
    private final ApplicationEventPublisher eventos;
    private final ZoneId zonaHoraria;
    private final com.residencial.security.EstadoApartamento estado;

    public NotificacionServiceImpl(NotificacionRepository repo,
                                   AccesoService acceso,
                                   ApplicationEventPublisher eventos,
                                   com.residencial.security.EstadoApartamento estado,
                                   @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
        this.repo = repo;
        this.acceso = acceso;
        this.estado = estado;
        this.eventos = eventos;
        this.zonaHoraria = ZoneId.of(zonaHoraria);
    }

    private NotificacionDTO toDTO(Notificacion n) {
        return new NotificacionDTO(n.getId(), n.getTipo(), n.getMensaje(), n.getFecha(), n.getLeida(),
                n.getReferenciaId(), n.getApartamentoId());
    }

    /** El apartamento de la sesión si es un residente; null para el administrador. */
    private Long apartamentoDeLaSesion() {
        return acceso.apartamentoActual().orElse(null);
    }

    /** La portería no tiene avisos propios y no debe ver los de la administración. */
    private boolean esPorteria() {
        return acceso.tokenActual().map(t -> com.residencial.security.TokenService.ROL_PORTERIA
                .equals(t.getClaimAsString(com.residencial.security.TokenService.CLAIM_ROL))).orElse(false);
    }

    /** P11: el contador no ve los avisos que son solo del administrador. */
    private boolean esContador() {
        return acceso.tokenActual().map(t -> com.residencial.security.TokenService.ROL_CONTADOR
                .equals(t.getClaimAsString(com.residencial.security.TokenService.CLAIM_ROL))).orElse(false);
    }

    @Override
    public List<NotificacionDTO> listarRecientes() {
        if (esPorteria()) return List.of();
        if (esContador()) return repo.findTop50ByDestinatarioApartamentoIdIsNullAndTipoInOrderByFechaDescIdDesc(TIPOS_CONTADOR)
                .stream().map(this::toDTO).collect(Collectors.toList());
        Long apto = apartamentoDeLaSesion();
        List<Notificacion> lista = apto == null
                ? repo.findTop50ByDestinatarioApartamentoIdIsNullOrderByFechaDescIdDesc()
                : estado.sinAvisosDelHogar(apto)
                ? repo.findTop50ByDestinatarioApartamentoIdAndTipoNotInOrderByFechaDescIdDesc(apto, com.residencial.security.EstadoApartamento.AVISOS_DEL_HOGAR)
                : repo.findTop50ByDestinatarioApartamentoIdOrderByFechaDescIdDesc(apto);
        return lista.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public long contarNoLeidas() {
        if (esPorteria()) return 0;
        if (esContador()) return repo.countByLeidaFalseAndDestinatarioApartamentoIdIsNullAndTipoIn(TIPOS_CONTADOR);
        Long apto = apartamentoDeLaSesion();
        if (apto == null) return repo.countByLeidaFalseAndDestinatarioApartamentoIdIsNull();
        return estado.sinAvisosDelHogar(apto)
                ? repo.countByLeidaFalseAndDestinatarioApartamentoIdAndTipoNotIn(apto, com.residencial.security.EstadoApartamento.AVISOS_DEL_HOGAR)
                : repo.countByLeidaFalseAndDestinatarioApartamentoId(apto);
    }

    @Override
    @Transactional
    public void marcarTodasLeidas() {
        if (esPorteria()) return;
        if (esContador()) { repo.marcarTodasLeidasContador(TIPOS_CONTADOR); return; }
        Long apto = apartamentoDeLaSesion();
        if (apto == null) repo.marcarTodasLeidasAdmin(); else repo.marcarTodasLeidasApartamento(apto);
    }

    @Override
    @Transactional
    public void eliminarTodas() {
        if (esPorteria()) return;
        if (esContador()) { repo.eliminarTodasContador(TIPOS_CONTADOR); return; }
        Long apto = apartamentoDeLaSesion();
        if (apto == null) repo.eliminarTodasAdmin(); else repo.eliminarTodasApartamento(apto);
    }

    // Si no existe o es de otro, la misma respuesta: no se revela que existe
    @Override
    @Transactional
    public void marcarLeida(Long id) {
        if (esPorteria()) throw new RecursoNoEncontradoException("Notificación no encontrada");
        Long apto = apartamentoDeLaSesion();
        int cambiadas = esContador() ? repo.marcarLeidaContador(id, TIPOS_CONTADOR) : apto == null ? repo.marcarLeidaAdmin(id) : repo.marcarLeidaApartamento(id, apto);
        if (cambiadas == 0) throw new RecursoNoEncontradoException("Notificación no encontrada");
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        if (esPorteria()) throw new RecursoNoEncontradoException("Notificación no encontrada");
        Long apto = apartamentoDeLaSesion();
        int borradas = esContador() ? repo.eliminarContador(id, TIPOS_CONTADOR) : apto == null ? repo.eliminarAdmin(id) : repo.eliminarApartamento(id, apto);
        if (borradas == 0) throw new RecursoNoEncontradoException("Notificación no encontrada");
    }

    @Override
    public void avisarNuevaReserva(Reserva r) {
        // Solo interesa lo que hacen los residentes: el admin no se avisa a sí mismo
        if (acceso.esAdminActual()) return;
        crear(TIPO_RESERVA, "Nueva reserva — " + cuando(r) + " · " + torreApto(r.getApartamento()),
                r.getId(), r.getApartamento().getId(), null);
    }

    @Override
    public void avisarNuevoVehiculo(Vehiculo v) {
        if (acceso.esAdminActual()) return;
        crear(TIPO_VEHICULO, "Nuevo vehículo registrado — " + descripcion(v) + " · " + torreApto(v.getApartamento()),
                v.getId(), v.getApartamento().getId(), null);
    }

    @Override
    public void avisarVehiculoRevisado(Vehiculo v, String parqueadero) {
        boolean aprobado = Vehiculo.APROBADO.equals(v.getEstado());
        String motivo = v.getMotivoRechazo() == null ? "" : ". Motivo: " + v.getMotivoRechazo();
        String extra = aprobado ? (parqueadero == null ? "" : " · Parqueadero " + parqueadero) : motivo;
        crear(aprobado ? TIPO_VEHICULO_APROBADO : TIPO_VEHICULO_RECHAZADO,
                (aprobado ? "Vehículo aprobado — " : "Vehículo rechazado — ") + descripcion(v) + extra,
                v.getId(), v.getApartamento().getId(), v.getApartamento().getId());
    }

    @Override
    public void avisarReservaRevisada(Reserva r) {
        boolean aprobada = "APROBADA".equals(r.getEstado());
        crear(aprobada ? TIPO_RESERVA_APROBADA : TIPO_RESERVA_RECHAZADA,
                (aprobada ? "Reserva aprobada — " : "Reserva rechazada — ") + cuando(r)
                        + (r.getMotivoRespuesta() == null ? "" : ". Motivo: " + r.getMotivoRespuesta()),
                r.getId(), r.getApartamento().getId(), r.getApartamento().getId());
    }

    @Override
    public void avisarNuevoResidente(Residente r) {
        crear(TIPO_RESIDENTE, "Nuevo arrendatario por aprobar — " + r.getNombreCompleto() + " · " + torreApto(r.getApartamento()),
                r.getId(), r.getApartamento().getId(), null);
    }

    @Override
    public void avisarResidenteRevisado(Residente r, String motivo) {
        boolean aprobado = Residente.APROBADO.equals(r.getEstado());
        crear(aprobado ? TIPO_RESIDENTE_APROBADO : TIPO_RESIDENTE_RECHAZADO,
                (aprobado ? "Arrendatario aprobado — " + r.getNombreCompleto() + " ya puede entrar con su documento"
                        : "Arrendatario rechazado — " + r.getNombreCompleto() + (motivo == null ? "" : ". Motivo: " + motivo)),
                r.getId(), r.getApartamento().getId(), r.getApartamento().getId());
    }

    @Override
    public void avisarCuotaNueva(Cuota c, String mes) {
        crear(TIPO_CUOTA, "Cuota de administración de " + mes + " — " + pesos(c) + ", vence el "
                        + c.getFechaLimite().format(FECHA),
                c.getId(), c.getApartamento().getId(), c.getApartamento().getId());
    }

    @Override
    public void avisarComprobante(Cuota c, String mes) {
        crear(TIPO_COMPROBANTE, "Comprobante por revisar — cuota de " + mes + " · " + torreApto(c.getApartamento()),
                c.getId(), c.getApartamento().getId(), null);
    }

    @Override
    public void avisarCuotaRevisada(Cuota c, String mes) {
        boolean pagada = c.pagada();
        String motivo = c.getMotivoRechazo() == null ? "" : ". Motivo: " + c.getMotivoRechazo();
        crear(pagada ? TIPO_CUOTA_PAGADA : TIPO_CUOTA_RECHAZADA,
                pagada ? "Pago confirmado — cuota de " + mes + " (" + pesos(c) + ")"
                        : "Comprobante rechazado — cuota de " + mes + motivo,
                c.getId(), c.getApartamento().getId(), c.getApartamento().getId());
    }

    // "$350.000"
    private static String pesos(Cuota c) {
        return "$" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")).format(c.getValor());
    }

    @Override
    public void avisarNuevoRegistro(Residente r) {
        String quien = r.getNombreCompleto() + " (" + (r.esArrendatario() ? "arrendatario" : "propietario") + ")";
        crear(TIPO_REGISTRO, "Nuevo registro por aprobar — " + quien + " · " + torreApto(r.getApartamento()),
                r.getId(), r.getApartamento().getId(), null);
        if (r.esArrendatario()) {
            crear(TIPO_SOLICITUD_ARRENDATARIO, "Solicitud de arrendatario — " + r.getNombreCompleto()
                            + " quiere registrarse como arrendatario de tu apartamento",
                    r.getId(), r.getApartamento().getId(), r.getApartamento().getId());
        }
    }

    @Override
    public void avisarRevisionDelPropietario(Residente r, String motivo) {
        boolean aprobado = Boolean.TRUE.equals(r.getAprobadoPropietario());
        crear(TIPO_REGISTRO, (aprobado ? "El propietario aprobó al arrendatario " : "El propietario rechazó al arrendatario ")
                        + r.getNombreCompleto() + " · " + torreApto(r.getApartamento())
                        + (aprobado || motivo == null ? "" : ". Motivo: " + motivo),
                r.getId(), r.getApartamento().getId(), null);
    }

    @Override
    public void avisarPublicacion(com.residencial.model.Publicacion p, java.util.List<Long> apartamentos) {
        String texto = p.getTexto().isBlank() ? "Nueva publicación con adjuntos" : p.getTexto().replaceAll("\\s+", " ");
        String resumen = "Nueva publicación de la administración — " + (texto.length() > 90 ? texto.substring(0, 90) + "…" : texto);
        for (Long apto : apartamentos) crear(TIPO_PUBLICACION, resumen, p.getId(), apto, apto);
    }

    @Override
    public void avisarVisitaFrecuente(com.residencial.model.VisitaPorteria v) {
        String vehiculo = !com.residencial.model.VisitaPorteria.VEHICULO.equals(v.getTipoIngreso()) ? ""
                : " en vehículo " + v.getPlaca();
        crear(TIPO_VISITA_FRECUENTE, "Entró " + v.getVisitanteNombre() + vehiculo + ", visitante frecuente de tu apartamento",
                v.getId(), v.getApartamento().getId(), v.getApartamento().getId());
    }

    @Override
    public void avisarVisita(com.residencial.model.VisitaPorteria v) {
        // P8 bloque 4: si llega en vehículo, con la placa y el parqueadero de invitados
        String vehiculo = !com.residencial.model.VisitaPorteria.VEHICULO.equals(v.getTipoIngreso()) ? ""
                : " en vehículo " + v.getPlaca() + (v.getParqueadero() == null ? "" : " (parqueadero " + v.getParqueadero().getNumero() + ")");
        crear(TIPO_VISITA, "Visita en portería: " + v.getVisitanteNombre() + vehiculo + ". Autorízala o recházala en " + com.residencial.model.VisitaPorteria.MINUTOS_VIGENCIA + " minutos",
                v.getId(), v.getApartamento().getId(), v.getApartamento().getId());
    }

    @Override
    public void avisarCorrespondencia(com.residencial.model.Correspondencia c) {
        boolean recibo = com.residencial.model.Correspondencia.RECIBO.equals(c.getTipo());
        String nota = c.getNota() == null ? "" : " (" + c.getNota() + ")";
        crear(recibo ? TIPO_RECIBO : TIPO_CORRESPONDENCIA,
                "Llegó " + queLlego(c) + nota + " a la portería. Recógelo cuando puedas",
                c.getId(), c.getApartamento().getId(), c.getApartamento().getId());
    }

    @Override
    public void avisarCorrespondenciaEntregada(com.residencial.model.Correspondencia c) {
        String que = queLlego(c);
        crear(TIPO_CORRESPONDENCIA_ENTREGADA,
                Character.toUpperCase(que.charAt(0)) + que.substring(1) + " se entregó a " + c.getEntregadaA()
                        + " a las " + c.getEntregadaEn().format(HORA),
                c.getId(), c.getApartamento().getId(), c.getApartamento().getId());
    }

    // "un paquete de Servientrega", "el recibo de Enel", "una carta"
    private static String queLlego(com.residencial.model.Correspondencia c) {
        String de = c.getRemitente() == null ? "" : " de " + c.getRemitente();
        return switch (c.getTipo()) {
            case "RECIBO" -> "el recibo" + de;
            case "CARTA" -> "una carta" + de;
            case "DOCUMENTO" -> "un documento" + de;
            default -> "un paquete" + de;
        };
    }

    // Al guardarla también sale el aviso a los celulares de quien la recibe (AvisosPush)
    @Override
    public void avisarSolicitudContable(com.residencial.model.SolicitudContable s, String resumen) {
        Long apto = s.getCuota() == null ? null : s.getCuota().getApartamento().getId();
        crear(TIPO_SOLICITUD_CONTABLE, "Por aprobar — " + resumen, s.getId(), apto, null);
    }

    @Override
    public void avisarObraNueva(com.residencial.model.Obra o) {
        crear(TIPO_OBRA, "Obra por aprobar — " + o.getDescripcionAdecuacion() + " · " + torreApto(o.getApartamento()),
                o.getId(), o.apartamentoId(), null);
    }

    @Override
    public void avisarObraRevisada(com.residencial.model.Obra o) {
        boolean aprobada = com.residencial.model.Obra.APROBADA.equals(o.getAprobacion());
        String motivo = aprobada || o.getMotivoRechazo() == null ? "" : ". Motivo: " + o.getMotivoRechazo();
        crear(aprobada ? TIPO_OBRA_APROBADA : TIPO_OBRA_RECHAZADA,
                (aprobada ? "Obra aprobada — " : "Obra rechazada — ") + o.getDescripcionAdecuacion() + motivo,
                o.getId(), o.apartamentoId(), o.apartamentoId());
    }

    @Override
    public void avisarPersonalNuevo(com.residencial.model.PersonalObra p) {
        com.residencial.model.Obra o = p.getObra();
        crear(TIPO_PERSONAL, "Trabajador por aprobar — " + p.getNombreTrabajador() + " · " + torreApto(o.getApartamento()),
                p.getId(), o.apartamentoId(), null);
    }

    @Override
    public void avisarPersonalRevisado(com.residencial.model.PersonalObra p) {
        boolean aprobado = com.residencial.model.PersonalObra.APROBADO.equals(p.getAprobacion());
        String motivo = aprobado || p.getMotivoRechazo() == null ? "" : ". Motivo: " + p.getMotivoRechazo();
        Long apto = p.getObra().apartamentoId();
        crear(aprobado ? TIPO_PERSONAL_APROBADO : TIPO_PERSONAL_RECHAZADO,
                (aprobado ? "Trabajador aprobado — " : "Trabajador rechazado — ") + p.getNombreTrabajador() + motivo,
                p.getId(), apto, apto);
    }

    private void crear(String tipo, String mensaje, Long referenciaId, Long apartamentoId, Long destinatario) {
        Notificacion n = repo.save(new Notificacion(null, tipo, mensaje, LocalDateTime.now(zonaHoraria), false,
                referenciaId, apartamentoId, destinatario));
        eventos.publishEvent(new AvisosPush.NotificacionCreada(n));
    }

    // "Zona BBQ el 25/10/2026 a las 14:00"
    private static String cuando(Reserva r) {
        return r.getZonaComun().getNombre() + " el " + r.getFecha().format(FECHA) + " a las " + r.getHora().format(HORA);
    }

    // "ABC123 (Carro Mazda Rojo)" o, sin placa, "Bicicleta Trek Negra"
    private static String descripcion(Vehiculo v) {
        String detalle = Stream.of(v.getTipo(), v.getMarca(), v.getColor())
                .filter(Objects::nonNull).filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));
        if (v.getPlaca() == null || v.getPlaca().isBlank()) return detalle;
        return v.getPlaca() + (detalle.isEmpty() ? "" : " (" + detalle + ")");
    }

    private static String torreApto(Apartamento a) {
        if (a == null) return "Conjunto";
        return "Torre " + a.getTorre() + " Apto " + a.getApto();
    }
}
