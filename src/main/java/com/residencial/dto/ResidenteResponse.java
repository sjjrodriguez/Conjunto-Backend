package com.residencial.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResidenteResponse {
    private long id;
    private String nombreCompleto;
    private String documento;
    private String telefono;
    private String email;
    private String tipoResidente;
}
