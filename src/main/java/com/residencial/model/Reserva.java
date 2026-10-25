package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reserva", indexes = {
        @Index(name = "idx_reserva_apartamento", columnList = "apartamento_id"),
        // Búsqueda de choques de horario: misma zona en un rango de fechas
        @Index(name = "idx_reserva_zona_fecha", columnList = "zona_comun_id, fecha")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    // Hora de inicio
    @Column(nullable = false)
    private LocalTime hora;

    @Column(nullable = false)
    private String estado; // "Pendiente", "Aprobada", "Rechazada"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zona_comun_id", nullable = false)
    private ZonaComun zonaComun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id", nullable = false)
    private Apartamento apartamento;

    // Hasta dónde va (P4). Null en las anteriores: duran app.reservas.duracion-horas
    @Column(name = "hora_fin")
    private LocalTime horaFin;

    // Cuántas personas asisten (P4); no puede pasar el aforo de la zona
    private Integer asistentes;

    /** Reserva como antes de P4: sin hora de fin ni asistentes. */
    public Reserva(Long id, LocalDate fecha, LocalTime hora, String estado, ZonaComun zonaComun, Apartamento apartamento) {
        this(id, fecha, hora, estado, zonaComun, apartamento, null, null);
    }
}
