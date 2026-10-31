package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Política de privacidad o términos, con versión: al subir la versión, todos deben aceptarla de nuevo. */
@Entity
@Table(name = "textos_legales",
        uniqueConstraints = @UniqueConstraint(name = "uk_texto_legal", columnNames = {"tipo", "version"}))
@Getter
@Setter
@NoArgsConstructor
public class TextoLegal {
    public static final String POLITICA_PRIVACIDAD = "POLITICA_PRIVACIDAD";
    public static final String TERMINOS = "TERMINOS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(nullable = false)
    private LocalDateTime vigenteDesde;
}
