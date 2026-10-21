package com.residencial;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 bloque 2: valida con Hikari la configuración del pool de producción.
 * Se lee el archivo de src/main porque en los tests el de src/test lo reemplaza.
 * Hikari no falla si keepalive-time >= max-lifetime: desactiva el keepalive en
 * silencio. Este test lo detecta.
 */
class ConfiguracionPoolTest {

    @Test
    void poolDeProduccionEsValidoYConservaElKeepalive() throws Exception {
        Properties props = new Properties();
        try (var in = new InputStreamReader(
                new FileInputStream("src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
            props.load(in);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:pool"); // validate() exige una URL; no se conecta
        config.setMaximumPoolSize(Integer.parseInt(valor(props, "maximum-pool-size")));
        config.setMinimumIdle(Integer.parseInt(valor(props, "minimum-idle")));
        config.setIdleTimeout(Long.parseLong(valor(props, "idle-timeout")));
        config.setKeepaliveTime(Long.parseLong(valor(props, "keepalive-time")));
        config.setMaxLifetime(Long.parseLong(valor(props, "max-lifetime")));
        config.setConnectionTimeout(Long.parseLong(valor(props, "connection-timeout")));
        config.validate();

        assertThat(config.getMaximumPoolSize()).isEqualTo(5);
        assertThat(config.getMinimumIdle()).isEqualTo(1);
        // Si validate() lo hubiera considerado inválido, lo habría puesto en 0
        assertThat(config.getKeepaliveTime()).isEqualTo(240_000L);
        assertThat(config.getKeepaliveTime()).isLessThan(config.getMaxLifetime());
        assertThat(config.getIdleTimeout()).isLessThan(config.getMaxLifetime());
    }

    // Resuelve "${DB_POOL_MAX:5}" a su valor por defecto
    private static String valor(Properties props, String clave) {
        String v = props.getProperty("spring.datasource.hikari." + clave).trim();
        if (v.startsWith("${") && v.contains(":")) {
            v = v.substring(v.indexOf(':') + 1, v.length() - 1);
        }
        return v;
    }
}
