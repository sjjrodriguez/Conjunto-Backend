package com.residencial.dto;

import java.util.List;

/** Parqueadero con lo que tiene asignado (P5 bloque 4). estado: LIBRE o ASIGNADO. */
public record ParqueaderoDTO(Long id, String numero, String tipo, String estado,
                             Long vehiculoId, String placa, String vehiculo,
                             Long apartamentoId, String torre, String apto) {

    /** POST /api/parqueaderos/generar: [cantidad] parqueaderos de [tipo] numerados PREFIJO-01, -02... */
    public record Generar(String tipo, Integer cantidad, String prefijo) {}

    /** PUT /api/parqueaderos/{id}/asignacion: el vehículo aprobado que lo usará (null = liberarlo). */
    public record Asignacion(Long vehiculoId) {}

    public record PorTipo(String tipo, long total, long asignados, long libres) {}

    /** Métricas: libres y asignados (sin contar invitados) y cuántos hay para invitados. */
    public record Resumen(long total, long asignados, long libres, long invitados, List<PorTipo> porTipo) {}
}
