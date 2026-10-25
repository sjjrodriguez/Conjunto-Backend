package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehiculo", indexes = {
        @Index(name = "idx_vehiculo_apartamento", columnList = "apartamento_id"),
        @Index(name = "idx_vehiculo_estado", columnList = "estado")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vehiculo {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Obligatoria solo para carros y motos (VehiculoServiceImpl); bicicletas y patinetas no tienen
    private String placa;

    private String marca;
    private String color;

    @Column(nullable = false)
    private String tipo; // Catalogo.TIPO_VEHICULO

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id", nullable = false)
    private Apartamento apartamento;

    // PENDIENTE hasta que el administrador lo apruebe o rechace (los que registra él, APROBADO)
    @Column(nullable = false, length = 20)
    private String estado = APROBADO;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    /** Vehículo aprobado (datos de ejemplo y registros del administrador). */
    public Vehiculo(Long id, String placa, String marca, String color, String tipo, Apartamento apartamento) {
        this(id, placa, marca, color, tipo, apartamento, APROBADO, null);
    }
}
