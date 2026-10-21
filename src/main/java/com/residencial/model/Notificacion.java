package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Aviso para el administrador cuando un propietario reserva o registra un vehículo. */
@Entity
@Table(name = "notificacion")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tipo; // "RESERVA" o "VEHICULO"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    // Hora de Colombia (app.zona-horaria)
    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false)
    private Boolean leida;
}
