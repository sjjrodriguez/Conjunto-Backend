package com.residencial.dto;

import java.util.List;

/** Parqueadero con lo que tiene asignado (P5 bloque 4). estado: LIBRE o ASIGNADO. */
public record ParqueaderoDTO(Long id, String numero, String tipo, String estado,
                             Long vehiculoId, String placa, String vehiculo,
                             Long apartamentoId, String torre, String apto) {

    /** POST /api/parqueaderos/generar: [cantidad] parqueaderos de [tipo] numerados PREFIJO-01, -02... */
    public record Generar(String tipo, Integer cantidad, String prefijo) {}

    /**
     * PUT /api/parqueaderos/{id}/asignacion. P11: a un apartamento (apartamentoId) y, si se quiere,
     * a uno de sus vehículos (vehiculoId; con él basta). Los dos null = liberarlo.
     */
    public record Asignacion(Long vehiculoId, Long apartamentoId) {}

    public record PorTipo(String tipo, long total, long asignados, long libres) {}

    /** Métricas: libres y asignados (sin contar invitados) y cuántos hay para invitados. */
    public record Resumen(long total, long asignados, long libres, long invitados, List<PorTipo> porTipo) {}
}
