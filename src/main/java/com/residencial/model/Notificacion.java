package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Aviso para el administrador (un residente reservó o registró un vehículo) o, desde P4, para
 * los residentes de un apartamento (su vehículo o su reserva fue aprobado o rechazado).
 * [referenciaId] y [apartamentoId] dicen a qué pantalla lleva al tocarlo.
 */
@Entity
@Table(name = "notificacion",
        indexes = @Index(name = "idx_notificacion_destinatario", columnList = "destinatario_apartamento_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tipo; // RESERVA, VEHICULO, VEHICULO_APROBADO, RESERVA_RECHAZADA...

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

    // Apartamento al que se refiere el aviso
    @Column(name = "apartamento_id")
    private Long apartamentoId;

    // A quién va: null = administrador; un id = los residentes de ese apartamento
    @Column(name = "destinatario_apartamento_id")
    private Long destinatarioApartamentoId;

    /** Aviso para el administrador. */
    public Notificacion(Long id, String tipo, String mensaje, LocalDateTime fecha, Boolean leida,
                        Long referenciaId, Long apartamentoId) {
        this(id, tipo, mensaje, fecha, leida, referenciaId, apartamentoId, null);
    }
}
