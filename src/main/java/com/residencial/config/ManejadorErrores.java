package com.residencial.config;

import com.residencial.dto.MensajeError;
import com.residencial.exception.RecursoNoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Respuestas de error con el formato uniforme {"mensaje": "..."}. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<MensajeError> sinPermiso(AccessDeniedException e) {
        return ResponseEntity.status(403).body(new MensajeError("No tienes permiso para realizar esta acción"));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<MensajeError> noEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(404).body(new MensajeError(e.getMessage()));
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

    // Cuerpo vacío o JSON mal formado (por ejemplo, una fecha con formato incorrecto)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MensajeError> cuerpoIlegible(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new MensajeError("El cuerpo de la petición falta o no es válido"));
    }

    // Por ejemplo /api/obras/abc cuando se espera un número
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<MensajeError> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new MensajeError("Parámetro inválido: " + e.getName()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<MensajeError> integridad(DataIntegrityViolationException e, HttpServletRequest request) {
        String mensaje = HttpMethod.DELETE.matches(request.getMethod())
                ? "No se puede eliminar: tiene registros asociados"
                : "Los datos entran en conflicto con registros existentes o les falta información obligatoria";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MensajeError(mensaje));
    }

    /**
     * Último recurso: las excepciones propias de Spring (ruta inexistente, método no
     * permitido...) conservan su código; cualquier otra es un 500 sin detalles internos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<MensajeError> inesperado(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse er) {
            String mensaje = er.getBody().getDetail() != null ? er.getBody().getDetail() : er.getStatusCode().toString();
            return ResponseEntity.status(er.getStatusCode()).body(new MensajeError(mensaje));
        }
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MensajeError("Error interno del servidor"));
    }
}
