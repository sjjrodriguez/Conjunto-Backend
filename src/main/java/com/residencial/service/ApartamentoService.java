package com.residencial.service;

import com.residencial.dto.ApartamentoDetalleResponse;
import com.residencial.dto.ApartamentoResumenResponse;

import java.util.List;

public interface ApartamentoService {
    List<ApartamentoResumenResponse> listarPorTorre(long idTorre);
    ApartamentoDetalleResponse obtenerDetalle(long idApartamento);
}
