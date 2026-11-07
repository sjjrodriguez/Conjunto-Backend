package com.residencial.service;

import com.residencial.dto.DisponibilidadDTO;
import com.residencial.dto.ReservaDTO;
import java.time.LocalDate;
import java.util.List;

public interface ReservaService {
    List<ReservaDTO> listarTodas();
    List<ReservaDTO> listarPorApartamento(Long apartamentoId);
    ReservaDTO guardar(ReservaDTO dto);
    ReservaDTO cambiarEstado(Long id, String nuevoEstado);

    /** P11: con el motivo que escribió la administración (opcional). */
    ReservaDTO cambiarEstado(Long id, String nuevoEstado, String motivo);
    void eliminar(Long id);

    /** Lo ocupado de una zona entre dos fechas (incluidas), para el calendario. */
    DisponibilidadDTO disponibilidad(Long zonaId, LocalDate desde, LocalDate hasta);
    // P5 bloque 5
    ReservaDTO actualizar(Long id, ReservaDTO dto);
    List<ReservaDTO> listarTodas(String estado);
}
