package com.residencial.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoRequest {
    private long idApartamento;
    private int mes;
    private int anio;
    private BigDecimal valor;
    private String observaciones;
}
