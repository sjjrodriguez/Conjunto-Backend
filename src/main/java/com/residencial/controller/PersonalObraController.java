package com.residencial.controller;

import com.residencial.dto.PersonalObraDTO;
import com.residencial.service.PersonalObraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/personal-obra")
@RequiredArgsConstructor
public class PersonalObraController {

    private final PersonalObraService service;

    @GetMapping("/obra/{obraId}")
    public List<PersonalObraDTO> listarPorObra(@PathVariable Long obraId) {
        return service.listarPorObra(obraId);
    }

    @PostMapping
    public ResponseEntity<PersonalObraDTO> guardar(@RequestBody PersonalObraDTO dto) {
        return ResponseEntity.ok(service.guardar(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
