package com.residencial.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApartamentoResumenResponse {
    private long id;
    private String numero;
    private int piso;
    private long idTorre;
    private String nombreTorre;
}
