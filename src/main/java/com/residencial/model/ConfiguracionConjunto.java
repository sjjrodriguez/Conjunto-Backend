package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * Un ajuste del conjunto que cambia la administración (clave/valor).
 * P7: la clave se repite en cada conjunto, así que el conjunto es parte de la llave y lo pone
 * ConfiguracionService (no usa EntidadConjunto: Hibernate no deja que el conjunto sea parte del id).
 */
@Entity
@Table(name = "configuracion_conjunto")
@IdClass(ConfiguracionConjunto.Llave.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionConjunto {

    public static final String VEHICULOS_ALTA_SOLO_ADMIN = "vehiculos_alta_solo_admin";

    @Id
    @Column(name = "conjunto_id")
    private Long conjuntoId;

    @Id
    @Column(length = 60)
    private String clave;

    @Column(nullable = false)
    private String valor;

    /** Hibernate 6.4 no acepta un record como @IdClass. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Llave implements Serializable {
        private Long conjuntoId;
        private String clave;
    }
}
