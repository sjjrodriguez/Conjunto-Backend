package com.residencial.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoDTO {
    private Long id;
    private Double saldoPendiente;
    private String estadoCuenta;
    private String observaciones;
    private Long apartamentoId;
}
