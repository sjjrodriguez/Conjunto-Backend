package com.residencial.dto;

import lombok.*;

// Respuesta de la API: nunca incluye la contraseña
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApartamentoDTO {
    private Long id;
    private String torre;
    private String apto;
}
