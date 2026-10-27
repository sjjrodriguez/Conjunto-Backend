package com.residencial.controller;

import com.residencial.dto.EstadoRequest;
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

    /**
     * Administración. Sin filtros, todas (como antes). P5: alcance APARTAMENTOS o CONJUNTO,
     * estado (EN_PROCESO, FINALIZADA), aprobacion (PENDIENTE, APROBADA, RECHAZADA) y q
     * (torre, apto o texto de la descripción).
     */
    @GetMapping
    public List<ObraDTO> listarTodas(@RequestParam(required = false) String alcance,
                                     @RequestParam(required = false) String estado,
                                     @RequestParam(required = false) String aprobacion,
                                     @RequestParam(required = false) String q) {
        if (alcance == null && estado == null && aprobacion == null && q == null) return service.listarTodas();
        return service.buscar(alcance, estado, aprobacion, q);
    }

    @GetMapping("/apartamento/{apartamentoId}")
    public List<ObraDTO> listarPorApartamento(@PathVariable Long apartamentoId) {
        return service.listarPorApartamento(apartamentoId);
    }

    @GetMapping("/{id:[0-9]+}")
    public ObraDTO obtener(@PathVariable Long id) {
        return service.obtener(id);
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

    @PutMapping("/{id}/estado")
    public ResponseEntity<ObraDTO> cambiarEstado(@PathVariable Long id, @RequestBody EstadoRequest dto) {
        ObraDTO updated = service.cambiarEstado(id, dto.getEstado());
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    /** Administración: aprobar (con su personal pendiente) o rechazar con motivo. */
    @PutMapping("/{id}/aprobacion")
    public ObraDTO revisar(@PathVariable Long id, @RequestBody ObraDTO.Revision revision) {
        return service.revisar(id, revision.aprobada(), revision.motivo());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
