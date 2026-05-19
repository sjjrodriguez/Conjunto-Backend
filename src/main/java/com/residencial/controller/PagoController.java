package com.residencial.controller;

import com.residencial.dto.PagoDTO;
import com.residencial.service.PagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService service;

    @GetMapping("/apartamento/{apartamentoId}")
    public ResponseEntity<PagoDTO> consultarPorApartamento(@PathVariable Long apartamentoId) {
        PagoDTO pago = service.consultarPorApartamento(apartamentoId);
        return pago != null ? ResponseEntity.ok(pago) : ResponseEntity.notFound().build();
    }

    @PutMapping("/apartamento/{apartamentoId}/simular")
    public ResponseEntity<?> simularPago(@PathVariable Long apartamentoId) {
        try {
            return ResponseEntity.ok(service.simularPago(apartamentoId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<PagoDTO> guardar(@RequestBody PagoDTO dto) {
        return ResponseEntity.ok(service.guardar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PagoDTO> actualizar(@PathVariable Long id, @RequestBody PagoDTO dto) {
        PagoDTO updated = service.actualizar(id, dto);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }
}
