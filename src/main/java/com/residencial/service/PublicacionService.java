package com.residencial.service;

import com.residencial.dto.PublicacionDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Feed de publicaciones de la administración (P5 bloque 3). */
public interface PublicacionService {
    PublicacionDTO.Pagina listar(int pagina);
    PublicacionDTO publicar(String texto, List<MultipartFile> archivos);
    PublicacionDTO editar(Long id, String texto);
    void eliminar(Long id);
}
