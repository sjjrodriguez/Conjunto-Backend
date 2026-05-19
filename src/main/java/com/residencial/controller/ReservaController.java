package com.residencial.controller;

import com.residencial.dto.ReservaDTO;
import com.residencial.service.ReservaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService service;

    @GetMapping
    public List<ReservaDTO> listarTodas() {
        return service.listarTodas();
    }

    @GetMapping("/apartamento/{apartamentoId}")
    public List<ReservaDTO> listarPorApartamento(@PathVariable Long apartamentoId) {
        return service.listarPorApartamento(apartamentoId);
    }

    @PostMapping
    public ResponseEntity<ReservaDTO> guardar(@RequestBody ReservaDTO dto) {
        return ResponseEntity.ok(service.guardar(dto));
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<ReservaDTO> cambiarEstado(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String nuevoEstado = body.get("estado");
        ReservaDTO updated = service.cambiarEstado(id, nuevoEstado);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
