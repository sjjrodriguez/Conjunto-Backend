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
public class VisitaPorteria extends EntidadConjunto {
    // P10: 5 minutos para responder (antes 2): da tiempo de llamar a la portería si hay dudas
    public static final int MINUTOS_VIGENCIA = 5;
    public static final String PENDIENTE = "PENDIENTE";
    public static final String AUTORIZADA = "AUTORIZADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String EXPIRADA = "EXPIRADA";
    public static final String CANAL_APP = "APP";
    public static final String CANAL_LLAMADA = "LLAMADA";
    /** P11: entró sin preguntar porque es un visitante frecuente del apartamento. */
    public static final String CANAL_FRECUENTE = "FRECUENTE";
    // P8 bloque 4: cómo entra el visitante
    public static final String PEATONAL = "PEATONAL";
    public static final String VEHICULO = "VEHICULO";

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

    // P7 bloque 4: el guardia que tenía el turno en la recepción (copia del nombre: el historial no cambia)
    @Column(name = "guardia_nombre", length = 150)
    private String guardiaNombre;

    // P8 bloque 4: a pie o en vehículo; con vehículo, el parqueadero de invitados que ocupa hasta la salida
    @Column(name = "tipo_ingreso", nullable = false, length = 20)
    private String tipoIngreso = PEATONAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parqueadero_id", foreignKey = @ForeignKey(name = "fk_visita_parqueadero"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private Parqueadero parqueadero;

    @Column(name = "salida_en")
    private LocalDateTime salidaEn;

    /** P12: quién dio la salida: la portería o el residente desde su app (si el vigilante lo olvidó). */
    public static final String SALIDA_PORTERIA = "PORTERIA";
    public static final String SALIDA_RESIDENTE = "RESIDENTE";

    @Column(name = "salida_por", length = 20)
    private String salidaPor;

    /** P12: la última vez que se le preguntó al residente si el visitante sigue adentro. */
    @Column(name = "recordatorio_en")
    private LocalDateTime recordatorioEn;

    // P10 bloque 7: foto de la cara que tomó la portería (ruta en el bucket privado)
    @Column(name = "foto_ruta", length = 200)
    private String fotoRuta;

    /** P10 bloque 7: si la rechazan, el residente y la portería pueden escribirse durante este tiempo. */
    public static final int MINUTOS_CHAT = 30;

    /** Hasta cuándo está abierto el chat de la visita rechazada (null si no aplica). */
    public LocalDateTime chatHasta() {
        return RECHAZADA.equals(estado) && respondidaEn != null ? respondidaEn.plusMinutes(MINUTOS_CHAT) : null;
    }

    /** Sigue dentro del conjunto: autorizada (o esperando respuesta) y sin salida registrada. */
    public boolean dentro() {
        return salidaEn == null && (PENDIENTE.equals(estado) || AUTORIZADA.equals(estado));
    }
}
