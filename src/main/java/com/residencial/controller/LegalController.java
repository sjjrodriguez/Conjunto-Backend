package com.residencial.controller;

import com.residencial.security.AccesoService;
import com.residencial.service.LegalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Textos legales, consentimientos y solicitudes de datos personales (P6 bloque 4). */
@RestController
@RequestMapping("/api")
public class LegalController {

    public record Aceptacion(List<Long> textoIds) {}
    public record NuevaSolicitud(String tipo, String detalle) {}
    public record Resolucion(String estado, String respuesta) {}

    private final LegalService servicio;
    private final AccesoService acceso;

    public LegalController(LegalService servicio, AccesoService acceso) {
        this.servicio = servicio;
        this.acceso = acceso;
    }

    /** Públicos: también se leen antes de registrarse. */
    @GetMapping("/legal/textos")
    public List<LegalService.Texto> textos() {
        return servicio.vigentes();
    }

    @GetMapping("/legal/pendientes")
    public List<LegalService.Texto> pendientes() {
        return servicio.pendientes(sujeto());
    }

    @PostMapping("/legal/consentimientos")
    public ResponseEntity<Void> aceptar(@RequestBody Aceptacion cuerpo, HttpServletRequest http) {
        servicio.aceptar(sujeto(), cuerpo.textoIds(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/solicitudes-datos")
    public ResponseEntity<LegalService.Solicitud> solicitar(@RequestBody NuevaSolicitud cuerpo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.solicitar(sujeto(), cuerpo.tipo(), cuerpo.detalle()));
    }

    @GetMapping("/solicitudes-datos/mias")
    public List<LegalService.Solicitud> mias() {
        return servicio.mias(sujeto());
    }

    /** Administración: todas las solicitudes. */
    @GetMapping("/solicitudes-datos")
    public List<LegalService.Solicitud> todas() {
        return servicio.todas();
    }

    @PatchMapping("/solicitudes-datos/{id}")
    public LegalService.Solicitud resolver(@PathVariable Long id, @RequestBody Resolucion cuerpo) {
        return servicio.resolver(id, cuerpo.estado(), cuerpo.respuesta(), sujeto());
    }

    private String sujeto() {
        return acceso.tokenActual().map(t -> t.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión"));
    }
}
