package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * P7 bloque 3: quien maneja la plataforma (crea, edita y suspende conjuntos y su administrador
 * principal). No es de ningún conjunto y no ve los datos de los residentes.
 */
@Entity
@Table(name = "operador_plataforma")
@Data
@NoArgsConstructor
public class OperadorPlataforma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String usuario;

    @Column(nullable = false)
    private String contrasena;

    @Column(name = "nombre_completo", length = 150)
    private String nombreCompleto;

    // Como en Administrador: sube al cerrar sesión o cambiar la contraseña
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    public OperadorPlataforma(String usuario, String contrasena, String nombreCompleto) {
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.nombreCompleto = nombreCompleto;
    }
}
