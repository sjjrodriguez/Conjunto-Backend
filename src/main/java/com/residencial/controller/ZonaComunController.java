package com.residencial.controller;

import com.residencial.dto.ZonaComunDTO;
import com.residencial.service.ZonaComunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/zonas")
@RequiredArgsConstructor
public class ZonaComunController {

    private final ZonaComunService service;

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
