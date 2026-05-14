package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "torre")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Torre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "numero_pisos")
    private int numeroPisos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conjunto_id", nullable = false)
    private Conjunto conjunto;

    @OneToMany(mappedBy = "torre", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Apartamento> apartamentos;
}
