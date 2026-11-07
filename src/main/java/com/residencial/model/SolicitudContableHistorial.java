package com.residencial.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** P11: cada paso de una solicitud del contador (enviada, corregida, editada, aprobada o rechazada). */
@Entity
@Table(name = "solicitud_contable_historial")
@Data
@NoArgsConstructor
public class SolicitudContableHistorial extends EntidadConjunto {

    public static final String ENVIADA = "ENVIADA";
    public static final String CORREGIDA = "CORREGIDA";
    public static final String EDITADA = "EDITADA";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitud_id", nullable = false)
    private Long solicitudId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 20)
    private String accion;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    @Column(length = 100)
    private String autor;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    public SolicitudContableHistorial(Long solicitudId, int version, String accion, String detalle, String motivo,
                                      String autor, LocalDateTime creadoEn) {
        this.solicitudId = solicitudId;
        this.version = version;
        this.accion = accion;
        this.detalle = detalle;
        this.motivo = motivo;
        this.autor = autor;
        this.creadoEn = creadoEn;
    }
}
