package com.residencial.controller;

import com.residencial.dto.ApartamentoDTO;
import com.residencial.dto.ApartamentoRequest;
import com.residencial.dto.CambioContrasenaRequest;
import com.residencial.service.ApartamentoService;
import jakarta.validation.Valid;
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
    public List<ApartamentoDTO> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApartamentoDTO> buscarPorId(@PathVariable Long id) {
        ApartamentoDTO a = service.buscarPorId(id);
        return a != null ? ResponseEntity.ok(a) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<ApartamentoDTO> guardar(@RequestBody ApartamentoRequest apartamento) {
        return ResponseEntity.ok(service.guardar(apartamento));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApartamentoDTO> actualizar(@PathVariable Long id, @RequestBody ApartamentoRequest apartamento) {
        ApartamentoDTO updated = service.actualizar(id, apartamento);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    public record Citofonos(String citofono1, String citofono2) {}

    /** Los dos teléfonos del citófono: los cambia quien vive en el apartamento (o el administrador). */
    @PutMapping("/{id}/citofonos")
    public ApartamentoDTO cambiarCitofonos(@PathVariable Long id, @RequestBody Citofonos citofonos) {
        return service.cambiarCitofonos(id, citofonos.citofono1(), citofonos.citofono2());
    }

    @PutMapping("/{id}/contrasena")
    public ResponseEntity<Void> cambiarContrasena(@PathVariable Long id,
                                                  @Valid @RequestBody CambioContrasenaRequest request) {
        return service.cambiarContrasena(id, request.getNuevaContrasena())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
