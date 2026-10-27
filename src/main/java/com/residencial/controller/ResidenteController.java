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

    public record Revision(String estado, String motivo) {}
    public record Acceso(String contrasena) {}
    public record AprobacionPropietario(boolean aprobado, String motivo) {}

    @GetMapping("/apartamento/{apartamentoId}")
    public List<ResidenteDTO> listarPorApartamento(@PathVariable Long apartamentoId) {
        return service.listarPorApartamento(apartamentoId);
    }

    /** Arrendatarios que agregaron los propietarios y esperan aprobación (administrador). */
    @GetMapping("/pendientes")
    public List<ResidenteDTO> pendientes() {
        return service.pendientes();
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

    @PutMapping("/{id}/estado")
    public ResidenteDTO revisar(@PathVariable Long id, @RequestBody Revision revision) {
        return service.revisar(id, revision.estado(), revision.motivo());
    }

    /** Contraseña para entrar con el documento; vacía le quita el acceso. */
    @PutMapping("/{id}/acceso")
    public ResidenteDTO cambiarAcceso(@PathVariable Long id, @RequestBody Acceso acceso) {
        return service.cambiarAcceso(id, acceso.contrasena());
    }

    /** El arrendatario del apartamento (pendiente o activo); 204 si no tiene. */
    @GetMapping("/apartamento/{apartamentoId}/arrendatario")
    public ResponseEntity<ResidenteDTO> arrendatario(@PathVariable Long apartamentoId) {
        ResidenteDTO a = service.arrendatarioDe(apartamentoId);
        return a == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(a);
    }

    /** El propietario acepta o rechaza la solicitud del arrendatario. */
    @PutMapping("/{id}/aprobacion-propietario")
    public ResidenteDTO aprobarComoPropietario(@PathVariable Long id, @RequestBody AprobacionPropietario aprobacion) {
        return service.aprobarComoPropietario(id, aprobacion.aprobado(), aprobacion.motivo());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
