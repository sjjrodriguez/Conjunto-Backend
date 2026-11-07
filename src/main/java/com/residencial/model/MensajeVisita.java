package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** P10 bloque 7: mensaje del chat temporal entre el residente y la portería sobre una visita rechazada. */
@Entity
@Table(name = "mensaje_visita", indexes = @Index(name = "idx_mensaje_visita_visita", columnList = "visita_id, creado_en"))
@Getter
@Setter
@NoArgsConstructor
public class MensajeVisita extends EntidadConjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visita_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mensaje_visita_visita"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private VisitaPorteria visita;

    @Column(name = "de_residente", nullable = false)
    private boolean deResidente;

    @Column(length = 150)
    private String autor;

    @Column(nullable = false, length = 500)
    private String texto;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
