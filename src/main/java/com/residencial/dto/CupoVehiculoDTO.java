package com.residencial.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cupo de un tipo de vehículo por apartamento. requierePlaca (solo lectura): carros y motos
 * la necesitan; bicicletas y patinetas no.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CupoVehiculoDTO {
    private String tipo;
    private Integer maximo;
    private Boolean requierePlaca;
    // P5: true si es el cupo propio del apartamento (no el general)
    private Boolean personalizado;

    public CupoVehiculoDTO(String tipo, Integer maximo, Boolean requierePlaca) {
        this(tipo, maximo, requierePlaca, null);
    }
}
