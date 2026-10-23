package com.residencial.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.IOException;
import java.util.Set;

/**
 * Avisos en el celular. Con FIREBASE_CREDENTIALS (JSON de la cuenta de servicio de Firebase)
 * se envían de verdad; sin ella, no se envía nada y todo lo demás funciona igual.
 * Los envíos van en segundo plano (@Async) para no demorar la respuesta al usuario.
 */
@Slf4j
@Configuration
@EnableAsync
public class PushConfig {

    @Bean
    EnviadorPush enviadorPush(@Value("${app.firebase.credenciales:}") String credenciales) throws IOException {
        if (credenciales == null || credenciales.isBlank()) {
            log.info("Avisos push desactivados: falta FIREBASE_CREDENTIALS");
            return (tokens, datos) -> Set.of();
        }
        log.info("Avisos push activos (Firebase Cloud Messaging)");
        return new FirebaseEnviadorPush(credenciales);
    }
}
