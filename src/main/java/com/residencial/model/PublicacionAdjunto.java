package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

/** Imagen o archivo de una publicación: la ruta en el almacenamiento (se firma al pedirla). */
@Entity
@Table(name = "publicacion_adjunto", indexes = @Index(name = "idx_adjunto_publicacion", columnList = "publicacion_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PublicacionAdjunto {

    public static final String IMAGEN = "IMAGEN";
    public static final String ARCHIVO = "ARCHIVO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publicacion_id", nullable = false)
    private Publicacion publicacion;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 500)
    private String ruta;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, length = 150)
    private String mime;

    @Column(nullable = false)
    private long tamano;

    @Column(nullable = false)
    private int orden;
}
