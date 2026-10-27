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
    public static final String TIPO_SOLICITUD_ARRENDATARIO = "SOLICITUD_ARRENDATARIO";
    public static final String TIPO_COMPROBANTE = "COMPROBANTE";
    public static final String TIPO_CUOTA_PAGADA = "CUOTA_PAGADA";
    public static final String TIPO_CUOTA_RECHAZADA = "CUOTA_RECHAZADA";
    public static final String TIPO_OBRA = "OBRA";
    public static final String TIPO_OBRA_APROBADA = "OBRA_APROBADA";
    public static final String TIPO_OBRA_RECHAZADA = "OBRA_RECHAZADA";
    public static final String TIPO_PERSONAL = "PERSONAL_OBRA";
    public static final String TIPO_PERSONAL_APROBADO = "PERSONAL_OBRA_APROBADO";
    public static final String TIPO_PERSONAL_RECHAZADO = "PERSONAL_OBRA_RECHAZADO";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificacionRepository repo;
    private final AccesoService acceso;
    private final ApplicationEventPublisher eventos;
    private final ZoneId zonaHoraria;

    public NotificacionServiceImpl(NotificacionRepository repo,
                                   AccesoService acceso,
                                   ApplicationEventPublisher eventos,
                                   @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
        this.repo = repo;
        this.acceso = acceso;
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

    @Override
    public List<NotificacionDTO> listarRecientes() {
        Long apto = apartamentoDeLaSesion();
        List<Notificacion> lista = apto == null
                ? repo.findTop50ByDestinatarioApartamentoIdIsNullOrderByFechaDescIdDesc()
                : repo.findTop50ByDestinatarioApartamentoIdOrderByFechaDescIdDesc(apto);
        return lista.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public long contarNoLeidas() {
        Long apto = apartamentoDeLaSesion();
        return apto == null ? repo.countByLeidaFalseAndDestinatarioApartamentoIdIsNull()
                : repo.countByLeidaFalseAndDestinatarioApartamentoId(apto);
    }

    @Override
    @Transactional
    public void marcarTodasLeidas() {
        Long apto = apartamentoDeLaSesion();
        if (apto == null) repo.marcarTodasLeidasAdmin(); else repo.marcarTodasLeidasApartamento(apto);
    }

    @Override
    @Transactional
    public void eliminarTodas() {
        Long apto = apartamentoDeLaSesion();
        if (apto == null) repo.eliminarTodasAdmin(); else repo.eliminarTodasApartamento(apto);
    }

    // Si no existe o es de otro, la misma respuesta: no se revela que existe
    @Override
    @Transactional
    public void marcarLeida(Long id) {
        Long apto = apartamentoDeLaSesion();
        int cambiadas = apto == null ? repo.marcarLeidaAdmin(id) : repo.marcarLeidaApartamento(id, apto);
        if (cambiadas == 0) throw new RecursoNoEncontradoException("Notificación no encontrada");
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Long apto = apartamentoDeLaSesion();
        int borradas = apto == null ? repo.eliminarAdmin(id) : repo.eliminarApartamento(id, apto);
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
                (aprobada ? "Reserva aprobada — " : "Reserva rechazada — ") + cuando(r),
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

    // Al guardarla también sale el aviso a los celulares de quien la recibe (AvisosPush)
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
