package com.residencial.dto;

import lombok.*;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZonaComunDTO {
    private Long id;
    private String nombre;
    private String estadoRecibido;
    private String descripcionEstado;
    private Boolean esReservable;
    // P4: máximo de personas por reserva y horario en que se puede reservar (null = sin límite)
    private Integer aforo;
    private LocalTime horaApertura;
    private LocalTime horaCierre;

    public ZonaComunDTO(Long id, String nombre, String estadoRecibido, String descripcionEstado, Boolean esReservable) {
        this(id, nombre, estadoRecibido, descripcionEstado, esReservable, null, null, null);
    }
}
