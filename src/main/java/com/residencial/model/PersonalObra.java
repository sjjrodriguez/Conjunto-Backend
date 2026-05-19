package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "personal_obra")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonalObra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombreTrabajador;

    @Column(nullable = false)
    private String cedula;

    @Column(nullable = false)
    private Boolean arlEstado; // true = ARL vigente, false = sin ARL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obra_id", nullable = false)
    private Obra obra;
}
