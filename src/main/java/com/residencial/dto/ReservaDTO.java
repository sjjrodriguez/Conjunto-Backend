package com.residencial.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaDTO {
    private Long id;
    private LocalDate fecha;
    private LocalTime hora; // hora de inicio (también se acepta como "horaInicio")
    private String estado;
    private Long zonaComunId;
    private Long apartamentoId;
    // P4: hasta qué hora y cuántas personas. Si no se envía horaFin, dura lo de siempre
    // (app.reservas.duracion-horas); al leerla siempre viene calculada.
    private LocalTime horaFin;
    private Integer asistentes;

    public ReservaDTO(Long id, LocalDate fecha, LocalTime hora, String estado, Long zonaComunId, Long apartamentoId) {
        this(id, fecha, hora, estado, zonaComunId, apartamentoId, null, null);
    }

    /** Nombre más claro para la hora de inicio; es la misma que "hora". */
    @JsonProperty("horaInicio")
    public LocalTime getHoraInicio() {
        return hora;
    }

    @JsonProperty("horaInicio")
    public void setHoraInicio(LocalTime horaInicio) {
        if (horaInicio != null) this.hora = horaInicio;
    }
}
