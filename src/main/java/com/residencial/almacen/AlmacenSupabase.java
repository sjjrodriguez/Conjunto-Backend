package com.residencial.almacen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Supabase Storage por su API REST, con la service key (solo la conoce el backend).
 * El bucket debe existir y ser privado (docs/comprobantes.md).
 */
public class AlmacenSupabase implements AlmacenArchivos {

    private final String base;       // https://xxxx.supabase.co/storage/v1
    private final String clave;
    private final String bucket;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public AlmacenSupabase(String url, String clave, String bucket) {
        this.base = url.replaceAll("/+$", "") + "/storage/v1";
        this.clave = clave;
        this.bucket = bucket;
    }

    @Override
    public void guardar(String ruta, byte[] datos, String tipo) {
        enviar(peticion("/object/" + bucket + "/" + codificar(ruta))
                .header("Content-Type", tipo)
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(datos)).build());
    }

    @Override
    public String enlace(String ruta, Duration vigencia) {
        String cuerpo = "{\"expiresIn\":" + vigencia.toSeconds() + "}";
        String respuesta = enviar(peticion("/object/sign/" + bucket + "/" + codificar(ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build());
        try {
            JsonNode nodo = json.readTree(respuesta);
            // Responde una ruta relativa a /storage/v1: "/object/sign/bucket/ruta?token=..."
            return base + nodo.path("signedURL").asText();
        } catch (IOException e) {
            throw fallo();
        }
    }

    @Override
    public void borrar(String ruta) {
        enviar(peticion("/object/" + bucket + "/" + codificar(ruta)).DELETE().build());
    }

    private HttpRequest.Builder peticion(String camino) {
        return HttpRequest.newBuilder(URI.create(base + camino))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + clave)
                .header("apikey", clave);
    }

    private String enviar(HttpRequest peticion) {
        try {
            HttpResponse<String> r = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) throw fallo();
            return r.body();
        } catch (IOException e) {
            throw fallo();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fallo();
        }
    }

    // Cada parte de la ruta va codificada; las barras se conservan
    private static String codificar(String ruta) {
        return Arrays.stream(ruta.split("/"))
                .map(p -> URLEncoder.encode(p, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private static ResponseStatusException fallo() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "No se pudo acceder al almacenamiento de comprobantes. Inténtalo de nuevo.");
    }
}
