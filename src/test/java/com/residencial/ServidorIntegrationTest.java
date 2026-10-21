package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Administrador;
import com.residencial.model.ZonaComun;
import com.residencial.repository.AdministradorRepository;
import com.residencial.repository.ZonaComunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 bloque 3: compresión gzip y /actuator/health contra un Tomcat real
 * (MockMvc no pasa por la compresión del servidor). Usa la configuración real
 * de application.properties; solo la base y los secretos vienen de los tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:servidor;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class ServidorIntegrationTest {

    @LocalServerPort int puerto;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ZonaComunRepository zonaRepo;
    @Autowired PasswordEncoder encoder;

    // java.net.http no descomprime solo: así se ve exactamente lo que manda el servidor
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void datos() {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        for (int i = 1; i <= 30; i++) {
            zonaRepo.save(new ZonaComun(null, "Zona " + i, "Bueno",
                    "Descripción larga de la zona común número " + i + " para superar 1 KB", true));
        }
    }

    @AfterEach
    void limpiar() {
        zonaRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void listasGrandesSeComprimenConGzip() throws Exception {
        HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(url("/api/zonas"))
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .header("Accept-Encoding", "gzip")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Encoding")).hasValue("gzip");
        String json;
        try (var gz = new GZIPInputStream(new ByteArrayInputStream(r.body()))) {
            json = new String(gz.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<?> zonas = JsonPath.read(json, "$");
        assertThat(zonas).hasSize(30);
        System.out.println("[rendimiento] GET /api/zonas: " + json.getBytes(StandardCharsets.UTF_8).length
                + " bytes -> " + r.body().length + " bytes con gzip");
    }

    @Test
    void sinAcceptEncodingNoSeComprime() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(url("/api/zonas"))
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Encoding")).isEmpty();
    }

    @Test
    void healthEsPublicoYSinDetalles() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(url("/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void elRestoDeActuatorNoEstaExpuesto() throws Exception {
        for (String ruta : List.of("/actuator", "/actuator/env", "/actuator/beans")) {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(url(ruta))
                            .header("Authorization", "Bearer " + tokenAdmin()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(r.statusCode()).as(ruta).isIn(404, 403);
        }
    }

    private URI url(String ruta) {
        return URI.create("http://localhost:" + puerto + ruta);
    }

    private String tokenAdmin() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(url("/api/auth/admin/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return JsonPath.read(r.body(), "$.token");
    }
}
