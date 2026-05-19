package com.residencial.service;

import com.residencial.dto.ResidenteDTO;
import java.util.List;

public interface ResidenteService {
    List<ResidenteDTO> listarPorApartamento(Long apartamentoId);
    ResidenteDTO guardar(ResidenteDTO dto);
    ResidenteDTO actualizar(Long id, ResidenteDTO dto);
    void eliminar(Long id);
}
