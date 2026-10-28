package com.residencial.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.residencial.service.WompiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Pago en línea de una cuota con Wompi (P6 bloque 6). */
@RestController
@RequestMapping("/api/pagos")
public class PagoCheckoutController {

    public record SolicitudCheckout(Long cuotaId) {}

    private final WompiService wompi;

    public PagoCheckoutController(WompiService wompi) {
        this.wompi = wompi;
    }

    /** El residente del apartamento (con permiso de pagar) pide la dirección firmada del checkout. */
    @PostMapping("/checkout")
    public WompiService.Checkout checkout(@RequestBody SolicitudCheckout cuerpo) {
        if (cuerpo == null || cuerpo.cuotaId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la cuota");
        return wompi.crearCheckout(cuerpo.cuotaId());
    }

    /** Público: lo llama Wompi. La firma del evento hace de permiso. */
    @PostMapping("/webhook/wompi")
    public ResponseEntity<Void> webhook(@RequestBody JsonNode evento,
                                        @RequestHeader(value = "X-Event-Checksum", required = false) String checksum) {
        return wompi.procesarEvento(evento, checksum) ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
