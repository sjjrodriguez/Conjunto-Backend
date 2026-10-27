package com.residencial.controller;

import com.residencial.dto.DisponibilidadDTO;
import com.residencial.dto.HorarioZonaDTO;
import com.residencial.repository.ZonaBloqueoRepository;
import com.residencial.repository.ZonaComunRepository;
import com.residencial.repository.ZonaDisponibilidadRepository;
import com.residencial.dto.ZonaComunDTO;
import com.residencial.service.ReservaService;
import com.residencial.service.ZonaComunService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/zonas")
@RequiredArgsConstructor
public class ZonaComunController {

    private final ZonaComunService service;
    private final ReservaService reservas;
    private final ZonaComunRepository zonaRepo;
    private final ZonaDisponibilidadRepository disp;
    private final ZonaBloqueoRepository bloqueos;

    /**
     * Lo ocupado de la zona día por día entre dos fechas (máximo 62 días), para el calendario
     * de reservas. Las franjas de otros apartamentos no dicen de quién son.
     */
    @GetMapping("/{id}/disponibilidad")
    public DisponibilidadDTO disponibilidad(@PathVariable Long id,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reservas.disponibilidad(id, desde, hasta);
    }

    @GetMapping
    public List<ZonaComunDTO> listarTodas() {
        return service.listarTodas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZonaComunDTO> buscarPorId(@PathVariable Long id) {
        ZonaComunDTO z = service.buscarPorId(id);
        return z != null ? ResponseEntity.ok(z) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<ZonaComunDTO> guardar(@RequestBody ZonaComunDTO dto) {
        return ResponseEntity.ok(service.guardar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ZonaComunDTO> actualizar(@PathVariable Long id, @RequestBody ZonaComunDTO dto) {
        ZonaComunDTO updated = service.actualizar(id, dto);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    // ─── P5: horario por día de la semana y fechas bloqueadas ───

    @GetMapping("/{id}/horario")
    public HorarioZonaDTO horario(@PathVariable Long id) {
        buscarZona(id);
        return new HorarioZonaDTO(
                disp.horarioDe(id).stream().map(h -> new HorarioZonaDTO.Dia(h.getDiaSemana(), h.getApertura(), h.getCierre())).toList(),
                disp.bloqueosDe(id).stream().map(b -> new HorarioZonaDTO.Bloqueo(b.getId(), b.getFecha(), b.getMotivo())).toList());
    }

    /** Reemplaza el horario semanal; vacío = rige el horario general de la zona. */
    @PutMapping("/{id}/horario")
    @org.springframework.transaction.annotation.Transactional
    public HorarioZonaDTO guardarHorario(@PathVariable Long id, @RequestBody HorarioZonaDTO.Semanal semanal) {
        com.residencial.model.ZonaComun zona = buscarZona(id);
        List<HorarioZonaDTO.Dia> dias = semanal.semanal() == null ? List.of() : semanal.semanal();
        java.util.Set<Integer> vistos = new java.util.HashSet<>();
        for (HorarioZonaDTO.Dia d : dias) {
            if (d.diaSemana() == null || d.diaSemana() < 1 || d.diaSemana() > 7 || !vistos.add(d.diaSemana())) {
                throw invalido("Cada día de la semana (1 a 7) puede aparecer una sola vez");
            }
            if (d.apertura() == null || d.cierre() == null || !d.cierre().isAfter(d.apertura())) {
                throw invalido("La hora de cierre debe ser después de la de apertura");
            }
        }
        disp.borrarHorarioDe(id);
        disp.flush();
        disp.saveAll(dias.stream().map(d -> new com.residencial.model.ZonaHorario(null, zona, d.diaSemana(), d.apertura(), d.cierre())).toList());
        return horario(id);
    }

    @PostMapping("/{id}/bloqueos")
    public HorarioZonaDTO.Bloqueo bloquear(@PathVariable Long id, @RequestBody HorarioZonaDTO.Bloqueo bloqueo) {
        com.residencial.model.ZonaComun zona = buscarZona(id);
        if (bloqueo.fecha() == null) throw invalido("La fecha es obligatoria");
        if (bloqueos.existsByZonaIdAndFecha(id, bloqueo.fecha())) throw invalido("Esa fecha ya está bloqueada");
        String motivo = bloqueo.motivo() == null || bloqueo.motivo().isBlank() ? null : bloqueo.motivo().trim();
        if (motivo != null && motivo.length() > 200) throw invalido("El motivo puede tener máximo 200 caracteres");
        com.residencial.model.ZonaBloqueo b = bloqueos.save(new com.residencial.model.ZonaBloqueo(null, zona, bloqueo.fecha(), motivo));
        return new HorarioZonaDTO.Bloqueo(b.getId(), b.getFecha(), b.getMotivo());
    }

    @DeleteMapping("/{id}/bloqueos/{bloqueoId}")
    public ResponseEntity<Void> desbloquear(@PathVariable Long id, @PathVariable Long bloqueoId) {
        bloqueos.findById(bloqueoId).filter(b -> b.getZona().getId().equals(id)).ifPresent(bloqueos::delete);
        return ResponseEntity.noContent().build();
    }

    private com.residencial.model.ZonaComun buscarZona(Long id) {
        return zonaRepo.findById(id).orElseThrow(() -> new com.residencial.exception.RecursoNoEncontradoException("Zona común no encontrada"));
    }

    private static org.springframework.web.server.ResponseStatusException invalido(String mensaje) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, mensaje);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
