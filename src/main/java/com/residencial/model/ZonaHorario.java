package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalTime;

/** Horario de una zona un día de la semana (P5). Sin fila para un día = ese día no abre. */
@Entity
@Table(name = "zona_horario",
        uniqueConstraints = @UniqueConstraint(name = "uk_zona_horario_dia", columnNames = {"zona_id", "dia_semana"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ZonaHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zona_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ZonaComun zona;

    @Column(nullable = false)
    private Integer diaSemana; // 1 = lunes ... 7 = domingo (java.time.DayOfWeek)

    @Column(nullable = false)
    private LocalTime apertura;

    @Column(nullable = false)
    private LocalTime cierre;
}
