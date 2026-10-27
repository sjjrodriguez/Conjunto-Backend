package com.residencial.controller;

import com.residencial.dto.CuotaDTO;
import com.residencial.dto.PersonalObraDTO;
import com.residencial.service.PersonalObraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/personal-obra")
@RequiredArgsConstructor
public class PersonalObraController {

    private final PersonalObraService service;

    @GetMapping
    public List<PersonalObraDTO> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/obra/{obraId}")
    public List<PersonalObraDTO> listarPorObra(@PathVariable Long obraId) {
        return service.listarPorObra(obraId);
    }

    @PostMapping
    public ResponseEntity<PersonalObraDTO> guardar(@RequestBody PersonalObraDTO dto) {
        return ResponseEntity.ok(service.guardar(dto));
    }

    @PutMapping("/{id}")
    public PersonalObraDTO actualizar(@PathVariable Long id, @RequestBody PersonalObraDTO dto) {
        return service.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** Certificado ARL del trabajador: foto o PDF (campo "archivo"). */
    @PostMapping(value = "/{id}/arl", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PersonalObraDTO subirCertificado(@PathVariable Long id, @RequestPart("archivo") MultipartFile archivo) {
        return service.subirCertificado(id, archivo);
    }

    /** Enlace temporal (10 minutos) para ver el certificado ARL. */
    @GetMapping("/{id}/arl")
    public CuotaDTO.Enlace enlaceCertificado(@PathVariable Long id) {
        return service.enlaceCertificado(id);
    }

    /** Administración: aprobar o rechazar al trabajador tras revisar su ARL. */
    @PutMapping("/{id}/aprobacion")
    public PersonalObraDTO revisar(@PathVariable Long id, @RequestBody PersonalObraDTO.Revision revision) {
        return service.revisar(id, revision.aprobado(), revision.motivo());
    }
}
