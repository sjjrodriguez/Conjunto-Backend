package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * P7 bloque 4: quien atiende la recepción. El celular de la recepción entra con la cuenta de
 * portería del conjunto y cada guardia abre su turno con su PIN: así cada visita dice quién la
 * registró. Lo crea la administración; darlo de baja no borra su historial.
 */
@Entity
@Table(name = "guardia_porteria",
        uniqueConstraints = @UniqueConstraint(name = "uk_guardia_documento", columnNames = {"conjunto_id", "documento"}))
@Getter
@Setter
@NoArgsConstructor
public class GuardiaPorteria extends EntidadConjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 30)
    private String documento;

    // BCrypt del PIN (4 a 6 dígitos)
    @Column(nullable = false)
    private String pin;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}
