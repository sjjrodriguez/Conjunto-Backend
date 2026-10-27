package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "administrador")
@Data
@NoArgsConstructor
public class Administrador {

    public static final String ADMIN = "ADMIN";
    public static final String CONTADOR = "CONTADOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String usuario;

    @Column(nullable = false)
    private String contrasena;

    // ADMIN (todo) o CONTADOR (solo cartera y facturación), P4 bloque 7
    @Column(nullable = false, length = 20)
    private String rol = ADMIN;

    // P5 bloque 7: el principal maneja el equipo; los demás ADMIN son asistentes
    @Column(nullable = false)
    private boolean principal;

    @Column(name = "nombre_completo", length = 150)
    private String nombreCompleto;

    @Column(length = 30)
    private String documento;

    @Column(length = 30)
    private String telefono;

    @Column(length = 150)
    private String correo;

    public Administrador(Long id, String usuario, String contrasena) {
        this(id, usuario, contrasena, ADMIN);
    }

    public Administrador(Long id, String usuario, String contrasena, String rol) {
        this.id = id;
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.rol = rol;
    }
}
