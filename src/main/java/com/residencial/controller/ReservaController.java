package com.residencial.controller;

import com.residencial.dto.ReservaRequest;
import com.residencial.dto.ReservaResponse;
import com.residencial.dto.ZonaComunResponse;
import com.residencial.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reservas y Zonas Comunes", description = "Gestión de zonas comunes y reservas de apartamentos")
public class ReservaController {

    private final ReservaService reservaService;

    @GetMapping("/api/v1/zonas-comunes/disponibles")
    @Operation(summary = "Listar zonas comunes disponibles",
               description = "Retorna todas las zonas comunes habilitadas para reserva en el conjunto")
    public ResponseEntity<List<ZonaComunResponse>> listarZonasDisponibles() {
        return ResponseEntity.ok(reservaService.listarZonasDisponibles());
    }

    @PostMapping("/api/v1/reservas")
    @Operation(summary = "Registrar una reserva",
               description = "Crea una reserva para una zona común validando que no haya conflictos de horario")
    public ResponseEntity<ReservaResponse> registrarReserva(@RequestBody ReservaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.registrarReserva(request));
    }

    @GetMapping("/api/v1/reservas/apartamento/{idApartamento}")
    @Operation(summary = "Historial de reservas por apartamento",
               description = "Lista todas las reservas realizadas por un apartamento")
    public ResponseEntity<List<ReservaResponse>> listarPorApartamento(
            @Parameter(description = "ID del apartamento") @PathVariable long idApartamento) {
        return ResponseEntity.ok(reservaService.listarReservasPorApartamento(idApartamento));
    }
}
