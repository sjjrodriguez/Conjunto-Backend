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

    /** Cuerpo de POST /api/cuotas/generar: la cuota del mes para todos los apartamentos. */
    public record Generar(String periodo, BigDecimal valor, LocalDate fechaLimite) {}

    /** Resultado de generar: cuántas se crearon y cuántos apartamentos ya la tenían. */
    public record Generadas(String periodo, int creadas, int existentes) {}

    /** PUT /api/cuotas/{id}/estado: PAGADA o RECHAZADA (con motivo). */
    public record Revision(String estado, String motivo) {}

    /** Enlace temporal para ver el comprobante. */
    public record Enlace(String url, String tipo, LocalDateTime expiraEn) {}
}
