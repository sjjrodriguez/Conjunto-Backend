package com.residencial.service;

import com.residencial.model.Apartamento;
import java.util.List;

public interface ApartamentoService {
    List<Apartamento> listarTodos();
    Apartamento buscarPorId(Long id);
    Apartamento guardar(Apartamento apartamento);
    Apartamento actualizar(Long id, Apartamento apartamento);
    void eliminar(Long id);
}
