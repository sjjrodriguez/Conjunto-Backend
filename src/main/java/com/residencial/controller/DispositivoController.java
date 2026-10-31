package com.residencial.controller;

import com.residencial.dto.DispositivoRequest;
import com.residencial.service.DispositivoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Celulares que reciben avisos. La app registra su token de Firebase al iniciar sesión (y
 * cuando Firebase lo cambia) y lo da de baja al cerrar sesión. Cualquier usuario con sesión.
 */
@RestController
@RequestMapping("/api/dispositivos")
@RequiredArgsConstructor
public class DispositivoController {

    private final DispositivoService service;

    @PostMapping
    public ResponseEntity<Void> registrar(@Valid @RequestBody DispositivoRequest request,
                                          @org.springframework.web.bind.annotation.RequestHeader(value = "X-App-Tipo", required = false) String app) {
        service.registrar(request.getToken(), request.getTipoApp() != null ? request.getTipoApp() : app,
                request.getApplicationId());
        return ResponseEntity.noContent().build();
    }

    // Al cerrar sesión (ya sin token de sesión). POST y no DELETE: algunos clientes HTTP no
    // envían cuerpo en un DELETE
    @PostMapping("/baja")
    public ResponseEntity<Void> darDeBaja(@Valid @RequestBody DispositivoRequest request) {
        service.darDeBaja(request.getToken());
        return ResponseEntity.noContent().build();
    }
}
