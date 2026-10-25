package com.residencial.controller;

import com.residencial.almacen.AlmacenArchivos;
import com.residencial.almacen.AlmacenLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Descarga de comprobantes guardados en la carpeta local (demo y pruebas). Sin sesión, como
 * los enlaces firmados de Supabase: basta la firma, que vence a los 10 minutos.
 */
@RestController
@RequestMapping("/api/archivos/local")
@RequiredArgsConstructor
public class ArchivoLocalController {

    private final AlmacenArchivos almacen;

    @GetMapping
    public ResponseEntity<byte[]> descargar(@RequestParam String ruta, @RequestParam long expira,
                                            @RequestParam String firma) {
        if (!(almacen instanceof AlmacenLocal local)) return ResponseEntity.notFound().build();
        return local.leer(ruta, expira, firma)
                .map(datos -> ResponseEntity.ok()
                        .contentType(tipo(ruta))
                        .cacheControl(CacheControl.noStore())
                        .body(datos))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static MediaType tipo(String ruta) {
        if (ruta.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (ruta.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (ruta.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
