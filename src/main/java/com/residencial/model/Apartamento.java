package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "apartamento")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Apartamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String torre;

    @Column(nullable = false)
    private String apto;

    @Column(nullable = false)
    private String contrasena;

    // P5: teléfonos del citófono (portería). Null = sin configurar
    @Column(length = 30)
    private String citofono1;

    @Column(length = 30)
    private String citofono2;

    public Apartamento(Long id, String torre, String apto, String contrasena) {
        this(id, torre, apto, contrasena, null, null);
    }
}
