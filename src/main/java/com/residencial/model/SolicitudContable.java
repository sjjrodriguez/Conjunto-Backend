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
public class SolicitudContable {

    public static final String FACTURACION = "FACTURACION";
    public static final String PAGO_MANUAL = "PAGO_MANUAL";
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
}
