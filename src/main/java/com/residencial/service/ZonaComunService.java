package com.residencial.service;

import com.residencial.dto.ZonaComunDTO;
import java.util.List;

public interface ZonaComunService {
    List<ZonaComunDTO> listarTodas();
    ZonaComunDTO buscarPorId(Long id);
    ZonaComunDTO guardar(ZonaComunDTO dto);
    ZonaComunDTO actualizar(Long id, ZonaComunDTO dto);
    void eliminar(Long id);
}
