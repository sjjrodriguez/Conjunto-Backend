package com.residencial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Token de Firebase del celular. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispositivoRequest {
    @NotBlank
    @Size(max = 512)
    private String token;
}
