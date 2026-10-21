package com.residencial.dto;

import lombok.*;

// Cuerpo de POST/PUT /api/apartamentos. La contraseña solo se escribe, nunca se devuelve.
// En PUT es opcional: si no se envía se conserva la actual.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApartamentoRequest {
    private String torre;
    private String apto;
    private String contrasena;
}
