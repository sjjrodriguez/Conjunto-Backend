package com.residencial.serviceimpl;

import com.residencial.dto.ReservaDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Catalogo;
import com.residencial.model.Reserva;
import com.residencial.model.ZonaComun;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ReservaRepository;
import com.residencial.repository.ZonaComunRepository;
import com.residencial.security.AccesoService;
import com.residencial.service.NotificacionService;
import com.residencial.service.ReservaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReservaServiceImpl implements ReservaService {

    private static final List<String> ESTADOS_QUE_OCUPAN = List.of("PENDIENTE", "APROBADA");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReservaRepository repo;
    private final ApartamentoRepository aptoRepo;
    private final ZonaComunRepository zonaRepo;
    private final AccesoService acceso;
    private final NotificacionService notificaciones;
    // Cada reserva ocupa la zona este tiempo a partir de su hora de inicio
    private final Duration duracion;
    // Hora de referencia para "fecha pasada": la del conjunto, no la del servidor (Render usa UTC)
    private final ZoneId zonaHoraria;

    public ReservaServiceImpl(ReservaRepository repo,
                              ApartamentoRepository aptoRepo,
                              ZonaComunRepository zonaRepo,
                              AccesoService acceso,
                              NotificacionService notificaciones,
                              @Value("${app.reservas.duracion-horas:2}") long duracionHoras,
                              @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
        if (duracionHoras < 1 || duracionHoras > 24) {
            throw new IllegalStateException("app.reservas.duracion-horas debe estar entre 1 y 24");
        }
        this.repo = repo;
        this.aptoRepo = aptoRepo;
        this.zonaRepo = zonaRepo;
        this.acceso = acceso;
        this.notificaciones = notificaciones;
        this.duracion = Duration.ofHours(duracionHoras);
        this.zonaHoraria = ZoneId.of(zonaHoraria);
    }

    private ReservaDTO toDTO(Reserva r) {
        return new ReservaDTO(r.getId(), r.getFecha(), r.getHora(),
                r.getEstado(), r.getZonaComun().getId(), r.getApartamento().getId());
    }

    @Override
    public List<ReservaDTO> listarTodas() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<ReservaDTO> listarPorApartamento(Long apartamentoId) {
        acceso.verificarApartamento(apartamentoId);
        return repo.findByApartamentoId(apartamentoId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ReservaDTO guardar(ReservaDTO dto) {
        acceso.verificarApartamento(dto.getApartamentoId());
        if (dto.getApartamentoId() == null || dto.getZonaComunId() == null
                || dto.getFecha() == null || dto.getHora() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La zona, el apartamento, la fecha y la hora son obligatorios");
        }
        Apartamento apto = aptoRepo.findById(dto.getApartamentoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado"));
        // Bloquea la zona hasta el final de la transacción (ver ZonaComunRepository)
        ZonaComun zona = zonaRepo.findByIdParaReservar(dto.getZonaComunId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Zona común no encontrada"));

        if (!Boolean.TRUE.equals(zona.getEsReservable())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La zona " + zona.getNombre() + " no se puede reservar");
        }
        if (LocalDateTime.of(dto.getFecha(), dto.getHora()).isBefore(LocalDateTime.now(zonaHoraria))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede reservar en una fecha u hora pasada");
        }
        buscarChoque(zona.getId(), dto.getFecha(), dto.getHora(), ESTADOS_QUE_OCUPAN, null)
                .ifPresent(this::rechazarPorChoque);

        Reserva r = repo.save(new Reserva(null, dto.getFecha(), dto.getHora(), "PENDIENTE", zona, apto));
        notificaciones.avisarNuevaReserva(r);
        return toDTO(r);
    }

    @Override
    @Transactional
    public ReservaDTO cambiarEstado(Long id, String nuevoEstado) {
        String estado = Catalogo.ESTADO_RESERVA.normalizar(nuevoEstado);
        return repo.findById(id).map(r -> {
            if ("APROBADA".equals(estado) && !"APROBADA".equals(r.getEstado())) {
                // Por si hay datos antiguos: no aprobar dos reservas que se cruzan
                zonaRepo.findByIdParaReservar(r.getZonaComun().getId());
                buscarChoque(r.getZonaComun().getId(), r.getFecha(), r.getHora(), List.of("APROBADA"), r.getId())
                        .ifPresent(this::rechazarPorChoque);
            }
            r.setEstado(estado);
            return toDTO(repo.save(r));
        }).orElse(null);
    }

    @Override
    public void eliminar(Long id) {
        repo.deleteById(id);
    }

    /** Primera reserva de la zona cuyo bloque [hora, hora + duración) se cruza con el pedido. */
    private Optional<Reserva> buscarChoque(Long zonaId, LocalDate fecha, LocalTime hora,
                                           List<String> estados, Long excluirId) {
        LocalDateTime inicio = LocalDateTime.of(fecha, hora);
        LocalDateTime fin = inicio.plus(duracion);
        return repo.findByZonaComunIdAndFechaBetweenAndEstadoIn(zonaId, fecha.minusDays(1), fecha.plusDays(1), estados)
                .stream()
                .filter(r -> !Objects.equals(r.getId(), excluirId))
                .filter(r -> {
                    LocalDateTime otroInicio = LocalDateTime.of(r.getFecha(), r.getHora());
                    return otroInicio.isBefore(fin) && inicio.isBefore(otroInicio.plus(duracion));
                })
                .findFirst();
    }

    private void rechazarPorChoque(Reserva otra) {
        LocalDateTime inicio = LocalDateTime.of(otra.getFecha(), otra.getHora());
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La zona ya está reservada el " + inicio.format(FECHA) + " de " + inicio.format(HORA)
                        + " a " + inicio.plus(duracion).format(HORA)
                        + ". Cada reserva ocupa " + duracion.toHours() + " horas.");
    }
}
