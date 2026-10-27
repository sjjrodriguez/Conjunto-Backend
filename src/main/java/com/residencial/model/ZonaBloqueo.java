package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;

/** Fecha en que una zona no se puede reservar (mantenimiento, evento del conjunto...). */
@Entity
@Table(name = "zona_bloqueo",
        uniqueConstraints = @UniqueConstraint(name = "uk_zona_bloqueo_fecha", columnNames = {"zona_id", "fecha"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ZonaBloqueo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zona_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ZonaComun zona;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 200)
    private String motivo;
}
