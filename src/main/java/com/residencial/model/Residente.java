package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "residente",
        indexes = {
                @Index(name = "idx_residente_apartamento", columnList = "apartamento_id"),
                @Index(name = "idx_residente_identificacion", columnList = "identificacion")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Residente {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombreCompleto;

    private String telefono;

    @Column(nullable = false)
    private String identificacion;

    @Column(nullable = false)
    private String tipoHabitante; // "PROPIETARIO" o "ARRENDATARIO"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id", nullable = false)
    private Apartamento apartamento;

    // Hash BCrypt. Null = no puede entrar con su documento
    @ToString.Exclude
    private String contrasena;

    // PENDIENTE: arrendatario que agregó el propietario y aún no aprueba el administrador
    @Column(nullable = false, length = 20)
    private String estado = APROBADO;

    public Residente(Long id, String nombreCompleto, String telefono, String identificacion,
                     String tipoHabitante, Apartamento apartamento) {
        this(id, nombreCompleto, telefono, identificacion, tipoHabitante, apartamento, null, APROBADO);
    }
}
