package com.residencial.service;

import com.residencial.dto.PagoDTO;
import java.util.List;

public interface PagoService {
    List<PagoDTO> listarTodos();
    PagoDTO consultarPorApartamento(Long apartamentoId);
    PagoDTO simularPago(Long apartamentoId);
    PagoDTO guardar(PagoDTO dto);
    PagoDTO actualizar(Long id, PagoDTO dto);
}
