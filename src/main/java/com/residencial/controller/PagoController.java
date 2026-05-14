package com.residencial.controller;

import com.residencial.dto.CarteraResponse;
import com.residencial.dto.PagoRequest;
import com.residencial.dto.PagoResponse;
import com.residencial.service.PagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Registro y consulta de historial de pagos y cartera")
public class PagoController {

    private final PagoService pagoService;

    @PostMapping
    @Operation(summary = "Registrar un nuevo pago",
               description = "Registra la cuota de administración de un apartamento para un mes y año específico")
    public ResponseEntity<PagoResponse> registrarPago(@RequestBody PagoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoService.registrarPago(request));
    }

    @GetMapping("/pendientes/{idApartamento}")
    @Operation(summary = "Consultar cartera pendiente",
               description = "Retorna el saldo y estado de cartera de un apartamento")
    public ResponseEntity<CarteraResponse> obtenerCartera(
            @Parameter(description = "ID del apartamento") @PathVariable long idApartamento) {
        return ResponseEntity.ok(pagoService.obtenerCartera(idApartamento));
    }
}
