package com.residencial.controller;

import com.residencial.service.CorrespondenciaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * P9 bloque 3: correspondencia. Registrar, listar y entregar: portería y administración (regla de
 * /api/porteria/** en SecurityConfig). "mia": los residentes, la de su apartamento.
 */
@RestController
@RequestMapping("/api/porteria/correspondencia")
public class CorrespondenciaController {

    private final CorrespondenciaService servicio;

    public CorrespondenciaController(CorrespondenciaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<CorrespondenciaService.Item> listar(@RequestParam(required = false) String estado,
                                                   @RequestParam(required = false) String q,
                                                   @RequestParam(required = false) Integer dias) {
        return servicio.listar(estado, q, dias);
    }

    @PostMapping
    public ResponseEntity<CorrespondenciaService.Item> registrar(@RequestBody CorrespondenciaService.Nueva cuerpo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(cuerpo));
    }

    @PostMapping("/recibos")
    public ResponseEntity<CorrespondenciaService.Lote> reciboParaTodos(@RequestBody CorrespondenciaService.NuevoReciboParaTodos cuerpo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.reciboParaTodos(cuerpo));
    }

    @PostMapping("/{id}/entrega")
    public CorrespondenciaService.Item entregar(@PathVariable Long id, @RequestBody CorrespondenciaService.Entrega cuerpo) {
        return servicio.entregar(id, cuerpo);
    }

    @GetMapping("/mia")
    public List<CorrespondenciaService.Item> mia() {
        return servicio.delApartamento();
    }
}
