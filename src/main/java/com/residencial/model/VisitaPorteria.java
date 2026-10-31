package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Visita que la portería registra y el apartamento autoriza o rechaza. Si nadie responde en
 * {@link #MINUTOS_VIGENCIA} minutos queda EXPIRADA (P6 bloque 5).
 */
@Entity
@Table(name = "visita_porteria", indexes = {
        @Index(name = "idx_visita_apartamento_estado", columnList = "apartamento_id, estado"),
        @Index(name = "idx_visita_estado_vence", columnList = "estado, vence_en")})
@Getter
@Setter
@NoArgsConstructor
public class VisitaPorteria {
    public static final int MINUTOS_VIGENCIA = 2;
    public static final String PENDIENTE = "PENDIENTE";
    public static final String AUTORIZADA = "AUTORIZADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String EXPIRADA = "EXPIRADA";
    public static final String CANAL_APP = "APP";
    public static final String CANAL_LLAMADA = "LLAMADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apartamento_id", nullable = false, foreignKey = @ForeignKey(name = "fk_visita_apartamento"))
    private Apartamento apartamento;

    @Column(name = "visitante_nombre", nullable = false, length = 150)
    private String visitanteNombre;

    @Column(name = "visitante_documento", length = 40)
    private String visitanteDocumento;

    @Column(length = 20)
    private String placa;

    @Column(length = 200)
    private String motivo;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "vence_en", nullable = false)
    private LocalDateTime venceEn;

    @Column(name = "respondida_en")
    private LocalDateTime respondidaEn;

    @Column(name = "respondida_por", length = 60)
    private String respondidaPor;

    @Column(name = "canal_respuesta", length = 20)
    private String canalRespuesta;

    @Column(name = "registrada_por", length = 60)
    private String registradaPor;
}
