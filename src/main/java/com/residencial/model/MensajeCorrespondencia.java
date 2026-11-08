package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** P12 bloque 3: chat temporal entre el residente y la portería cuando rechaza la entrega de su correspondencia. */
@Entity
@Table(name = "mensaje_correspondencia", indexes = @Index(name = "idx_mensaje_corr_correspondencia", columnList = "correspondencia_id, creado_en"))
@Getter
@Setter
@NoArgsConstructor
public class MensajeCorrespondencia extends EntidadConjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "correspondencia_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mensaje_corr_correspondencia"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Correspondencia correspondencia;

    @Column(name = "de_residente", nullable = false)
    private boolean deResidente;

    @Column(length = 150)
    private String autor;

    @Column(nullable = false, length = 500)
    private String texto;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
