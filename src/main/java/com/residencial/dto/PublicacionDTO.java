package com.residencial.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Publicación del feed con sus adjuntos; cada url es un enlace firmado que vence en una hora. */
public record PublicacionDTO(Long id, String texto, String autor, LocalDateTime creadaEn, LocalDateTime editadaEn,
                             List<Adjunto> adjuntos) {

    public record Adjunto(Long id, String tipo, String nombre, String mime, long tamano, String url) {}

    /** Una página del feed: hayMas = falta cargar publicaciones más antiguas. */
    public record Pagina(List<PublicacionDTO> publicaciones, boolean hayMas) {}

    public record Edicion(String texto) {}
}
