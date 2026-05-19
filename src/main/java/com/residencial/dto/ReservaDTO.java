package com.residencial.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaDTO {
    private Long id;
    private LocalDate fecha;
    private LocalTime hora;
    private String estado;
    private Long zonaComunId;
    private Long apartamentoId;
}
