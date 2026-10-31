package com.residencial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.Instant;

// Respuesta de los dos logins. id, torre y apto siguen en el primer nivel
// para que la app anterior pueda leerlos durante la transición.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String token;
    private Instant expiraEn;
    private String rol;      // "ADMIN", "CONTADOR", "PROPIETARIO" o "ARRENDATARIO"
    private Long id;
    private String usuario;  // solo admin
    private String torre;    // solo propietario
    private String apto;     // solo propietario
    private Long residenteId; // solo si entró con su documento
    private String nombre;    // solo si entró con su documento
    private Boolean principal; // P5: solo admin; el principal maneja el equipo

    // P6: solo residentes. Cómo está ocupado el apartamento y qué le deja ver la administración al arrendatario
    private String tipoOcupacion;
    private Boolean arrendatarioVeCartera;
    private Boolean arrendatarioPuedePagar;
    private Boolean arrendatarioVeObras;
    // Si solo la administración registra vehículos (ajuste del conjunto)
    private Boolean vehiculosAltaSoloAdmin;
    // Hay una versión de los términos o de la política que esta cuenta aún no acepta
    private Boolean consentimientosPendientes;
}
