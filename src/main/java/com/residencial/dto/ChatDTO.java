package com.residencial.dto;

import java.time.LocalDateTime;

/** Chat entre la administración y cada residente (P5 bloque 8). */
public final class ChatDTO {

    private ChatDTO() {}

    /** noLeidos: los mensajes del otro lado que quien consulta no ha visto. */
    public record Conversacion(Long id, Long apartamentoId, String torre, String apto, String participante,
                               String ultimoMensaje, LocalDateTime ultimoMensajeEn, int noLeidos) {}

    /** propio: lo escribió quien consulta (la burbuja va a la derecha). */
    public record Mensaje(Long id, String autor, String autorNombre, String texto, LocalDateTime enviadoEn, boolean propio) {}

    /** Abrir (o crear) el chat: la administración dice con quién; el residente no manda nada. */
    public record Abrir(Long apartamentoId, String participante) {}

    public record Nuevo(String texto) {}
}
