package com.residencial.service;

import com.residencial.dto.PersonalObraDTO;
import java.util.List;

public interface PersonalObraService {
    List<PersonalObraDTO> listarTodos();
    List<PersonalObraDTO> listarPorObra(Long obraId);
    PersonalObraDTO guardar(PersonalObraDTO dto);
    PersonalObraDTO actualizar(Long id, PersonalObraDTO dto);
    void eliminar(Long id);
}