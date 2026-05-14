package com.residencial.service.impl;

import com.residencial.dto.ReservaRequest;
import com.residencial.dto.ReservaResponse;
import com.residencial.dto.ZonaComunResponse;
import com.residencial.model.Apartamento;
import com.residencial.model.Reserva;
import com.residencial.model.ZonaComun;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ReservaRepository;
import com.residencial.repository.ZonaComunRepository;
import com.residencial.service.ReservaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservaServiceImpl implements ReservaService {

    private final ReservaRepository reservaRepository;
    private final ZonaComunRepository zonaComunRepository;
    private final ApartamentoRepository apartamentoRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ZonaComunResponse> listarZonasDisponibles() {
        return zonaComunRepository.findByDisponibleTrue().stream()
                .map(this::toZonaComunResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ReservaResponse registrarReserva(ReservaRequest request) {
        ZonaComun zona = zonaComunRepository.findById(request.getIdZona())
                .orElseThrow(() -> new NoSuchElementException("Zona común no encontrada: " + request.getIdZona()));

        if (!zona.isDisponible()) {
            throw new IllegalStateException("La zona común no está disponible para reservas.");
        }

        Apartamento apartamento = apartamentoRepository.findById(request.getIdApartamento())
                .orElseThrow(() -> new NoSuchElementException("Apartamento no encontrado: " + request.getIdApartamento()));

        List<Reserva> conflictos = reservaRepository.findConflictos(
                request.getIdZona(),
                request.getFechaReserva(),
                request.getHoraInicio(),
                request.getHoraFin());

        if (!conflictos.isEmpty()) {
            throw new IllegalStateException("Ya existe una reserva en ese horario para la zona común seleccionada.");
        }

        Reserva reserva = Reserva.builder()
                .zonaComun(zona)
                .apartamento(apartamento)
                .fechaReserva(request.getFechaReserva())
                .horaInicio(request.getHoraInicio())
                .horaFin(request.getHoraFin())
                .estado(Reserva.EstadoReserva.CONFIRMADA)
                .observaciones(request.getObservaciones())
                .build();

        Reserva saved = reservaRepository.save(reserva);
        return toReservaResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponse> listarReservasPorApartamento(long idApartamento) {
        return reservaRepository.findByApartamentoId(idApartamento).stream()
                .map(this::toReservaResponse)
                .collect(Collectors.toList());
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private ZonaComunResponse toZonaComunResponse(ZonaComun z) {
        return ZonaComunResponse.builder()
                .id(z.getId())
                .nombre(z.getNombre())
                .descripcion(z.getDescripcion())
                .capacidadMaxima(z.getCapacidadMaxima())
                .costoReserva(z.getCostoReserva())
                .disponible(z.isDisponible())
                .idConjunto(z.getConjunto().getId())
                .nombreConjunto(z.getConjunto().getNombre())
                .build();
    }

    private ReservaResponse toReservaResponse(Reserva r) {
        return ReservaResponse.builder()
                .id(r.getId())
                .fechaReserva(r.getFechaReserva())
                .horaInicio(r.getHoraInicio())
                .horaFin(r.getHoraFin())
                .estado(r.getEstado() != null ? r.getEstado().name() : null)
                .observaciones(r.getObservaciones())
                .idZonaComun(r.getZonaComun().getId())
                .nombreZonaComun(r.getZonaComun().getNombre())
                .idApartamento(r.getApartamento().getId())
                .numeroApartamento(r.getApartamento().getNumero())
                .build();
    }
}
