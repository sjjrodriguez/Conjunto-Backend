package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "zona_comun")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZonaComun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String estadoRecibido;

    @Column(columnDefinition = "TEXT")
    private String descripcionEstado;

    @Column(nullable = false)
    private Boolean esReservable;

    // Máximo de personas por reserva (P4); null = sin límite
    private Integer aforo;

    // Horario en que se puede reservar (P4); null = todo el día
    @Column(name = "hora_apertura")
    private LocalTime horaApertura;

    @Column(name = "hora_cierre")
    private LocalTime horaCierre;

    /** Zona como antes de P4: sin aforo ni horario. */
    public ZonaComun(Long id, String nombre, String estadoRecibido, String descripcionEstado, Boolean esReservable) {
        this(id, nombre, estadoRecibido, descripcionEstado, esReservable, null, null, null);
    }
}
