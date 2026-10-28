package com.residencial.controller;

import com.residencial.service.PersonalConjuntoService;
import com.residencial.service.PersonalConjuntoService.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Personal y presupuesto del conjunto (P6 bloque 7). Todo es del administrador (SecurityConfig);
 * el contador solo lee GET /presupuesto/resumen.
 */
@RestController
@RequestMapping("/api/personal-conjunto")
public class PersonalConjuntoController {

    private final PersonalConjuntoService servicio;

    public PersonalConjuntoController(PersonalConjuntoService servicio) {
        this.servicio = servicio;
    }

    // Empresas
    @GetMapping("/empresas")
    public List<Empresa> empresas() { return servicio.empresas(); }

    @PostMapping("/empresas")
    public ResponseEntity<Empresa> crearEmpresa(@RequestBody Empresa e) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crearEmpresa(e));
    }

    @PutMapping("/empresas/{id}")
    public Empresa actualizarEmpresa(@PathVariable Long id, @RequestBody Empresa e) { return servicio.actualizarEmpresa(id, e); }

    @DeleteMapping("/empresas/{id}")
    public ResponseEntity<Void> eliminarEmpresa(@PathVariable Long id) {
        servicio.eliminarEmpresa(id);
        return ResponseEntity.noContent().build();
    }

    // Trabajadores
    @GetMapping("/trabajadores")
    public List<Trabajador> trabajadores() { return servicio.trabajadores(); }

    @PostMapping("/trabajadores")
    public ResponseEntity<Trabajador> crearTrabajador(@RequestBody Trabajador t) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crearTrabajador(t));
    }

    @PutMapping("/trabajadores/{id}")
    public Trabajador actualizarTrabajador(@PathVariable Long id, @RequestBody Trabajador t) { return servicio.actualizarTrabajador(id, t); }

    // Turnos
    @GetMapping("/turnos")
    public List<Turno> turnos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.turnos(desde, hasta);
    }

    @PostMapping("/turnos")
    public ResponseEntity<Turno> crearTurno(@RequestBody Turno t) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crearTurno(t));
    }

    @DeleteMapping("/turnos/{id}")
    public ResponseEntity<Void> eliminarTurno(@PathVariable Long id) {
        servicio.eliminarTurno(id);
        return ResponseEntity.noContent().build();
    }

    // Nómina
    @GetMapping("/nomina")
    public List<Nomina> nomina(@RequestParam String periodo) { return servicio.nomina(periodo); }

    @PostMapping("/nomina/generar")
    public List<Nomina> generarNomina(@RequestParam String periodo) { return servicio.generarNomina(periodo); }

    @PatchMapping("/nomina/{id}")
    public Nomina ajustarNomina(@PathVariable Long id, @RequestBody CambioNomina c) { return servicio.ajustarNomina(id, c); }

    // Presupuesto
    @GetMapping("/presupuesto")
    public List<LineaPresupuesto> presupuesto(@RequestParam String periodo) { return servicio.presupuesto(periodo); }

    @PutMapping("/presupuesto")
    public List<LineaPresupuesto> fijarPresupuesto(@RequestParam String periodo, @RequestBody List<LineaPresupuesto> lineas) {
        return servicio.fijarPresupuesto(periodo, lineas);
    }

    /** También lo lee el contador. */
    @GetMapping("/presupuesto/resumen")
    public Resumen resumen(@RequestParam String periodo) { return servicio.resumen(periodo); }
}
