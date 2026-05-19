package com.residencial.controller;

import com.residencial.dto.VehiculoDTO;
import com.residencial.service.VehiculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/vehiculos")
@RequiredArgsConstructor
public class VehiculoController {

    private final VehiculoService service;

    @GetMapping("/apartamento/{apartamentoId}")
    public List<VehiculoDTO> listarPorApartamento(@PathVariable Long apartamentoId) {
        return service.listarPorApartamento(apartamentoId);
    }

    @PostMapping
    public ResponseEntity<VehiculoDTO> guardar(@RequestBody VehiculoDTO dto) {
        return ResponseEntity.ok(service.guardar(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
