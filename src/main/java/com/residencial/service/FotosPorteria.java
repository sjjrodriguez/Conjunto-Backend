package com.residencial.service;

import com.residencial.almacen.AlmacenArchivos;
import com.residencial.conjunto.ConjuntoContexto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * P10 bloque 7: fotos que toma la portería (la cara del visitante, el paquete que llegó). Se
 * suben primero y la visita o la correspondencia se registra con el fotoId que devuelve. La ruta
 * siempre lleva el conjunto de la sesión, así que nadie puede usar la foto de otro conjunto. El
 * bucket es privado: se ven con un enlace firmado que vence.
 */
@Service
public class FotosPorteria {

    public record Subida(String fotoId) {}

    public record Enlace(String url) {}

    static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
    static final Duration VIGENCIA = Duration.ofMinutes(15);
    private static final Pattern ID = Pattern.compile("^[0-9a-f]{32}\\.(jpg|png)$");

    private final AlmacenArchivos almacen;

    public FotosPorteria(AlmacenArchivos almacen) {
        this.almacen = almacen;
    }

    public Subida subir(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) throw invalido("Toma la foto");
        if (archivo.getSize() > TAMANO_MAXIMO) throw invalido("La foto no puede pasar de 5 MB");
        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw invalido("No se pudo leer la foto");
        }
        String[] tipo = tipo(datos);
        if (tipo == null) throw invalido("La foto debe ser JPG o PNG");
        String id = UUID.randomUUID().toString().replace("-", "") + "." + tipo[1];
        almacen.guardar(ruta(id), datos, tipo[0]);
        return new Subida(id);
    }

    /** La ruta de un fotoId que llegó al registrar (null si no hay foto); 400 si no es válido. */
    public String rutaDe(String fotoId) {
        if (fotoId == null || fotoId.isBlank()) return null;
        if (!ID.matcher(fotoId.trim()).matches()) throw invalido("La foto no es válida; tómala otra vez");
        return ruta(fotoId.trim());
    }

    public String enlace(String ruta) {
        return ruta == null ? null : almacen.enlace(ruta, VIGENCIA);
    }

    public Map<String, String> enlaces(Collection<String> rutas) {
        return almacen.enlaces(rutas, VIGENCIA);
    }

    private static String ruta(String id) {
        return "porteria/conjunto-" + ConjuntoContexto.actual() + "/" + id;
    }

    private static String[] tipo(byte[] d) {
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return new String[]{"image/jpeg", "jpg"};
        if (d.length > 4 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return new String[]{"image/png", "png"};
        return null;
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
