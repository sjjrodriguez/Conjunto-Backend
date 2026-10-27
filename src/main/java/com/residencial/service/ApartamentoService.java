package com.residencial.service;

import com.residencial.dto.ApartamentoDTO;
import com.residencial.dto.ApartamentoRequest;
import java.util.List;

public interface ApartamentoService {
    List<ApartamentoDTO> listarTodos();
    ApartamentoDTO buscarPorId(Long id);
    ApartamentoDTO guardar(ApartamentoRequest apartamento);
    ApartamentoDTO actualizar(Long id, ApartamentoRequest apartamento);
    ApartamentoDTO cambiarCitofonos(Long id, String citofono1, String citofono2);
    boolean cambiarContrasena(Long id, String nuevaContrasena);
    void eliminar(Long id);
}
