package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** P7 bloque 3: lo que hizo cada operador de la plataforma, para revisarlo después. No se edita ni se borra. */
@Entity
@Table(name = "auditoria_plataforma", indexes = @Index(name = "ix_auditoria_plataforma_conjunto", columnList = "conjunto_id"))
@Getter
@NoArgsConstructor
public class AuditoriaPlataforma {

    public static final String INICIO_SESION = "INICIO_SESION";
    public static final String CREAR_CONJUNTO = "CREAR_CONJUNTO";
    public static final String EDITAR_CONJUNTO = "EDITAR_CONJUNTO";
    public static final String SUSPENDER_CONJUNTO = "SUSPENDER_CONJUNTO";
    public static final String REACTIVAR_CONJUNTO = "REACTIVAR_CONJUNTO";
    public static final String RESTABLECER_PRINCIPAL = "RESTABLECER_PRINCIPAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operador_id", nullable = false)
    private Long operadorId;

    // Copia del usuario: el registro se entiende aunque el operador se dé de baja
    @Column(name = "operador_usuario", nullable = false, length = 60)
    private String operadorUsuario;

    @Column(nullable = false, length = 30)
    private String accion;

    // Sin FK: el registro queda aunque el conjunto ya no exista
    @Column(name = "conjunto_id")
    private Long conjuntoId;

    @Column(length = 500)
    private String detalle;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public AuditoriaPlataforma(OperadorPlataforma operador, String accion, Long conjuntoId, String detalle) {
        this.operadorId = operador.getId();
        this.operadorUsuario = operador.getUsuario();
        this.accion = accion;
        this.conjuntoId = conjuntoId;
        this.detalle = detalle == null || detalle.length() <= 500 ? detalle : detalle.substring(0, 500);
    }
}
