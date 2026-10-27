package com.residencial.service;

import com.residencial.dto.ObraDTO;
import java.util.List;

public interface ObraService {
    List<ObraDTO> listarPorApartamento(Long apartamentoId);
    List<ObraDTO> listarEnProceso();
    List<ObraDTO> listarTodas();
    ObraDTO guardar(ObraDTO dto);
    ObraDTO actualizar(Long id, ObraDTO dto);
    ObraDTO cambiarEstado(Long id, String nuevoEstado);
    void eliminar(Long id);

    // P5 bloque 6: filtros de la administración y aprobación
    List<ObraDTO> buscar(String alcance, String estado, String aprobacion, String texto);
    ObraDTO obtener(Long id);
    ObraDTO revisar(Long id, boolean aprobada, String motivo);
}
