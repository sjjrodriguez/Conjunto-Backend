package com.residencial.service;

import com.residencial.dto.ReservaDTO;
import java.util.List;

public interface ReservaService {
    List<ReservaDTO> listarTodas();
    List<ReservaDTO> listarPorApartamento(Long apartamentoId);
    ReservaDTO guardar(ReservaDTO dto);
    ReservaDTO cambiarEstado(Long id, String nuevoEstado);
    void eliminar(Long id);
}
