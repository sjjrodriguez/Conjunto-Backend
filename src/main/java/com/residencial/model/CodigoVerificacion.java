package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * P7 bloque 5: código de 6 dígitos enviado por correo para verificar un celular nuevo. La app solo
 * conoce [id] (aleatorio); el código se guarda cifrado con BCrypt.
 */
@Entity
@Table(name = "codigo_verificacion")
@Getter
@Setter
@NoArgsConstructor
public class CodigoVerificacion extends EntidadConjunto {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 40)
    private String sujeto;

    @Column(nullable = false, length = 64)
    private String dispositivo;

    @Column(length = 80)
    private String nombre;

    @Column(name = "tipo_app", length = 20)
    private String tipoApp;

    @Column(nullable = false, length = 100)
    private String codigo;

    @Column(nullable = false)
    private int intentos;

    @Column(nullable = false)
    private int envios = 1;

    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn;

    @Column(nullable = false)
    private LocalDateTime vence;

    @Column(nullable = false)
    private boolean usado;
}
