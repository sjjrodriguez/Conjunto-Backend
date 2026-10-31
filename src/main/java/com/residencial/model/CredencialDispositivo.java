package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Credencial opaca de un celular para entrar con biometría; solo se guarda su SHA-256 (P6 bloque 4). */
@Entity
@Table(name = "credenciales_dispositivo",
        uniqueConstraints = @UniqueConstraint(name = "uk_credencial_dispositivo", columnNames = {"sujeto", "dispositivo_id", "tipo_app"}),
        indexes = @Index(name = "idx_credencial_hash", columnList = "hash_credencial"))
@Getter
@Setter
@NoArgsConstructor
public class CredencialDispositivo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String sujeto;

    @Column(name = "dispositivo_id", nullable = false, length = 64)
    private String dispositivoId;

    @Column(name = "tipo_app", nullable = false, length = 20)
    private String tipoApp;

    @Column(name = "hash_credencial", nullable = false, length = 64)
    private String hashCredencial;

    @Column(nullable = false)
    private LocalDateTime creadaEn;

    private LocalDateTime ultimoUso;
}
