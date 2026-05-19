package com.residencial.controller;

import com.residencial.dto.ObraDTO;
import com.residencial.service.ObraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/obras")
@RequiredArgsConstructor
public class ObraController {

    private final ObraService service;

    @GetMapping("/apartamento/{apartamentoId}")
    public List<ObraDTO> listarPorApartamento(@PathVariable Long apartamentoId) {
        return service.listarPorApartamento(apartamentoId);
    }

    @GetMapping("/en-proceso")
    public List<ObraDTO> listarEnProceso() {
        return service.listarEnProceso();
    }

    @PostMapping
    public ResponseEntity<ObraDTO> guardar(@RequestBody ObraDTO dto) {
        return ResponseEntity.ok(service.guardar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ObraDTO> actualizar(@PathVariable Long id, @RequestBody ObraDTO dto) {
        ObraDTO updated = service.actualizar(id, dto);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
