package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Chat de la administración con el propietario o el arrendatario de un apartamento (P5 bloque 8). */
@Entity
@Table(name = "conversacion",
        uniqueConstraints = @UniqueConstraint(name = "uk_conversacion", columnNames = {"apartamento_id", "participante"}))
@Getter
@Setter
@NoArgsConstructor
public class Conversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apartamento_id", nullable = false)
    private Apartamento apartamento;

    // PROPIETARIO o ARRENDATARIO
    @Column(nullable = false, length = 20)
    private String participante;

    @Column(name = "ultimo_mensaje", columnDefinition = "TEXT")
    private String ultimoMensaje;

    @Column(name = "ultimo_mensaje_en")
    private LocalDateTime ultimoMensajeEn;

    @Column(name = "no_leidos_admin", nullable = false)
    private int noLeidosAdmin;

    @Column(name = "no_leidos_residente", nullable = false)
    private int noLeidosResidente;

    public Conversacion(Apartamento apartamento, String participante) {
        this.apartamento = apartamento;
        this.participante = participante;
    }
}
