package com.residencial.controller;

import com.residencial.dto.LoginResponse;
import com.residencial.service.GuardiaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * P7 bloque 4: guardias de la recepción (los maneja la administración) y el turno que cada uno
 * abre con su PIN en el celular de la recepción. Permisos por ruta en SecurityConfig.
 */
@RestController
@RequestMapping("/api/porteria")
public class GuardiaController {

    private final GuardiaService servicio;

    public GuardiaController(GuardiaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/guardias")
    public List<GuardiaService.Guardia> listar() {
        return servicio.listar();
    }

    @PostMapping("/guardias")
    public ResponseEntity<GuardiaService.Guardia> crear(@RequestBody GuardiaService.Datos datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(datos));
    }

    @PutMapping("/guardias/{id}")
    public GuardiaService.Guardia editar(@PathVariable Long id, @RequestBody GuardiaService.Datos datos) {
        return servicio.editar(id, datos);
    }

    /** La pantalla de bloqueo de la recepción: quiénes pueden abrir turno. */
    @GetMapping("/turno/guardias")
    public List<GuardiaService.EnTurno> paraElTurno() {
        return servicio.paraElTurno();
    }

    /** Abre el turno con el PIN del guardia: devuelve un token nuevo con su id (400 si el PIN no coincide). */
    @PostMapping("/turno")
    public LoginResponse abrirTurno(@RequestBody GuardiaService.AbrirTurno pedido) {
        return servicio.abrirTurno(pedido);
    }
}
