package com.residencial.serviceimpl;

import com.residencial.dto.NotificacionDTO;
import com.residencial.model.Apartamento;
import com.residencial.model.Notificacion;
import com.residencial.model.Reserva;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    public void avisarVehiculoRevisado(Vehiculo v) {
        boolean aprobado = Vehiculo.APROBADO.equals(v.getEstado());
        String motivo = v.getMotivoRechazo() == null ? "" : ". Motivo: " + v.getMotivoRechazo();
        crear(aprobado ? TIPO_VEHICULO_APROBADO : TIPO_VEHICULO_RECHAZADO,
                (aprobado ? "Vehículo aprobado — " : "Vehículo rechazado — ") + descripcion(v) + (aprobado ? "" : motivo),
                v.getId(), v.getApartamento().getId(), v.getApartamento().getId());
    }

    @Override
    public void avisarReservaRevisada(Reserva r) {
        boolean aprobada = "APROBADA".equals(r.getEstado());
        crear(aprobada ? TIPO_RESERVA_APROBADA : TIPO_RESERVA_RECHAZADA,
                (aprobada ? "Reserva aprobada — " : "Reserva rechazada — ") + cuando(r),
                r.getId(), r.getApartamento().getId(), r.getApartamento().getId());
    }

    // Al guardarla también sale el aviso a los celulares de quien la recibe (AvisosPush)
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
        return "Torre " + a.getTorre() + " Apto " + a.getApto();
    }
}
