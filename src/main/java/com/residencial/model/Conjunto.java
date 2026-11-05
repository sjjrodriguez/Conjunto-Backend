package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** P7: un conjunto residencial. Todo lo demás (apartamentos, cuotas, avisos...) es de uno de ellos. */
@Entity
@Table(name = "conjunto")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 60)
    private String departamento;

    @Column(nullable = false, length = 80)
    private String municipio;

    @Column(length = 160)
    private String direccion;

    @Column(nullable = false)
    private boolean activo = true;

    // P7 bloque 5: celulares con sesión abierta a la vez por cuenta (lo cambia la plataforma)
    @Column(name = "max_dispositivos", nullable = false)
    private int maxDispositivos = 3;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    // P9 bloque 2: de aquí sale el código diario del conjunto (CodigoConjunto). Nunca sale del servidor
    @Column(name = "codigo_secreto", nullable = false, length = 64)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String codigoSecreto = com.residencial.security.CodigoConjunto.nuevoSecreto();

    public Conjunto(String nombre, String departamento, String municipio, String direccion) {
        this.nombre = nombre;
        this.departamento = departamento;
        this.municipio = municipio;
        this.direccion = direccion;
    }
}
