package com.residencial.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehiculoDTO {
    private Long id;
    private String placa;
    private String marca;
    private String color;
    private String tipo; // "Carro" o "Moto"
    private Long apartamentoId;
}
