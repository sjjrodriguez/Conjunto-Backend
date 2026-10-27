package com.residencial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ObraDTO {
    private Long id;
    private String descripcionAdecuacion;
    private LocalDate fechaInicio;
    private LocalDate fechaFinEstimada;
    private String estadoObra; // "EN_PROCESO" o "FINALIZADA"
    private Long apartamentoId; // null = obra del conjunto (P5)

    // P5 (solo respuesta): aprobación de la administración y de qué apartamento es
    private String aprobacion; // PENDIENTE, APROBADA o RECHAZADA
    private String motivoRechazo;
    private String torre;
    private String apto;
    private Integer personalPendiente;

    public ObraDTO(Long id, String descripcionAdecuacion, LocalDate fechaInicio, LocalDate fechaFinEstimada,
                   String estadoObra, Long apartamentoId) {
        this(id, descripcionAdecuacion, fechaInicio, fechaFinEstimada, estadoObra, apartamentoId, null, null, null, null, null);
    }

    public record Revision(boolean aprobada, String motivo) {}
}
