package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Publicación de la administración en el feed de Inicio (P5 bloque 3). */
@Entity
@Table(name = "publicacion", indexes = @Index(name = "idx_publicacion_creada", columnList = "creada_en"))
@Getter
@Setter
@NoArgsConstructor
public class Publicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public static final List<String> CATEGORIAS = List.of("GENERAL", "URGENTE", "MANTENIMIENTO", "EVENTO", "FINANZAS");
    public static final List<String> AUDIENCIAS = List.of("TODOS", "PROPIETARIOS", "ARRENDATARIOS");

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(length = 150)
    private String titulo;

    @Column(nullable = false, length = 20)
    private String categoria = "GENERAL";

    @Column(nullable = false, length = 20)
    private String audiencia = "TODOS";

    // null = todas las torres
    @Column(length = 20)
    private String torre;

    @Column(nullable = false)
    private boolean fijado;

    // Sin llave foránea: si se elimina la cuenta, la publicación conserva el nombre
    private Long autorId;

    @Column(nullable = false)
    private String autorNombre;

    @Column(nullable = false)
    private LocalDateTime creadaEn;

    private LocalDateTime editadaEn;

    @OneToMany(mappedBy = "publicacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden")
    private List<PublicacionAdjunto> adjuntos = new ArrayList<>();
}
