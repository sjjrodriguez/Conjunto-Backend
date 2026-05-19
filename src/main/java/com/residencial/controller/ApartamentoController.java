package com.residencial.controller;

import com.residencial.model.Apartamento;
import com.residencial.service.ApartamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/apartamentos")
@RequiredArgsConstructor
public class ApartamentoController {

    private final ApartamentoService service;

    @GetMapping
    public List<Apartamento> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Apartamento> buscarPorId(@PathVariable Long id) {
        Apartamento a = service.buscarPorId(id);
        return a != null ? ResponseEntity.ok(a) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<Apartamento> guardar(@RequestBody Apartamento apartamento) {
        return ResponseEntity.ok(service.guardar(apartamento));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Apartamento> actualizar(@PathVariable Long id, @RequestBody Apartamento apartamento) {
        Apartamento updated = service.actualizar(id, apartamento);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
