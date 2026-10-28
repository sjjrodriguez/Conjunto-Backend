package com.residencial.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** [sujeto] (el del token) aceptó esa versión de un texto legal. */
@Entity
@Table(name = "consentimientos",
        uniqueConstraints = @UniqueConstraint(name = "uk_consentimiento", columnNames = {"sujeto", "texto_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Consentimiento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String sujeto;

    @Column(name = "texto_id", nullable = false)
    private Long textoId;

    @Column(nullable = false)
    private LocalDateTime aceptadoEn;

    @Column(length = 64)
    private String ip;
}
