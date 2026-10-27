package com.residencial.controller;

import com.residencial.dto.PublicacionDTO;
import com.residencial.service.PublicacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/publicaciones")
@RequiredArgsConstructor
public class PublicacionController {

    private final PublicacionService service;

    /** El feed de Inicio, de la más nueva a la más antigua (20 por página). */
    @GetMapping
    public PublicacionDTO.Pagina listar(@RequestParam(defaultValue = "0") int pagina) {
        return service.listar(pagina);
    }

    /** Administración: texto y hasta 6 imágenes o archivos (campo "archivos"). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PublicacionDTO> publicar(@RequestParam(value = "texto", required = false) String texto,
                                                   @RequestParam(value = "archivos", required = false) List<MultipartFile> archivos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.publicar(texto, archivos));
    }

    @PutMapping("/{id}")
    public PublicacionDTO editar(@PathVariable Long id, @RequestBody PublicacionDTO.Edicion edicion) {
        return service.editar(id, edicion.texto());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
