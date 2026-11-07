package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lo que el contador manda a aprobar (P5 bloque 7): la facturación de un mes (periodo, valor,
 * fecha límite) o un pago sin comprobante (efectivo, oficina) de una cuota.
 */
@Entity
@Table(name = "solicitud_contable")
@Data
@NoArgsConstructor
public class SolicitudContable extends EntidadConjunto {

    public static final String FACTURACION = "FACTURACION";
    public static final String PAGO_MANUAL = "PAGO_MANUAL";
    // P11: lo que el contador cambia en la cartera de un apartamento
    public static final String CUOTA_NUEVA = "CUOTA_NUEVA";
    public static final String CUOTA_CAMBIO = "CUOTA_CAMBIO";
    public static final String CUOTA_ANULAR = "CUOTA_ANULAR";
    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(length = 7)
    private String periodo;

    @Column(precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "fecha_limite")
    private LocalDate fechaLimite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuota_id")
    private Cuota cuota;

    @Column(name = "solicitada_por", nullable = false, length = 100)
    private String solicitadaPor;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "revisada_en")
    private LocalDateTime revisadaEn;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    // --- P11: versiones, edición después de aprobada y el detalle completo (JSON) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id")
    private Apartamento apartamento;

    @Column(nullable = false)
    private int version = 1;

    /** true: el contador editó algo ya aprobado y la edición espera al administrador. */
    @Column(nullable = false)
    private boolean edicion;

    /** Lo que pide esta versión (JSON). */
    @Column(columnDefinition = "TEXT")
    private String detalle;

    /** Lo último que aprobó el administrador (JSON), para comparar una edición. */
    @Column(name = "detalle_aprobado", columnDefinition = "TEXT")
    private String detalleAprobado;

    @Column(length = 500)
    private String nota;

    @Column(name = "revisada_por", length = 100)
    private String revisadaPor;

    @Column(name = "actualizada_en")
    private LocalDateTime actualizadaEn;
}
