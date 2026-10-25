package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "administrador")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    public Administrador(Long id, String usuario, String contrasena) {
        this(id, usuario, contrasena, ADMIN);
    }
}
