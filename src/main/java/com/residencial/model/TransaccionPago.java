package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Un intento de pago en línea de una cuota con la pasarela (P6 bloque 6). */
@Entity
@Table(name = "transaccion_pago",
        uniqueConstraints = @UniqueConstraint(name = "uk_transaccion_referencia", columnNames = "referencia"),
        indexes = @Index(name = "idx_transaccion_cuota", columnList = "cuota_id"))
@Getter
@Setter
@NoArgsConstructor
public class TransaccionPago {
    public static final String CREADA = "CREADA";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String ERROR = "ERROR";
    public static final String ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuota_id", nullable = false)
    private Long cuotaId;

    @Column(nullable = false, length = 80)
    private String referencia;

    @Column(name = "monto_centavos", nullable = false)
    private long montoCentavos;

    @Column(nullable = false, length = 3)
    private String moneda = "COP";

    @Column(nullable = false, length = 20)
    private String estado = CREADA;

    @Column(nullable = false, length = 20)
    private String proveedor = "WOMPI";

    @Column(name = "id_proveedor", length = 80)
    private String idProveedor;

    @Column(name = "firma_webhook", length = 128)
    private String firmaWebhook;

    @Column(nullable = false)
    private LocalDateTime creadaEn;

    private LocalDateTime actualizadaEn;
}
