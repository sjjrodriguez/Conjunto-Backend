package com.residencial.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaResponse {
    private long id;
    private LocalDate fechaReserva;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String estado;
    private String observaciones;
    private long idZonaComun;
    private String nombreZonaComun;
    private long idApartamento;
    private String numeroApartamento;
}
