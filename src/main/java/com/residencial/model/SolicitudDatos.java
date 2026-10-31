package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** Petición de un residente sobre sus datos personales (Ley 1581): consultarlos o pedir su supresión. */
@Entity
@Table(name = "solicitudes_datos", indexes = @Index(name = "idx_solicitud_datos_estado", columnList = "estado, creadaEn"))
@Getter
@Setter
@NoArgsConstructor
public class SolicitudDatos {
    public static final List<String> TIPOS = List.of("CONSULTA", "SUPRESION");
    public static final String PENDIENTE = "PENDIENTE";
    public static final String ATENDIDA = "ATENDIDA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String sujeto;

    private String nombre;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(columnDefinition = "TEXT")
    private String respuesta;

    @Column(nullable = false)
    private LocalDateTime creadaEn;

    private LocalDateTime resueltaEn;

    @Column(length = 60)
    private String resueltaPor;
}
