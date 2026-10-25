package com.residencial.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lo ocupado de una zona día por día, para el calendario de reservas. No dice de quién son
 * las reservas de otros apartamentos: solo la franja y si es del que consulta (propia).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadDTO {
    private Long zonaId;
    private Integer aforo;
    private LocalTime horaApertura;
    private LocalTime horaCierre;
    private List<Dia> dias;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dia {
        private LocalDate fecha;
        private List<Franja> ocupadas;
    }

    /** [inicio, fin) de un día; fin 23:59:59 si la reserva sigue al día siguiente. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Franja {
        private LocalTime inicio;
        private LocalTime fin;
        private String estado; // PENDIENTE o APROBADA
        private Boolean propia;
    }
}
