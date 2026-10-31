package com.residencial.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import com.residencial.security.SesionVersion;
import com.residencial.security.VersionTokenValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class JwtConfig {

    private static final int MIN_BYTES_SECRETO = 32; // HS256 necesita al menos 256 bits

    @Bean
    public SecretKey jwtClave(@Value("${app.jwt.secret:}") String secreto) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_BYTES_SECRETO) {
            throw new IllegalStateException(
                    "Falta la variable de entorno JWT_SECRET o tiene menos de 32 caracteres");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtClave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtClave));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtClave, SesionVersion sesiones) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtClave).macAlgorithm(MacAlgorithm.HS256).build();
        // Además de firma y caducidad: que la sesión no se haya cerrado ni la cuenta dado de baja
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), new VersionTokenValidator(sesiones)));
        return decoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
