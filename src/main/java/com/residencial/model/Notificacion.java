package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Aviso para el administrador cuando un propietario reserva o registra un vehículo.
 * [referenciaId] y [apartamentoId] dicen a qué pantalla lleva al tocarlo.
 */
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

    // La reserva o el vehículo del aviso (según el tipo); null en avisos anteriores a P4
    @Column(name = "referencia_id")
    private Long referenciaId;

    // Apartamento que originó el aviso
    @Column(name = "apartamento_id")
    private Long apartamentoId;
}
