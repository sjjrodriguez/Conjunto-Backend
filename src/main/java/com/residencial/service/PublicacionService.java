package com.residencial.service;

import com.residencial.dto.PublicacionDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Feed de publicaciones de la administración (P5 bloque 3; categorías, audiencia y lectura en P6 bloque 3). */
public interface PublicacionService {
    PublicacionDTO.Pagina listar(int pagina);
    PublicacionDTO publicar(String texto, List<MultipartFile> archivos, String titulo, String categoria,
                            String audiencia, String torre, boolean fijado);
    PublicacionDTO editar(Long id, PublicacionDTO.Edicion edicion);
    PublicacionDTO fijar(Long id, boolean fijado);
    /** Marca la publicación como leída por quien la abre; repetirlo no cambia nada. */
    void marcarLeida(Long id);
    void eliminar(Long id);
}
