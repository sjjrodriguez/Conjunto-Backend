package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "propietario")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Propietario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 150)
    private String nombreCompleto;

    @Column(unique = true, length = 20)
    private String documento;

    @Column(length = 20)
    private String telefono;

    @Column(length = 150)
    private String email;

    @OneToOne(mappedBy = "propietario", fetch = FetchType.LAZY)
    private Apartamento apartamento;
}
