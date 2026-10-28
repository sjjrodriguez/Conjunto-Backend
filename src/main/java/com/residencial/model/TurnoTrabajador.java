package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/** Turno de un trabajador en una fecha; los de un mismo trabajador no se cruzan (P6 bloque 7). */
@Entity
@Table(name = "turno_trabajador", indexes = @Index(name = "idx_turno_trabajador_fecha", columnList = "trabajador_id, fecha"))
@Getter
@Setter
@NoArgsConstructor
public class TurnoTrabajador {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trabajador_id", nullable = false, foreignKey = @ForeignKey(name = "fk_turno_trabajador"))
    private TrabajadorConjunto trabajador;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;
}
