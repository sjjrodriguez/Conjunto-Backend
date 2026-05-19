package com.residencial.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResidenteDTO {
    private Long id;
    private String nombreCompleto;
    private String telefono;
    private String identificacion;
    private String tipoHabitante; // "PROPIETARIO" o "ARRENDATARIO"
    private Long apartamentoId;
}
