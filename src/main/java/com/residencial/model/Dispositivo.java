package com.residencial.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Celular con sesión iniciada que recibe avisos por Firebase Cloud Messaging. */
@Entity
@Table(name = "dispositivo",
        indexes = @Index(name = "idx_dispositivo_rol", columnList = "rol"),
        uniqueConstraints = @UniqueConstraint(name = "uk_dispositivo_token", columnNames = "token"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Token de Firebase del celular: identifica a dónde enviar el aviso
    @Column(nullable = false, length = 512)
    private String token;

    // ADMIN o PROPIETARIO (TokenService)
    @Column(nullable = false, length = 32)
    private String rol;

    // Solo para residentes: de qué apartamento es la sesión
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id", foreignKey = @ForeignKey(name = "fk_dispositivo_apartamento"))
    private Apartamento apartamento;

    // P6: qué app registró el celular (ADMIN o RESIDENTE) y su applicationId; null = app anterior
    @Column(name = "tipo_app", length = 20)
    private String tipoApp;

    @Column(name = "application_id", length = 100)
    private String applicationId;

    // Hora de Colombia de la última vez que el celular lo registró
    @Column(nullable = false)
    private LocalDateTime actualizado;
}
