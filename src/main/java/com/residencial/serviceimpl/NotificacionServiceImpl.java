package com.residencial.serviceimpl;

import com.residencial.dto.NotificacionDTO;
import com.residencial.model.Apartamento;
import com.residencial.model.Notificacion;
import com.residencial.model.Reserva;
import com.residencial.model.Vehiculo;
import com.residencial.repository.NotificacionRepository;
import com.residencial.security.AccesoService;
import com.residencial.service.NotificacionService;
import org.springframework.beans.factory.annotation.Value;
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
    private final ZoneId zonaHoraria;

    public NotificacionServiceImpl(NotificacionRepository repo,
                                   AccesoService acceso,
                                   @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
        this.repo = repo;
        this.acceso = acceso;
        this.zonaHoraria = ZoneId.of(zonaHoraria);
    }

    private NotificacionDTO toDTO(Notificacion n) {
        return new NotificacionDTO(n.getId(), n.getTipo(), n.getMensaje(), n.getFecha(), n.getLeida());
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
    public void avisarNuevaReserva(Reserva r) {
        crear(TIPO_RESERVA, "Nueva reserva — " + r.getZonaComun().getNombre()
                + " el " + r.getFecha().format(FECHA) + " a las " + r.getHora().format(HORA)
                + " · " + torreApto(r.getApartamento()));
    }

    @Override
    public void avisarNuevoVehiculo(Vehiculo v) {
        String detalle = Stream.of(v.getTipo(), v.getMarca(), v.getColor())
                .filter(Objects::nonNull).filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));
        crear(TIPO_VEHICULO, "Nuevo vehículo registrado — " + v.getPlaca()
                + (detalle.isEmpty() ? "" : " (" + detalle + ")")
                + " · " + torreApto(v.getApartamento()));
    }

    // Solo interesa lo que hacen los propietarios: el admin no se avisa a sí mismo
    private void crear(String tipo, String mensaje) {
        if (acceso.esAdminActual()) return;
        repo.save(new Notificacion(null, tipo, mensaje, LocalDateTime.now(zonaHoraria), false));
    }

    private static String torreApto(Apartamento a) {
        return "Torre " + a.getTorre() + " Apto " + a.getApto();
    }
}
