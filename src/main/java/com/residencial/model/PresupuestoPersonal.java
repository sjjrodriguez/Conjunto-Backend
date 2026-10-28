package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Lo que se presupuesta para el personal de un cargo en un mes (P6 bloque 7). */
@Entity
@Table(name = "presupuesto_personal",
        uniqueConstraints = @UniqueConstraint(name = "uk_presupuesto_periodo_cargo", columnNames = {"periodo", "cargo"}))
@Getter
@Setter
@NoArgsConstructor
public class PresupuestoPersonal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 7)
    private String periodo;

    @Column(nullable = false, length = 20)
    private String cargo;

    @Column(name = "monto_presupuestado", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoPresupuestado;
}
