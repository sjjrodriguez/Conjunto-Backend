package com.residencial.controller;

import com.residencial.dto.NotificacionDTO;
import com.residencial.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Solo para el administrador (ver SecurityConfig). */
@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService service;

    // Las 50 más recientes, de la más nueva a la más antigua
    @GetMapping
    public List<NotificacionDTO> listar() {
        return service.listarRecientes();
    }

    @PutMapping("/leidas")
    public ResponseEntity<Void> marcarTodasLeidas() {
        service.marcarTodasLeidas();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> eliminarTodas() {
        service.eliminarTodas();
        return ResponseEntity.noContent().build();
    }
}
