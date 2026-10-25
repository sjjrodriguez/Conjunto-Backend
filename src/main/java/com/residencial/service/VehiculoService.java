package com.residencial.service;

import com.residencial.dto.CupoVehiculoDTO;
import com.residencial.dto.VehiculoDTO;
import java.util.List;

public interface VehiculoService {
    List<VehiculoDTO> listarPorApartamento(Long apartamentoId);
    VehiculoDTO guardar(VehiculoDTO dto);
    void eliminar(Long id);

    /** Todos los vehículos en un estado (el administrador revisa los PENDIENTE). */
    List<VehiculoDTO> listarPorEstado(String estado);

    /** El administrador aprueba o rechaza; el residente recibe una notificación. */
    VehiculoDTO cambiarEstado(Long id, String estado, String motivo);

    List<CupoVehiculoDTO> cupos();
    List<CupoVehiculoDTO> guardarCupos(List<CupoVehiculoDTO> cupos);
}
