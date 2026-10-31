package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "residente",
        indexes = {
                @Index(name = "idx_residente_apartamento", columnList = "apartamento_id"),
                @Index(name = "idx_residente_identificacion", columnList = "identificacion"),
                @Index(name = "idx_residente_titular", columnList = "apartamento_id, titular")
        })
@Data
@NoArgsConstructor
public class Residente {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String PROPIETARIO = "PROPIETARIO";
    public static final String ARRENDATARIO = "ARRENDATARIO";

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

    // Hash BCrypt. Null = no puede entrar a la app
    @ToString.Exclude
    private String contrasena;

    // PENDIENTE: espera la aprobación de la administración
    @Column(nullable = false, length = 20)
    private String estado = APROBADO;

    // P5: titular = el propietario o arrendatario que tiene la cuenta del apartamento
    private String correo;

    @Column(nullable = false)
    private boolean titular;

    private LocalDateTime terminosAceptadosEn;

    // Solo arrendatarios: también los aprueba el propietario. Null = no aplica
    private Boolean aprobadoPropietario;

    // P6: sube al cerrar sesión (ver SesionVersion)
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    public Residente(Long id, String nombreCompleto, String telefono, String identificacion,
                     String tipoHabitante, Apartamento apartamento, String contrasena, String estado,
                     String correo, boolean titular, LocalDateTime terminosAceptadosEn, Boolean aprobadoPropietario) {
        this.id = id;
        this.nombreCompleto = nombreCompleto;
        this.telefono = telefono;
        this.identificacion = identificacion;
        this.tipoHabitante = tipoHabitante;
        this.apartamento = apartamento;
        this.contrasena = contrasena;
        this.estado = estado;
        this.correo = correo;
        this.titular = titular;
        this.terminosAceptadosEn = terminosAceptadosEn;
        this.aprobadoPropietario = aprobadoPropietario;
    }

    public Residente(Long id, String nombreCompleto, String telefono, String identificacion,
                     String tipoHabitante, Apartamento apartamento) {
        this(id, nombreCompleto, telefono, identificacion, tipoHabitante, apartamento, null, APROBADO);
    }

    public Residente(Long id, String nombreCompleto, String telefono, String identificacion,
                     String tipoHabitante, Apartamento apartamento, String contrasena, String estado) {
        this(id, nombreCompleto, telefono, identificacion, tipoHabitante, apartamento, contrasena, estado,
                null, contrasena != null, null, ARRENDATARIO.equals(tipoHabitante) ? Boolean.TRUE : null);
    }

    public boolean esArrendatario() {
        return ARRENDATARIO.equals(tipoHabitante);
    }

    /** Puede iniciar sesión: tiene contraseña, lo aprobó la administración y, si es arrendatario, el propietario. */
    public boolean puedeEntrar() {
        return contrasena != null && APROBADO.equals(estado) && !(esArrendatario() && Boolean.FALSE.equals(aprobadoPropietario));
    }
}
