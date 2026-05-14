package com.residencial.service;

import com.residencial.dto.ReservaRequest;
import com.residencial.dto.ReservaResponse;
import com.residencial.dto.ZonaComunResponse;

import java.util.List;

public interface ReservaService {
    List<ZonaComunResponse> listarZonasDisponibles();
    ReservaResponse registrarReserva(ReservaRequest request);
    List<ReservaResponse> listarReservasPorApartamento(long idApartamento);
}
