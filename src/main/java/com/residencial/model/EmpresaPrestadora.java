package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Empresa que presta personal al conjunto (vigilancia, aseo...) (P6 bloque 7). */
@Entity
@Table(name = "empresa_prestadora", uniqueConstraints = @UniqueConstraint(name = "uk_empresa_prestadora_nombre", columnNames = "nombre"))
@Getter
@Setter
@NoArgsConstructor
public class EmpresaPrestadora {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 40)
    private String nit;

    @Column(length = 150)
    private String contacto;

    @Column(length = 40)
    private String telefono;

    @Column(nullable = false)
    private boolean activa = true;
}
