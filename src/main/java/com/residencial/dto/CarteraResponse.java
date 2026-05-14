package com.residencial.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarteraResponse {
    private long idApartamento;
    private String numeroApartamento;
    private BigDecimal saldoPendiente;
    private int cuotasPendientes;
    private String estadoCartera;           // "AL DIA" | "EN MORA"
    private List<PagoResponse> pagosPendientes;
}
