package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Persona que trabaja en el conjunto, contratada directo o por una empresa (P6 bloque 7). */
@Entity
@Table(name = "trabajador_conjunto",
        uniqueConstraints = @UniqueConstraint(name = "uk_trabajador_conjunto_documento", columnNames = "documento"),
        indexes = @Index(name = "idx_trabajador_cargo", columnList = "cargo, activo"))
@Getter
@Setter
@NoArgsConstructor
public class TrabajadorConjunto {
    public static final List<String> CARGOS = List.of("VIGILANTE", "ASEO", "JARDINERIA", "MANTENIMIENTO", "ADMINISTRATIVO", "OTRO");
    public static final String DIRECTO = "DIRECTO";
    public static final String EMPRESA = "EMPRESA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(nullable = false, length = 40)
    private String documento;

    @Column(length = 40)
    private String telefono;

    @Column(nullable = false, length = 20)
    private String cargo;

    @Column(nullable = false, length = 20)
    private String vinculo = DIRECTO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", foreignKey = @ForeignKey(name = "fk_trabajador_empresa"))
    private EmpresaPrestadora empresa;

    @Column(name = "salario_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal salarioBase = BigDecimal.ZERO;

    @Column(name = "fecha_ingreso")
    private LocalDate fechaIngreso;

    @Column(nullable = false)
    private boolean activo = true;
}
