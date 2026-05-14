package com.residencial.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API - Administración Conjuntos Residenciales")
                        .version("1.0.0-MVP")
                        .description("""
                                Backend MVP para la administración provisional de conjuntos residenciales.
                                Gestiona apartamentos, propietarios, residentes, pagos y reservas de zonas comunes.
                                <br><br>
                                ⚠️ <b>MVP</b>: Todos los endpoints son públicos. Sin autenticación.
                                """)
                        .contact(new Contact()
                                .name("Equipo de Desarrollo")
                                .email("dev@residencial.com"))
                        .license(new License()
                                .name("Privado")
                                .url("#")));
    }
}
