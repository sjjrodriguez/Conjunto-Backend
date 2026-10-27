package com.residencial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cuota de administración de un mes; el comprobante se pide aparte (enlace firmado). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CuotaDTO {
    private Long id;
    private Long apartamentoId;
    private String torre;
    private String apto;
    private String periodo;        // "2026-11"
    private BigDecimal valor;
    private LocalDate fechaLimite;
    private String estado;         // PENDIENTE, EN_REVISION, PAGADA o RECHAZADA
    private Boolean vencida;       // sin pagar y pasó la fecha límite
    private Boolean tieneComprobante;
    private String comprobanteTipo;
    private LocalDateTime comprobanteSubidoEn;
    private String motivoRechazo;
    private LocalDateTime pagadaEn;
    // P5: el contador registró un pago sin comprobante y espera al administrador
    private Boolean pagoPorAprobar;

    public CuotaDTO(Long id, Long apartamentoId, String torre, String apto, String periodo, BigDecimal valor,
                    LocalDate fechaLimite, String estado, Boolean vencida, Boolean tieneComprobante, String comprobanteTipo,
                    LocalDateTime comprobanteSubidoEn, String motivoRechazo, LocalDateTime pagadaEn) {
        this(id, apartamentoId, torre, apto, periodo, valor, fechaLimite, estado, vencida, tieneComprobante, comprobanteTipo,
                comprobanteSubidoEn, motivoRechazo, pagadaEn, null);
    }

    /** Cuerpo de POST /api/cuotas/generar: la cuota del mes para todos los apartamentos. */
    public record Generar(String periodo, BigDecimal valor, LocalDate fechaLimite) {}

    /** Resultado de generar: cuántas se crearon y cuántos apartamentos ya la tenían. */
    public record Generadas(String periodo, int creadas, int existentes, boolean enAprobacion, Long solicitudId) {
        public Generadas(String periodo, int creadas, int existentes) {
            this(periodo, creadas, existentes, false, null);
        }
    }

    /** P5: facturación o pago sin comprobante que mandó el contador, para que lo apruebe el administrador. */
    public record Solicitud(Long id, String tipo, String estado, String periodo, BigDecimal valor, LocalDate fechaLimite,
                            Long cuotaId, String torre, String apto, String solicitadaPor, LocalDateTime creadaEn,
                            LocalDateTime revisadaEn, String motivoRechazo) {}

    public record RevisionSolicitud(boolean aprobada, String motivo) {}

    /** P5: resumen de la cartera de un mes para la administración. */
    public record Estadisticas(String periodo, int cuotas, int pagadas, int enRevision, int pendientes, int vencidas,
                               int rechazadas, BigDecimal facturado, BigDecimal recaudado, BigDecimal porCobrar,
                               int porcentajeRecaudo, int apartamentos, int apartamentosAlDia, int apartamentosEnMora,
                               BigDecimal carteraTotal, long solicitudesPendientes) {}

    /** PUT /api/cuotas/{id}/estado: PAGADA o RECHAZADA (con motivo). */
    public record Revision(String estado, String motivo) {}

    /** PUT /api/cuotas/{id}: corregir el valor o la fecha límite (los dos opcionales). */
    public record Cambio(BigDecimal valor, LocalDate fechaLimite) {}

    /** Enlace temporal para ver el comprobante. */
    public record Enlace(String url, String tipo, LocalDateTime expiraEn) {}
}
