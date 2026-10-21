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
}
