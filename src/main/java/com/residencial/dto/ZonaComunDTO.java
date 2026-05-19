package com.residencial.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZonaComunDTO {
    private Long id;
    private String nombre;
    private String estadoRecibido;
    private String descripcionEstado;
}
