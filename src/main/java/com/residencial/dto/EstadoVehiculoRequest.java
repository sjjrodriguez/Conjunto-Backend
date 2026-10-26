package com.residencial.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** El administrador aprueba o rechaza un vehículo; el motivo se muestra al residente. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadoVehiculoRequest {
    private String estado; // APROBADO o RECHAZADO

    @Size(max = 300, message = "El motivo puede tener máximo 300 caracteres")
    private String motivo;

    // P5: al aprobar, el parqueadero que se le asigna (opcional)
    private Long parqueaderoId;
}
