package com.residencial.controller;

import com.residencial.dto.NotificacionDTO;
import com.residencial.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** Las de la sesión: el administrador ve las suyas y cada residente las de su apartamento. */
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

    // Para el contador del panel: no hace falta descargar la lista completa
    @GetMapping("/no-leidas")
    public Map<String, Long> contarNoLeidas() {
        return Map.of("total", service.contarNoLeidas());
    }

    @PutMapping("/leidas")
    public ResponseEntity<Void> marcarTodasLeidas() {
        service.marcarTodasLeidas();
        return ResponseEntity.noContent().build();
    }

    // Al abrir una notificación (en la lista o desde el aviso del celular)
    @PutMapping("/{id}/leida")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id) {
        service.marcarLeida(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> eliminarTodas() {
        service.eliminarTodas();
        return ResponseEntity.noContent().build();
    }
}
