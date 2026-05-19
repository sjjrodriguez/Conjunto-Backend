package com.residencial.controller;

import com.residencial.dto.ResidenteDTO;
import com.residencial.service.ResidenteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/residentes")
@RequiredArgsConstructor
public class ResidenteController {

    private final ResidenteService service;

    @GetMapping("/apartamento/{apartamentoId}")
    public List<ResidenteDTO> listarPorApartamento(@PathVariable Long apartamentoId) {
        return service.listarPorApartamento(apartamentoId);
    }

    @PostMapping
    public ResponseEntity<ResidenteDTO> guardar(@RequestBody ResidenteDTO dto) {
        return ResponseEntity.ok(service.guardar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResidenteDTO> actualizar(@PathVariable Long id, @RequestBody ResidenteDTO dto) {
        ResidenteDTO updated = service.actualizar(id, dto);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
