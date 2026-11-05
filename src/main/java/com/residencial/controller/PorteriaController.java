package com.residencial.controller;

import com.residencial.service.PorteriaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Visitas de la portería (P6 bloque 5). Las rutas de la portería y las del residente se separan en SecurityConfig. */
@RestController
@RequestMapping("/api/porteria")
public class PorteriaController {

    public record Respuesta(String decision) {}

    private final PorteriaService servicio;

    public PorteriaController(PorteriaService servicio) {
        this.servicio = servicio;
    }

    /** Portería y administración: registra una visita y avisa al apartamento. */
    @PostMapping("/visitas")
    public ResponseEntity<PorteriaService.Visita> registrar(@RequestBody PorteriaService.Nueva cuerpo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(cuerpo));
    }

    /** Portería y administración: las visitas de las últimas 24 horas (la app consulta cada 7 segundos). */
    @GetMapping("/visitas")
    public List<PorteriaService.Visita> recientes() {
        return servicio.recientes();
    }

    /** Residentes: las visitas que esperan respuesta en su apartamento. */
    @GetMapping("/visitas/pendientes")
    public List<PorteriaService.Visita> pendientes() {
        return servicio.pendientesDelApartamento();
    }

    /** Residente de ese apartamento (o la portería, si contestaron por teléfono): AUTORIZAR o RECHAZAR. */
    @PatchMapping("/visitas/{id}/respuesta")
    public PorteriaService.Visita responder(@PathVariable Long id, @RequestBody Respuesta cuerpo) {
        return servicio.responder(id, cuerpo.decision());
    }

    /** P8 bloque 4: portería y administración: el visitante salió (libera su parqueadero de invitados). */
    @PostMapping("/visitas/{id}/salida")
    public PorteriaService.Visita salida(@PathVariable Long id) {
        return servicio.registrarSalida(id);
    }

    /** P8 bloque 4: portería y administración: los parqueaderos de invitados, libres u ocupados. */
    @GetMapping("/parqueaderos")
    public List<PorteriaService.ParqueaderoInvitados> parqueaderos() {
        return servicio.parqueaderosInvitados();
    }
}
