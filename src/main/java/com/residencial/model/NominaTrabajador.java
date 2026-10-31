package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Pago mensual de un trabajador: salario base + bonificaciones - descuentos (P6 bloque 7). */
@Entity
@Table(name = "nomina_trabajador",
        uniqueConstraints = @UniqueConstraint(name = "uk_nomina_trabajador_periodo", columnNames = {"trabajador_id", "periodo"}))
@Getter
@Setter
@NoArgsConstructor
public class NominaTrabajador {
    public static final String GENERADA = "GENERADA";
    public static final String PAGADA = "PAGADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trabajador_id", nullable = false, foreignKey = @ForeignKey(name = "fk_nomina_trabajador"))
    private TrabajadorConjunto trabajador;

    @Column(nullable = false, length = 7)
    private String periodo;

    @Column(name = "salario_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal salarioBase;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal bonificaciones = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentos = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal neto;

    @Column(nullable = false, length = 20)
    private String estado = GENERADA;
}
