package com.residencial.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Publicación del feed con sus adjuntos; cada url es un enlace firmado que vence en una hora. */
public record PublicacionDTO(Long id, String texto, String autor, LocalDateTime creadaEn, LocalDateTime editadaEn,
                             List<Adjunto> adjuntos, String titulo, String categoria, String audiencia, String torre,
                             boolean fijado, boolean leida) {

    public record Adjunto(Long id, String tipo, String nombre, String mime, long tamano, String url) {}

    /** Una página del feed: hayMas = falta cargar publicaciones más antiguas. */
    public record Pagina(List<PublicacionDTO> publicaciones, boolean hayMas) {}

    /** Editar: lo que viene null se deja como estaba. */
    public record Edicion(String texto, String titulo, String categoria, String audiencia, String torre, Boolean fijado) {}

    public record Fijar(boolean fijado) {}
}
