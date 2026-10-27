package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "obra",
        indexes = @Index(name = "idx_obra_apartamento", columnList = "apartamento_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Obra {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String FINALIZADA = "FINALIZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcionAdecuacion;

    @Column(nullable = false)
    private LocalDate fechaInicio;

    @Column(nullable = false)
    private LocalDate fechaFinEstimada;

    @Column(nullable = false)
    private String estadoObra; // "EN_PROCESO" o "FINALIZADA"

    // Null = obra del conjunto (zonas comunes), no de un apartamento (P5)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id")
    private Apartamento apartamento;

    // P5: la administración la aprueba (verifica la ARL del personal) antes de empezar
    @Column(nullable = false, length = 20)
    private String aprobacion = APROBADA;

    @Column(columnDefinition = "TEXT")
    private String motivoRechazo;

    public Obra(Long id, String descripcionAdecuacion, LocalDate fechaInicio, LocalDate fechaFinEstimada,
                String estadoObra, Apartamento apartamento) {
        this(id, descripcionAdecuacion, fechaInicio, fechaFinEstimada, estadoObra, apartamento, APROBADA, null);
    }

    public Long apartamentoId() {
        return apartamento == null ? null : apartamento.getId();
    }
}
