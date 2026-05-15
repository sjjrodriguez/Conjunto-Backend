package com.residencial.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "apartamento")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Apartamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 20)
    private String numero;

    private int piso;

    // Evita el ciclo infinito hacia Torre en el JSON
    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torre_id", nullable = false)
    private Torre torre;

    @JsonIgnore // Lo ocultamos para el MVP de Android
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propietario_id")
    private Propietario propietario;

    @JsonManagedReference
    @OneToMany(mappedBy = "apartamento", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Residente> residentes;

    @JsonManagedReference
    @OneToMany(mappedBy = "apartamento", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Pago> pagos;

    @JsonManagedReference
    @OneToMany(mappedBy = "apartamento", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Reserva> reservas;
}