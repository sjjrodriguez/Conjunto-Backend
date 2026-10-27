package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Visitante frecuente de un apartamento (P5): lo registra el residente y lo consulta la portería. */
@Entity
@Table(name = "visitante_frecuente",
        indexes = @Index(name = "idx_visitante_apartamento", columnList = "apartamento_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisitanteFrecuente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id", nullable = false)
    private Apartamento apartamento;

    @Column(nullable = false)
    private String nombreCompleto;

    @Column(length = 50)
    private String identificacion;

    @Column(length = 50)
    private String telefono;

    @Column(nullable = false)
    private LocalDateTime creadoEn;
}
