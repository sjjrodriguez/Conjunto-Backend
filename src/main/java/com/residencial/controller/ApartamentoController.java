package com.residencial.controller;

import com.residencial.dto.ApartamentoDetalleResponse;
import com.residencial.dto.ApartamentoResumenResponse;
import com.residencial.service.ApartamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/apartamentos")
@RequiredArgsConstructor
@Tag(name = "Apartamentos", description = "Consulta de apartamentos por torre y detalle de ocupantes")
public class ApartamentoController {

    private final ApartamentoService apartamentoService;

    @GetMapping("/torre/{idTorre}")
    @Operation(summary = "Listar apartamentos de una torre",
               description = "Retorna todos los apartamentos pertenecientes a la torre indicada")
    public ResponseEntity<List<ApartamentoResumenResponse>> listarPorTorre(
            @Parameter(description = "ID de la torre") @PathVariable long idTorre) {
        return ResponseEntity.ok(apartamentoService.listarPorTorre(idTorre));
    }

    @GetMapping("/{idApartamento}/detalle")
    @Operation(summary = "Perfil del apartamento",
               description = "Retorna propietario y residentes actuales del apartamento")
    public ResponseEntity<ApartamentoDetalleResponse> obtenerDetalle(
            @Parameter(description = "ID del apartamento") @PathVariable long idApartamento) {
        return ResponseEntity.ok(apartamentoService.obtenerDetalle(idApartamento));
    }
}
