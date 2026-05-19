package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pago")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double saldoPendiente;

    @Column(nullable = false)
    private String estadoCuenta; // "Al Día", "En Mora"

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id", nullable = false, unique = true)
    private Apartamento apartamento;
}
