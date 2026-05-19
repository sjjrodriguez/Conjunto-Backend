package com.residencial.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObraDTO {
    private Long id;
    private String descripcionAdecuacion;
    private LocalDate fechaInicio;
    private LocalDate fechaFinEstimada;
    private String estadoObra; // "EN_PROCESO" o "FINALIZADA"
    private Long apartamentoId;
}
