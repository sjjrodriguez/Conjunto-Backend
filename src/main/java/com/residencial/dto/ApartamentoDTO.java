package com.residencial.dto;

import lombok.*;

// Respuesta de la API: nunca incluye la contraseña
@Data
@NoArgsConstructor
public class ApartamentoDTO {
    private Long id;
    private String torre;
    private String apto;

    // P5 (solo respuesta): citófonos y si el apartamento está arrendado (arrendatario activo)
    private String citofono1;
    private String citofono2;
    private Boolean arrendado;

    // P6: cómo está ocupado y qué le deja ver la administración al arrendatario
    private String tipoOcupacion;
    private Boolean arrendatarioVeCartera;
    private Boolean arrendatarioPuedePagar;
    private Boolean arrendatarioVeObras;

    public ApartamentoDTO(Long id, String torre, String apto, String citofono1, String citofono2, Boolean arrendado) {
        this.id = id;
        this.torre = torre;
        this.apto = apto;
        this.citofono1 = citofono1;
        this.citofono2 = citofono2;
        this.arrendado = arrendado;
    }

    public ApartamentoDTO(Long id, String torre, String apto) {
        this(id, torre, apto, null, null, null);
    }

    /** Cuerpo de PATCH /api/apartamentos/{id}/ocupacion-y-banderas: lo que no se envía no cambia. */
    public record OcupacionYBanderas(String tipoOcupacion, Boolean arrendatarioVeCartera,
                                     Boolean arrendatarioPuedePagar, Boolean arrendatarioVeObras) {}
}
