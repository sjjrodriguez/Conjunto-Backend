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

    @Override
    public List<NotificacionDTO> listarRecientes() {
        return repo.findTop50ByOrderByFechaDescIdDesc().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public long contarNoLeidas() {
        return repo.countByLeidaFalse();
    }

    @Override
    @Transactional
    public void marcarTodasLeidas() {
        repo.marcarTodasLeidas();
    }

    @Override
    public void eliminarTodas() {
        repo.deleteAllInBatch();
    }

    @Override
    @Transactional
    public void marcarLeida(Long id) {
        if (repo.marcarLeida(id) == 0) throw new RecursoNoEncontradoException("Notificación no encontrada");
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        if (!repo.existsById(id)) throw new RecursoNoEncontradoException("Notificación no encontrada");
        repo.deleteById(id);
    }

    @Override
    public void avisarNuevaReserva(Reserva r) {
        crear(TIPO_RESERVA, "Nueva reserva — " + r.getZonaComun().getNombre()
                + " el " + r.getFecha().format(FECHA) + " a las " + r.getHora().format(HORA)
                + " · " + torreApto(r.getApartamento()), r.getId(), r.getApartamento().getId());
    }

    @Override
    public void avisarNuevoVehiculo(Vehiculo v) {
        String detalle = Stream.of(v.getTipo(), v.getMarca(), v.getColor())
                .filter(Objects::nonNull).filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));
        crear(TIPO_VEHICULO, "Nuevo vehículo registrado — " + v.getPlaca()
                + (detalle.isEmpty() ? "" : " (" + detalle + ")")
                + " · " + torreApto(v.getApartamento()), v.getId(), v.getApartamento().getId());
    }

    // Solo interesa lo que hacen los propietarios: el admin no se avisa a sí mismo.
    // Al guardarla también sale el aviso a los celulares del admin (AvisosPush).
    private void crear(String tipo, String mensaje, Long referenciaId, Long apartamentoId) {
        if (acceso.esAdminActual()) return;
        Notificacion n = repo.save(new Notificacion(null, tipo, mensaje, LocalDateTime.now(zonaHoraria), false,
                referenciaId, apartamentoId));
        eventos.publishEvent(new AvisosPush.NotificacionCreada(n));
    }

    private static String torreApto(Apartamento a) {
        return "Torre " + a.getTorre() + " Apto " + a.getApto();
    }
}
