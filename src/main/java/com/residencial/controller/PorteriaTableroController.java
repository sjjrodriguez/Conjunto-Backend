package com.residencial.controller;

import com.residencial.service.PorteriaService;
import com.residencial.service.PorteriaTableroService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * P8 bloque 5: el tablero de la portería (resumen, quién sigue dentro, historial, directorio y
 * minuta). Portería y administración (regla de /api/porteria/** en SecurityConfig).
 */
@RestController
@RequestMapping("/api/porteria")
public class PorteriaTableroController {

    private final PorteriaTableroService servicio;

    public PorteriaTableroController(PorteriaTableroService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/resumen")
    public PorteriaTableroService.Resumen resumen() {
        return servicio.resumen();
    }

    @GetMapping("/visitas/dentro")
    public List<PorteriaService.Visita> dentro() {
        return servicio.dentro();
    }

    @GetMapping("/visitas/historial")
    public PorteriaTableroService.Pagina historial(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) Integer dias,
                                                   @RequestParam(required = false) Integer pagina) {
        return servicio.historial(q, dias, pagina);
    }

    @GetMapping("/directorio")
    public List<PorteriaTableroService.ApartamentoDirectorio> directorio(@RequestParam(required = false) String q) {
        return servicio.directorio(q);
    }

    @GetMapping("/minuta")
    public List<PorteriaTableroService.Anotacion> minuta(@RequestParam(required = false) Integer dias) {
        return servicio.minuta(dias);
    }

    @PostMapping("/minuta")
    public ResponseEntity<PorteriaTableroService.Anotacion> anotar(@RequestBody PorteriaTableroService.NuevaAnotacion cuerpo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.anotar(cuerpo));
    }
}
