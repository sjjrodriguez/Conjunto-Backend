package com.residencial.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonalObraDTO {
    private Long id;
    private String nombreTrabajador;
    private String cedula;
    private Boolean arlEstado;
    private Long obraId;
}
