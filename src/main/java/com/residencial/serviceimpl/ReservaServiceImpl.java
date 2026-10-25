package com.residencial.serviceimpl;

import com.residencial.dto.DisponibilidadDTO;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReservaServiceImpl implements ReservaService {

    private static final List<String> ESTADOS_QUE_OCUPAN = List.of("PENDIENTE", "APROBADA");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // Hasta cuántos días se puede pedir la disponibilidad de una vez (un mes y algo)
    private static final int MAXIMO_DIAS_DISPONIBILIDAD = 62;
    // Fin de una franja que sigue al día siguiente
    private static final LocalTime FIN_DEL_DIA = LocalTime.of(23, 59, 59);

    private final ReservaRepository repo;
    private final ApartamentoRepository aptoRepo;
    private final ZonaComunRepository zonaRepo;
    private final AccesoService acceso;
    private final NotificacionService notificaciones;
    // Lo que dura una reserva que no dice hora de fin (las de la app anterior a P4)
    private final Duration duracion;
    // Lo máximo que puede durar una reserva con hora de fin
    private final Duration duracionMaxima;
    // Hora de referencia para "fecha pasada": la del conjunto, no la del servidor (Render usa UTC)
    private final ZoneId zonaHoraria;

    public ReservaServiceImpl(ReservaRepository repo,
                              ApartamentoRepository aptoRepo,
                              ZonaComunRepository zonaRepo,
                              AccesoService acceso,
                              NotificacionService notificaciones,
                              @Value("${app.reservas.duracion-horas:2}") long duracionHoras,
                              @Value("${app.reservas.duracion-maxima-horas:8}") long duracionMaximaHoras,
                              @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
        if (duracionHoras < 1 || duracionHoras > 24) {
            throw new IllegalStateException("app.reservas.duracion-horas debe estar entre 1 y 24");
        }
        if (duracionMaximaHoras < 1 || duracionMaximaHoras > 24) {
            throw new IllegalStateException("app.reservas.duracion-maxima-horas debe estar entre 1 y 24");
        }
        this.repo = repo;
        this.aptoRepo = aptoRepo;
        this.zonaRepo = zonaRepo;
        this.acceso = acceso;
        this.notificaciones = notificaciones;
        this.duracion = Duration.ofHours(duracionHoras);
        this.duracionMaxima = Duration.ofHours(duracionMaximaHoras);
        this.zonaHoraria = ZoneId.of(zonaHoraria);
    }

    /** Lo que ocupa una reserva: [inicio, fin). */
    private record Intervalo(LocalDateTime inicio, LocalDateTime fin) {
        boolean seCruzaCon(Intervalo otro) {
            return inicio.isBefore(otro.fin) && otro.inicio.isBefore(fin);
        }
    }

    private Intervalo intervalo(Reserva r) {
        return intervalo(r.getFecha(), r.getHora(), r.getHoraFin());
    }

    // Sin hora de fin (reservas anteriores a P4) dura lo de siempre
    private Intervalo intervalo(LocalDate fecha, LocalTime hora, LocalTime horaFin) {
        LocalDateTime inicio = LocalDateTime.of(fecha, hora);
        return new Intervalo(inicio, horaFin != null ? LocalDateTime.of(fecha, horaFin) : inicio.plus(duracion));
    }

    private ReservaDTO toDTO(Reserva r) {
        // La hora de fin siempre va: guardada o calculada con la duración fija
        return new ReservaDTO(r.getId(), r.getFecha(), r.getHora(), r.getEstado(), r.getZonaComun().getId(),
                r.getApartamento().getId(), intervalo(r).fin().toLocalTime(), r.getAsistentes());
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
            throw error("La zona " + zona.getNombre() + " no se puede reservar");
        }
        if (LocalDateTime.of(dto.getFecha(), dto.getHora()).isBefore(LocalDateTime.now(zonaHoraria))) {
            throw error("No se puede reservar en una fecha u hora pasada");
        }
        if (dto.getHoraFin() != null) {
            if (!dto.getHoraFin().isAfter(dto.getHora())) throw error("La hora de fin debe ser después de la de inicio");
            if (Duration.between(dto.getHora(), dto.getHoraFin()).compareTo(duracionMaxima) > 0) {
                throw error("Una reserva puede durar máximo " + duracionMaxima.toHours() + " horas");
            }
        }
        Intervalo pedido = intervalo(dto.getFecha(), dto.getHora(), dto.getHoraFin());
        verificarHorario(zona, pedido);
        verificarAforo(zona, dto.getAsistentes());
        buscarChoque(zona.getId(), pedido, ESTADOS_QUE_OCUPAN, null).ifPresent(this::rechazarPorChoque);

        Reserva r = repo.save(new Reserva(null, dto.getFecha(), dto.getHora(), "PENDIENTE", zona, apto,
                dto.getHoraFin(), dto.getAsistentes()));
        notificaciones.avisarNuevaReserva(r);
        return toDTO(r);
    }

    /** Dentro del horario de la zona (si lo tiene) y sin pasarse al día siguiente. */
    private void verificarHorario(ZonaComun zona, Intervalo pedido) {
        LocalTime apertura = zona.getHoraApertura();
        LocalTime cierre = zona.getHoraCierre();
        if (apertura == null && cierre == null) return;
        boolean antes = apertura != null && pedido.inicio().toLocalTime().isBefore(apertura);
        boolean despues = cierre != null && (pedido.fin().toLocalDate().isAfter(pedido.inicio().toLocalDate())
                || pedido.fin().toLocalTime().isAfter(cierre));
        if (antes || despues) {
            throw error(zona.getNombre() + " se puede reservar de "
                    + (apertura == null ? "00:00" : apertura.format(HORA)) + " a "
                    + (cierre == null ? "24:00" : cierre.format(HORA)));
        }
    }

    private void verificarAforo(ZonaComun zona, Integer asistentes) {
        if (asistentes == null) return; // la app anterior a P4 no lo envía
        if (asistentes < 1) throw error("Debe asistir al menos una persona");
        if (zona.getAforo() != null && asistentes > zona.getAforo()) {
            throw error("El aforo de " + zona.getNombre() + " es de " + zona.getAforo() + " personas");
        }
    }

    @Override
    @Transactional
    public ReservaDTO cambiarEstado(Long id, String nuevoEstado) {
        String estado = Catalogo.ESTADO_RESERVA.normalizar(nuevoEstado);
        return repo.findById(id).map(r -> {
            if ("APROBADA".equals(estado) && !"APROBADA".equals(r.getEstado())) {
                // Por si hay datos antiguos: no aprobar dos reservas que se cruzan
                zonaRepo.findByIdParaReservar(r.getZonaComun().getId());
                buscarChoque(r.getZonaComun().getId(), intervalo(r), List.of("APROBADA"), r.getId())
                        .ifPresent(this::rechazarPorChoque);
            }
            boolean cambio = !estado.equals(r.getEstado());
            r.setEstado(estado);
            Reserva guardada = repo.save(r);
            // El residente se entera de que su reserva fue aprobada o rechazada
            if (cambio && !"PENDIENTE".equals(estado)) notificaciones.avisarReservaRevisada(guardada);
            return toDTO(guardada);
        }).orElse(null);
    }

    @Override
    public void eliminar(Long id) {
        Reserva r = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada: " + id));
        if (!acceso.esAdminActual()) {
            acceso.verificarApartamento(r.getApartamento().getId());
            if (!LocalDateTime.of(r.getFecha(), r.getHora()).isAfter(LocalDateTime.now(zonaHoraria))) {
                throw error("Solo puedes cancelar una reserva que aún no empieza");
            }
        }
        repo.delete(r);
    }

    @Override
    public DisponibilidadDTO disponibilidad(Long zonaId, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw error("El rango de fechas no es válido");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAXIMO_DIAS_DISPONIBILIDAD) {
            throw error("Se pueden consultar máximo " + MAXIMO_DIAS_DISPONIBILIDAD + " días a la vez");
        }
        ZonaComun zona = zonaRepo.findById(zonaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Zona común no encontrada"));
        Long propio = acceso.apartamentoActual().orElse(null);
        // Desde el día anterior: una reserva tarde en la noche puede seguir al día siguiente
        List<Reserva> reservas = repo.findByZonaComunIdAndFechaBetweenAndEstadoIn(
                zonaId, desde.minusDays(1), hasta, ESTADOS_QUE_OCUPAN);

        List<DisponibilidadDTO.Dia> dias = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            LocalDateTime inicioDia = dia.atStartOfDay();
            Intervalo delDia = new Intervalo(inicioDia, inicioDia.plusDays(1));
            List<DisponibilidadDTO.Franja> franjas = new ArrayList<>();
            for (Reserva r : reservas) {
                Intervalo ocupado = intervalo(r);
                if (!ocupado.seCruzaCon(delDia)) continue;
                LocalDateTime inicio = ocupado.inicio().isBefore(inicioDia) ? inicioDia : ocupado.inicio();
                LocalTime fin = ocupado.fin().isBefore(delDia.fin()) ? ocupado.fin().toLocalTime() : FIN_DEL_DIA;
                franjas.add(new DisponibilidadDTO.Franja(inicio.toLocalTime(), fin, r.getEstado(),
                        propio != null && propio.equals(r.getApartamento().getId())));
            }
            franjas.sort(Comparator.comparing(DisponibilidadDTO.Franja::getInicio));
            dias.add(new DisponibilidadDTO.Dia(dia, franjas));
        }
        return new DisponibilidadDTO(zona.getId(), zona.getAforo(), zona.getHoraApertura(), zona.getHoraCierre(), dias);
    }

    /** Primera reserva de la zona en esos estados que se cruza con el intervalo pedido. */
    private Optional<Reserva> buscarChoque(Long zonaId, Intervalo pedido, List<String> estados, Long excluirId) {
        LocalDate fecha = pedido.inicio().toLocalDate();
        return repo.findByZonaComunIdAndFechaBetweenAndEstadoIn(zonaId, fecha.minusDays(1), fecha.plusDays(1), estados)
                .stream()
                .filter(r -> !Objects.equals(r.getId(), excluirId))
                .filter(r -> intervalo(r).seCruzaCon(pedido))
                .findFirst();
    }

    private void rechazarPorChoque(Reserva otra) {
        Intervalo ocupado = intervalo(otra);
        // Las reservas sin hora de fin ocupan siempre lo mismo: se explica cuánto
        String duracionFija = otra.getHoraFin() == null ? " Cada reserva ocupa " + duracion.toHours() + " horas." : "";
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La zona ya está reservada el " + ocupado.inicio().format(FECHA) + " de "
                        + ocupado.inicio().format(HORA) + " a " + ocupado.fin().format(HORA) + "." + duracionFija);
    }

    private static ResponseStatusException error(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
