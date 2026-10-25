package com.residencial.almacen;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Carpeta local, para la demo y las pruebas (sin Supabase). Los enlaces apuntan a
 * GET /api/archivos/local con una firma HMAC que vence, como los de Supabase.
 */
public class AlmacenLocal implements AlmacenArchivos {

    private final Path raiz;
    private final byte[] llave = new byte[32];

    public AlmacenLocal(Path raiz) {
        this.raiz = raiz.toAbsolutePath().normalize();
        new SecureRandom().nextBytes(llave);
    }

    @Override
    public void guardar(String ruta, byte[] datos, String tipo) {
        try {
            Path destino = resolver(ruta);
            Files.createDirectories(destino.getParent());
            Files.write(destino, datos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String enlace(String ruta, Duration vigencia) {
        long expira = Instant.now().plus(vigencia).getEpochSecond();
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/archivos/local")
                .queryParam("ruta", ruta)
                .queryParam("expira", expira)
                .queryParam("firma", firmar(ruta, expira))
                .build().encode().toUriString();
    }

    @Override
    public void borrar(String ruta) {
        try {
            Files.deleteIfExists(resolver(ruta));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** El archivo si la firma es válida y no ha vencido. */
    public Optional<byte[]> leer(String ruta, long expira, String firma) {
        if (Instant.now().getEpochSecond() > expira) return Optional.empty();
        if (!MessageDigest.isEqual(firmar(ruta, expira).getBytes(StandardCharsets.UTF_8),
                String.valueOf(firma).getBytes(StandardCharsets.UTF_8))) return Optional.empty();
        try {
            Path archivo = resolver(ruta);
            return Files.exists(archivo) ? Optional.of(Files.readAllBytes(archivo)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // Nunca fuera de la carpeta (rutas con "..")
    private Path resolver(String ruta) {
        Path p = raiz.resolve(ruta).normalize();
        if (!p.startsWith(raiz)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta no válida");
        return p;
    }

    private String firmar(String ruta, long expira) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(llave, "HmacSHA256"));
            byte[] firma = mac.doFinal((ruta + "|" + expira).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(firma);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
