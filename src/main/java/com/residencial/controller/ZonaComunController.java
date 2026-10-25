package com.residencial.controller;

import com.residencial.dto.DisponibilidadDTO;
import com.residencial.dto.ZonaComunDTO;
import com.residencial.service.ReservaService;
import com.residencial.service.ZonaComunService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/zonas")
@RequiredArgsConstructor
public class ZonaComunController {

    private final ZonaComunService service;
    private final ReservaService reservas;

    /**
     * Lo ocupado de la zona día por día entre dos fechas (máximo 62 días), para el calendario
     * de reservas. Las franjas de otros apartamentos no dicen de quién son.
     */
    @GetMapping("/{id}/disponibilidad")
    public DisponibilidadDTO disponibilidad(@PathVariable Long id,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reservas.disponibilidad(id, desde, hasta);
    }

    @GetMapping
    public List<ZonaComunDTO> listarTodas() {
        return service.listarTodas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZonaComunDTO> buscarPorId(@PathVariable Long id) {
        ZonaComunDTO z = service.buscarPorId(id);
        return z != null ? ResponseEntity.ok(z) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<ZonaComunDTO> guardar(@RequestBody ZonaComunDTO dto) {
        return ResponseEntity.ok(service.guardar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ZonaComunDTO> actualizar(@PathVariable Long id, @RequestBody ZonaComunDTO dto) {
        ZonaComunDTO updated = service.actualizar(id, dto);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
