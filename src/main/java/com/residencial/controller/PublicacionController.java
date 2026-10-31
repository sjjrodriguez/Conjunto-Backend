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

    /** El feed de Inicio: las fijadas primero y luego de la más nueva a la más antigua (20 por página). */
    @GetMapping
    public PublicacionDTO.Pagina listar(@RequestParam(defaultValue = "0") int pagina) {
        return service.listar(pagina);
    }

    /** Administración: texto y hasta 6 imágenes o archivos (campo "archivos"). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PublicacionDTO> publicar(@RequestParam(value = "texto", required = false) String texto,
                                                   @RequestParam(value = "archivos", required = false) List<MultipartFile> archivos,
                                                   @RequestParam(value = "titulo", required = false) String titulo,
                                                   @RequestParam(value = "categoria", required = false) String categoria,
                                                   @RequestParam(value = "audiencia", required = false) String audiencia,
                                                   @RequestParam(value = "torre", required = false) String torre,
                                                   @RequestParam(value = "fijado", defaultValue = "false") boolean fijado) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.publicar(texto, archivos, titulo, categoria, audiencia, torre, fijado));
    }

    @PutMapping("/{id}")
    public PublicacionDTO editar(@PathVariable Long id, @RequestBody PublicacionDTO.Edicion edicion) {
        return service.editar(id, edicion);
    }

    @PatchMapping("/{id}/fijar")
    public PublicacionDTO fijar(@PathVariable Long id, @RequestBody PublicacionDTO.Fijar cuerpo) {
        return service.fijar(id, cuerpo.fijado());
    }

    /** Quien la abre la marca como leída (idempotente). */
    @PostMapping("/{id}/lectura")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id) {
        service.marcarLeida(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
