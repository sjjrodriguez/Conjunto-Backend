package com.residencial.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonaComunResponse {
    private long id;
    private String nombre;
    private String descripcion;
    private int capacidadMaxima;
    private BigDecimal costoReserva;
    private boolean disponible;
    private long idConjunto;
    private String nombreConjunto;
}
