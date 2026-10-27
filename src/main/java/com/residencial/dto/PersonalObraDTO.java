package com.residencial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersonalObraDTO {
    private Long id;
    private String nombreTrabajador;
    private String cedula;
    private Boolean arlEstado;
    private Long obraId;
    private String torrePiso;
    private String aptoNum;

    // P5 (solo respuesta): aprobación y certificado ARL
    private String aprobacion; // PENDIENTE, APROBADO o RECHAZADO
    private String motivoRechazo;
    private Boolean tieneCertificado;
    private LocalDateTime certificadoSubidoEn;

    public PersonalObraDTO(Long id, String nombreTrabajador, String cedula, Boolean arlEstado, Long obraId,
                           String torrePiso, String aptoNum) {
        this(id, nombreTrabajador, cedula, arlEstado, obraId, torrePiso, aptoNum, null, null, null, null);
    }

    public record Revision(boolean aprobado, String motivo) {}
}
