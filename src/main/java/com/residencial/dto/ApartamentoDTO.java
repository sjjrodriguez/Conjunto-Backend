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

    // P5 (solo respuesta): citófonos y si el apartamento está arrendado (arrendatario activo)
    private String citofono1;
    private String citofono2;
    private Boolean arrendado;

    public ApartamentoDTO(Long id, String torre, String apto) {
        this(id, torre, apto, null, null, null);
    }
}
