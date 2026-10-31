package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

/** Un ajuste del conjunto que cambia la administración (clave/valor). */
@Entity
@Table(name = "configuracion_conjunto")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionConjunto {

    public static final String VEHICULOS_ALTA_SOLO_ADMIN = "vehiculos_alta_solo_admin";

    @Id
    @Column(length = 60)
    private String clave;

    @Column(nullable = false)
    private String valor;
}
