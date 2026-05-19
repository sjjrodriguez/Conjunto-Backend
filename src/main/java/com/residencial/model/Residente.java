package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "residente")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Residente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombreCompleto;

    private String telefono;

    @Column(nullable = false)
    private String identificacion;

    @Column(nullable = false)
    private String tipoHabitante; // "PROPIETARIO" o "ARRENDATARIO"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id", nullable = false)
    private Apartamento apartamento;
}
