package com.residencial.service;

import com.residencial.dto.CuotaDTO;
import com.residencial.dto.PersonalObraDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PersonalObraService {
    List<PersonalObraDTO> listarTodos();
    List<PersonalObraDTO> listarPorObra(Long obraId);
    PersonalObraDTO guardar(PersonalObraDTO dto);
    PersonalObraDTO actualizar(Long id, PersonalObraDTO dto);
    void eliminar(Long id);

    // P5 bloque 6: certificado ARL y aprobación
    PersonalObraDTO subirCertificado(Long id, MultipartFile archivo);
    CuotaDTO.Enlace enlaceCertificado(Long id);
    PersonalObraDTO revisar(Long id, boolean aprobado, String motivo);
}
