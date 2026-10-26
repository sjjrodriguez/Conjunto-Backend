package com.residencial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResidenteDTO {
    private Long id;
    private String nombreCompleto;
    private String telefono;
    private String identificacion;
    private String tipoHabitante; // "PROPIETARIO" o "ARRENDATARIO"
    private Long apartamentoId;

    // P4 bloque 6 (solo respuesta)
    private String estado;        // PENDIENTE, APROBADO o RECHAZADO
    private Boolean tieneAcceso;  // true si puede entrar con su documento
    private String torre;         // para la lista de pendientes del administrador
    private String apto;

    // Contraseña inicial al crearlo; nunca se devuelve
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String contrasena;

    // P5 (solo respuesta): datos del titular que se registró desde la app
    private String correo;
    private Boolean titular;
    private Boolean aprobadoPropietario; // arrendatarios: si ya lo aprobó el propietario

    public ResidenteDTO(Long id, String nombreCompleto, String telefono, String identificacion,
                        String tipoHabitante, Long apartamentoId) {
        this(id, nombreCompleto, telefono, identificacion, tipoHabitante, apartamentoId,
                null, null, null, null, null, null, null, null);
    }
}
