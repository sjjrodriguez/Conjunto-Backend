package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cuota de administración de un apartamento en un mes (P4 bloque 7). */
@Entity
@Table(name = "cuota",
        uniqueConstraints = @UniqueConstraint(name = "uk_cuota_apartamento_periodo_concepto", columnNames = {"apartamento_id", "periodo", "concepto"}),
        indexes = {
                @Index(name = "idx_cuota_periodo", columnList = "periodo"),
                @Index(name = "idx_cuota_estado", columnList = "estado")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cuota extends EntidadConjunto {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String EN_REVISION = "EN_REVISION";
    public static final String PAGADA = "PAGADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id", nullable = false)
    private Apartamento apartamento;

    @Column(nullable = false, length = 7)
    private String periodo; // "2026-11"

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate fechaLimite;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    // Dónde quedó el archivo en el almacenamiento (no es un enlace: se firma al pedirlo)
    @Column(length = 500)
    private String comprobanteRuta;

    @Column(length = 100)
    private String comprobanteTipo;

    private LocalDateTime comprobanteSubidoEn;

    @Column(columnDefinition = "TEXT")
    private String motivoRechazo;

    private LocalDateTime pagadaEn;

    // --- P11: concepto, referencia de pago fija (Wompi la usa) y la solicitud que la creó ---
    public static final String ADMINISTRACION = "Administración";

    @Column(nullable = false, length = 80)
    private String concepto = ADMINISTRACION;

    @Column(length = 300)
    private String descripcion;

    @Column(name = "referencia_pago", length = 40, unique = true)
    private String referenciaPago = nuevaReferencia();

    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final java.security.SecureRandom AZAR = new java.security.SecureRandom();

    /** "MC-7K2Q9XHB": la referencia con la que se paga esta cuota (sin letras que se confunden). */
    public static String nuevaReferencia() {
        StringBuilder r = new StringBuilder("MC-");
        for (int i = 0; i < 8; i++) r.append(LETRAS.charAt(AZAR.nextInt(LETRAS.length())));
        return r.toString();
    }

    @Column(name = "solicitud_id")
    private Long solicitudId;

    // Pagada en la oficina (efectivo, transferencia...): cómo y con qué soporte
    @Column(name = "medio_pago", length = 30)
    private String medioPago;

    @Column(name = "pago_referencia", length = 80)
    private String pagoReferencia;

    @Column(name = "pago_nota", length = 300)
    private String pagoNota;

    public Cuota(Apartamento apartamento, String periodo, BigDecimal valor, LocalDate fechaLimite) {
        this.apartamento = apartamento;
        this.periodo = periodo;
        this.valor = valor;
        this.fechaLimite = fechaLimite;
    }

    public boolean pagada() {
        return PAGADA.equals(estado);
    }
}
