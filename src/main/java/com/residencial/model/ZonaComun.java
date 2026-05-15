package com.residencial.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "zona_comun")
@Getter // Cambiado de @Data para evitar recursión en toString/equals
@Setter
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

    // Usamos @JsonIgnore porque para el listado de Android
    // no necesitamos que nos devuelva toda la info del Conjunto.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conjunto_id", nullable = false)
    private Conjunto conjunto;

    // Esto permite que cuando consultes una zona, veas sus reservas
    // pero evita que la reserva intente volver a pintar la zona.
    @JsonManagedReference
    @OneToMany(mappedBy = "zonaComun", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Reserva> reservas;
}