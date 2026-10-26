package com.residencial.correo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Brevo (API transaccional, plan gratuito) si está BREVO_API_KEY; si no, solo log. Ver docs/correo.md */
@Configuration
public class ConfiguracionCorreo {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionCorreo.class);

    @Bean
    public Correo correo(@Value("${app.correo.brevo-api-key:}") String clave,
                         @Value("${app.correo.remitente:}") String remitente,
                         @Value("${app.correo.nombre-remitente:MiConjunto}") String nombreRemitente) {
        if (clave.isBlank() || remitente.isBlank()) {
            log.warn("Correos solo en el log (sin BREVO_API_KEY / CORREO_REMITENTE)");
            return (para, nombre, asunto, html) -> log.info("Correo para {}: {}", para, asunto);
        }
        log.info("Correos por Brevo desde {}", remitente);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        ObjectMapper json = new ObjectMapper();
        return (para, nombre, asunto, html) -> {
            try {
                String cuerpo = json.writeValueAsString(Map.of(
                        "sender", Map.of("name", nombreRemitente, "email", remitente),
                        "to", List.of(Map.of("email", para, "name", nombre == null ? para : nombre)),
                        "subject", asunto,
                        "htmlContent", html));
                HttpRequest peticion = HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                        .timeout(Duration.ofSeconds(20))
                        .header("api-key", clave)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
                // En segundo plano: el registro no espera al correo
                http.sendAsync(peticion, HttpResponse.BodyHandlers.ofString()).whenComplete((r, e) -> {
                    if (e != null) log.warn("No se pudo enviar el correo a {}: {}", para, e.getMessage());
                    else if (r.statusCode() / 100 != 2) log.warn("Brevo rechazó el correo a {}: {} {}", para, r.statusCode(), r.body());
                });
            } catch (Exception e) {
                log.warn("No se pudo preparar el correo a {}: {}", para, e.getMessage());
            }
        };
    }
}
