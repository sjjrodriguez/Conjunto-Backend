package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "zona_comun")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonaComun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 250)
    private String descripcion;

    @Column(name = "capacidad_maxima")
    private int capacidadMaxima;

    @Column(name = "costo_reserva", precision = 10, scale = 2)
    private BigDecimal costoReserva;

    private boolean disponible;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conjunto_id", nullable = false)
    private Conjunto conjunto;

    @OneToMany(mappedBy = "zonaComun", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Reserva> reservas;
}
