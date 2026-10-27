package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mensaje_chat", indexes = @Index(name = "idx_mensaje_conversacion", columnList = "conversacion_id, id"))
@Getter
@Setter
@NoArgsConstructor
public class MensajeChat {

    public static final String ADMINISTRACION = "ADMINISTRACION";
    public static final String RESIDENTE = "RESIDENTE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversacion_id", nullable = false)
    private Conversacion conversacion;

    // ADMINISTRACION o RESIDENTE
    @Column(nullable = false, length = 20)
    private String autor;

    @Column(name = "autor_nombre", nullable = false, length = 150)
    private String autorNombre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn;
}
