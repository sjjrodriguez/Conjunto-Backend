package com.residencial.service;

import com.residencial.dto.PagoDTO;

public interface PagoService {
    PagoDTO consultarPorApartamento(Long apartamentoId);
    PagoDTO simularPago(Long apartamentoId);
    PagoDTO guardar(PagoDTO dto);
    PagoDTO actualizar(Long id, PagoDTO dto);
}
