package com.residencial.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoResponse {
    private long id;
    private int mes;
    private int anio;
    private BigDecimal valor;
    private String estado;
    private LocalDateTime fechaPago;
    private String observaciones;
    private long idApartamento;
    private String numeroApartamento;
}
