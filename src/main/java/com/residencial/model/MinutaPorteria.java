package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * P8 bloque 5: una anotación de la minuta de la portería (ronda, novedad, paquete, incidente o
 * cambio de turno), con el guardia que la escribió. No se edita ni se borra.
 */
@Entity
@Table(name = "minuta_porteria", indexes = @Index(name = "idx_minuta_conjunto_fecha", columnList = "conjunto_id, creada_en"))
@Getter
@Setter
@NoArgsConstructor
public class MinutaPorteria extends EntidadConjunto {

    public static final String NOVEDAD = "NOVEDAD";
    public static final Set<String> CATEGORIAS = Set.of(NOVEDAD, "RONDA", "PAQUETE", "INCIDENTE", "TURNO");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String categoria = NOVEDAD;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "guardia_nombre", length = 150)
    private String guardiaNombre;

    @Column(name = "registrada_por", length = 60)
    private String registradaPor;
}
