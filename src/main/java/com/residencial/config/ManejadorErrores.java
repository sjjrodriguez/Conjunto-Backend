package com.residencial.config;

import com.residencial.dto.MensajeError;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Respuestas de error con el formato uniforme {"mensaje": "..."}. */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<MensajeError> sinPermiso(AccessDeniedException e) {
        return ResponseEntity.status(403).body(new MensajeError("No tienes permiso para realizar esta acción"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<MensajeError> conEstado(ResponseStatusException e) {
        String mensaje = e.getReason() != null ? e.getReason() : e.getStatusCode().toString();
        return ResponseEntity.status(e.getStatusCode()).body(new MensajeError(mensaje));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MensajeError> datosInvalidos(MethodArgumentNotValidException e) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("Datos inválidos");
        return ResponseEntity.badRequest().body(new MensajeError(mensaje));
    }
}
