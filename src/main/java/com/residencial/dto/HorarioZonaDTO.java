package com.residencial.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Disponibilidad configurada de una zona (P5): el horario de cada día de la semana (vacío =
 * rige el horario general de la zona) y las fechas bloqueadas.
 */
public record HorarioZonaDTO(List<Dia> semanal, List<Bloqueo> bloqueos) {

    /** diaSemana: 1 = lunes ... 7 = domingo. */
    public record Dia(Integer diaSemana, LocalTime apertura, LocalTime cierre) {}

    public record Bloqueo(Long id, LocalDate fecha, String motivo) {}

    public record Semanal(List<Dia> semanal) {}
}
