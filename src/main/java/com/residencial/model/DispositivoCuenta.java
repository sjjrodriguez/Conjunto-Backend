package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * P7 bloque 5: un celular de una cuenta ("admin:ID" o "residente:ID"). Queda verificado cuando
 * se confirma el código del correo; [sesion] es la sesión abierta en él (claim sid del token).
 * Cerrar sesión la deja en null; quitar el celular borra la fila y la próxima vez pide código.
 */
@Entity
@Table(name = "dispositivo_cuenta",
        uniqueConstraints = @UniqueConstraint(name = "uk_dispositivo_cuenta", columnNames = {"conjunto_id", "sujeto", "dispositivo"}))
@Getter
@Setter
@NoArgsConstructor
public class DispositivoCuenta extends EntidadConjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String sujeto;

    // El id que la app genera al instalarse (el mismo de la huella)
    @Column(nullable = false, length = 64)
    private String dispositivo;

    // Marca y modelo que manda la app, para reconocerlo en "Mis dispositivos"
    @Column(length = 80)
    private String nombre;

    @Column(name = "tipo_app", length = 20)
    private String tipoApp;

    @Column(name = "verificado_en")
    private LocalDateTime verificadoEn;

    @Column(length = 64, unique = true)
    private String sesion;

    @Column(name = "sesion_desde")
    private LocalDateTime sesionDesde;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
