package com.residencial.controller;

import com.residencial.dto.ParqueaderoDTO;
import com.residencial.serviceimpl.ParqueaderoServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Parqueaderos del conjunto (P5 bloque 4): solo el administrador. */
@RestController
@RequestMapping("/api/parqueaderos")
@RequiredArgsConstructor
public class ParqueaderoController {

    private final ParqueaderoServiceImpl service;

    /** Filtros opcionales: tipo (Carro, Moto... o Invitados) y estado (LIBRE o ASIGNADO). */
    @GetMapping
    public List<ParqueaderoDTO> listar(@RequestParam(required = false) String tipo,
                                       @RequestParam(required = false) String estado) {
        return service.listar(tipo, estado);
    }

    @GetMapping("/resumen")
    public ParqueaderoDTO.Resumen resumen() {
        return service.resumen();
    }

    @PostMapping("/generar")
    public List<ParqueaderoDTO> generar(@RequestBody ParqueaderoDTO.Generar solicitud) {
        return service.generar(solicitud);
    }

    @PutMapping("/{id}/asignacion")
    public ParqueaderoDTO asignar(@PathVariable Long id, @RequestBody ParqueaderoDTO.Asignacion asignacion) {
        return service.asignar(id, asignacion.vehiculoId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
