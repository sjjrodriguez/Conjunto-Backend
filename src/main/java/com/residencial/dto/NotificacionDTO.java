package com.residencial.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionDTO {
    private Long id;
    private String tipo; // "RESERVA" o "VEHICULO"
    private String mensaje;
    private LocalDateTime fecha;
    private Boolean leida;
}
