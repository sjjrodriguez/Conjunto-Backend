package com.residencial.controller;

import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.VisitanteFrecuente;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.VisitanteFrecuenteRepository;
import com.residencial.security.AccesoService;
import com.residencial.security.EstadoApartamento;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Visitantes frecuentes de cada apartamento (P5): los gestiona quien vive ahí; el administrador también. */
@RestController
@RequestMapping("/api/visitantes")
@RequiredArgsConstructor
public class VisitanteController {

    private static final int MAXIMO = 30;

    private final VisitanteFrecuenteRepository repo;
    private final ApartamentoRepository aptoRepo;
    private final AccesoService acceso;
    private final EstadoApartamento estado;

    @Value("${app.zona-horaria:America/Bogota}")
    private String zonaHoraria;

    public record VisitanteDTO(Long id, String nombreCompleto, String identificacion, String telefono, Long apartamentoId) {}

    @GetMapping("/apartamento/{apartamentoId}")
    public List<VisitanteDTO> listar(@PathVariable Long apartamentoId) {
        acceso.verificarApartamento(apartamentoId);
        return repo.findByApartamentoIdOrderByNombreCompletoAsc(apartamentoId).stream().map(VisitanteController::toDTO).toList();
    }

    @PostMapping
    @Transactional
    public VisitanteDTO crear(@RequestBody VisitanteDTO dto) {
        if (dto.apartamentoId() == null) throw invalido("El apartamento es obligatorio");
        acceso.verificarApartamento(dto.apartamentoId());
        estado.exigirQueViva(dto.apartamentoId());
        Apartamento a = aptoRepo.findById(dto.apartamentoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado"));
        if (repo.findByApartamentoIdOrderByNombreCompletoAsc(a.getId()).size() >= MAXIMO) {
            throw invalido("Máximo " + MAXIMO + " visitantes frecuentes por apartamento");
        }
        VisitanteFrecuente v = new VisitanteFrecuente(null, a, nombre(dto), vacioANull(dto.identificacion()),
                vacioANull(dto.telefono()), LocalDateTime.now(ZoneId.of(zonaHoraria)));
        return toDTO(repo.save(v));
    }

    @PutMapping("/{id}")
    @Transactional
    public VisitanteDTO actualizar(@PathVariable Long id, @RequestBody VisitanteDTO dto) {
        VisitanteFrecuente v = buscar(id);
        v.setNombreCompleto(nombre(dto));
        v.setIdentificacion(vacioANull(dto.identificacion()));
        v.setTelefono(vacioANull(dto.telefono()));
        return toDTO(repo.save(v));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        repo.delete(buscar(id));
        return ResponseEntity.noContent().build();
    }

    // Con el permiso de escribir en su apartamento
    private VisitanteFrecuente buscar(Long id) {
        VisitanteFrecuente v = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Visitante no encontrado: " + id));
        acceso.verificarApartamento(v.getApartamento().getId());
        estado.exigirQueViva(v.getApartamento().getId());
        return v;
    }

    private static String nombre(VisitanteDTO dto) {
        if (dto.nombreCompleto() == null || dto.nombreCompleto().isBlank()) throw invalido("El nombre es obligatorio");
        return dto.nombreCompleto().trim();
    }

    private static VisitanteDTO toDTO(VisitanteFrecuente v) {
        return new VisitanteDTO(v.getId(), v.getNombreCompleto(), v.getIdentificacion(), v.getTelefono(), v.getApartamento().getId());
    }

    private static String vacioANull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
