package com.residencial.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehiculoDTO {
    private Long id;
    private String placa; // obligatoria para Carro y Moto
    private String marca;
    private String color;
    private String tipo; // Catalogo.TIPO_VEHICULO: Carro, Moto, Bicicleta, Patineta...
    private Long apartamentoId;
    // Solo lectura (P4): PENDIENTE, APROBADO o RECHAZADO, el motivo si se rechazó, y de qué
    // apartamento es (para la lista de aprobación del administrador)
    private String estado;
    private String motivoRechazo;
    private String torre;
    private String apto;
    // P5: el parqueadero asignado (null = sin parqueadero)
    private Long parqueaderoId;
    private String parqueadero;

    /** Lo que enviaba la app antes de P4 (y lo que basta para registrar uno). */
    public VehiculoDTO(Long id, String placa, String marca, String color, String tipo, Long apartamentoId) {
        this(id, placa, marca, color, tipo, apartamentoId, null, null, null, null, null, null);
    }
}
