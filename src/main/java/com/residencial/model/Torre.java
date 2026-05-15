package com.residencial.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "torre")
@Getter
@Setter
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

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conjunto_id", nullable = false)
    private Conjunto conjunto;

    // Le dice a Spring Boot: "Tú mandas en esta lista, inclúyela en el JSON"
    @JsonManagedReference
    @OneToMany(mappedBy = "torre", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Apartamento> apartamentos;
}