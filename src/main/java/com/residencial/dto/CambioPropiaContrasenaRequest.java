package com.residencial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** El usuario cambia su propia contraseña (Cuenta → Perfil): debe confirmar la actual. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CambioPropiaContrasenaRequest {
    @NotBlank(message = "La contraseña actual es obligatoria")
    private String contrasenaActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
    private String contrasenaNueva;
}
