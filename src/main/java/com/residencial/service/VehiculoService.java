package com.residencial.service;

import com.residencial.dto.VehiculoDTO;
import java.util.List;

public interface VehiculoService {
    List<VehiculoDTO> listarPorApartamento(Long apartamentoId);
    VehiculoDTO guardar(VehiculoDTO dto);
    void eliminar(Long id);
}
