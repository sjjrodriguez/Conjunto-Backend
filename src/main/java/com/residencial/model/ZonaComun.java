package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "zona_comun")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZonaComun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String estadoRecibido;

    @Column(columnDefinition = "TEXT")
    private String descripcionEstado;

    @Column(nullable = false)
    private Boolean esReservable;
}