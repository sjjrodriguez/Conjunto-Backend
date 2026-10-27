package com.residencial.controller;

import com.residencial.dto.CupoVehiculoDTO;
import com.residencial.dto.EstadoVehiculoRequest;
import com.residencial.dto.VehiculoDTO;
import com.residencial.service.VehiculoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/vehiculos")
@RequiredArgsConstructor
public class VehiculoController {

    private final VehiculoService service;

    @GetMapping("/apartamento/{apartamentoId}")
    public List<VehiculoDTO> listarPorApartamento(@PathVariable Long apartamentoId) {
        return service.listarPorApartamento(apartamentoId);
    }

    @PostMapping
    public ResponseEntity<VehiculoDTO> guardar(@RequestBody VehiculoDTO dto) {
        return ResponseEntity.ok(service.guardar(dto));
    }

    @PutMapping("/{id}")
    public VehiculoDTO actualizar(@PathVariable Long id, @RequestBody VehiculoDTO dto) {
        return service.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // Administrador: los vehículos en un estado (por defecto, los que esperan aprobación)
    @GetMapping
    public List<VehiculoDTO> listarPorEstado(@RequestParam(defaultValue = "PENDIENTE") String estado) {
        return service.listarPorEstado(estado);
    }

    // Administrador: aprobar o rechazar (con motivo opcional)
    @PutMapping("/{id}/estado")
    public VehiculoDTO cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoVehiculoRequest request) {
        return service.cambiarEstado(id, request.getEstado(), request.getMotivo(), request.getParqueaderoId());
    }

    // Cuántos vehículos de cada tipo puede tener un apartamento (lo ven todos; lo edita el admin)
    // Con apartamentoId: los que rigen para ese apartamento (los suyos o los generales)
    @GetMapping("/cupos")
    public List<CupoVehiculoDTO> cupos(@RequestParam(required = false) Long apartamentoId) {
        return apartamentoId == null ? service.cupos() : service.cuposDe(apartamentoId);
    }

    // Administrador: cupos propios del apartamento (maximo null = vuelve al general)
    @PutMapping("/cupos/apartamento/{apartamentoId}")
    public List<CupoVehiculoDTO> guardarCuposDe(@PathVariable Long apartamentoId, @RequestBody List<CupoVehiculoDTO> cupos) {
        return service.guardarCuposDe(apartamentoId, cupos);
    }

    @PutMapping("/cupos")
    public List<CupoVehiculoDTO> guardarCupos(@RequestBody List<CupoVehiculoDTO> cupos) {
        return service.guardarCupos(cupos);
    }
}
