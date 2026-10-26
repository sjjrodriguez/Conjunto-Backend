package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Parqueadero numerado de una categoría de vehículo, o de invitados (P5 bloque 4). */
@Entity
@Table(name = "parqueadero",
        uniqueConstraints = @UniqueConstraint(name = "uk_parqueadero_numero", columnNames = "numero"),
        indexes = @Index(name = "idx_parqueadero_tipo", columnList = "tipo"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Parqueadero {

    public static final String INVITADOS = "Invitados";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column(nullable = false, length = 40)
    private String tipo;

    // El vehículo aprobado que lo usa (y con él, su apartamento). Null = libre
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_id", unique = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Vehiculo vehiculo;

    public boolean libre() {
        return vehiculo == null;
    }
}
