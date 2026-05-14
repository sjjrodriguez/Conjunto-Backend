package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "residente")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Residente {

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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TipoResidente tipoResidente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id", nullable = false)
    private Apartamento apartamento;

    public enum TipoResidente {
        TITULAR, FAMILIAR, ARRENDATARIO
    }
}
