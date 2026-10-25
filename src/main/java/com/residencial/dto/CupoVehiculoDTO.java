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
}
