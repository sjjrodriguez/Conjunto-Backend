package com.residencial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Token de Firebase del celular. */
@Data
@NoArgsConstructor
public class DispositivoRequest {
    @NotBlank
    @Size(max = 512)
    private String token;

    // P6: la app que lo registra; el tipo también viaja en X-App-Tipo
    @Size(max = 20)
    private String tipoApp;

    @Size(max = 100)
    private String applicationId;

    public DispositivoRequest(String token) {
        this.token = token;
    }
}
