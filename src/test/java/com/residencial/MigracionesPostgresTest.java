package com.residencial;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4: las migraciones de Flyway contra un Postgres real (embebido), como en Supabase.
 * - Base vacía: las migraciones crean todas las tablas y Hibernate ('validate') confirma que
 *   coinciden con las entidades (si no, la app no arranca).
 * - Base existente sin Flyway (el caso de Supabase): no se ejecuta V1, se marca como versión 1
 *   y se aplican solo las siguientes, sin perder datos.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
class MigracionesPostgresTest {

    private static final EmbeddedPostgres POSTGRES = iniciar();

    @Autowired
    private JdbcTemplate jdbc;

    private static EmbeddedPostgres iniciar() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo iniciar Postgres embebido", e);
        }
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registro.add("spring.datasource.username", () -> "postgres");
        registro.add("spring.datasource.password", () -> "");
    }

    @AfterAll
    static void detener() throws IOException {
        POSTGRES.close();
    }

    @Test
    void enBaseVaciaLasMigracionesCreanLasTablasYCoincidenConLasEntidades() {
        // Si las tablas no coincidieran con las entidades, el contexto no habría arrancado
        Integer aplicadas = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(aplicadas).isEqualTo(4);
        Integer tablas = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'"
                        + " and table_name <> 'flyway_schema_history'", Integer.class);
        assertThat(tablas).isEqualTo(12);
    }

    @Test
    void enBaseExistenteSinFlywayNoEjecutaV1YAplicaSoloLasNuevas() throws Exception {
        // Una base aparte con las tablas ya creadas, como estaba Supabase antes de Flyway
        DataSource postgres = POSTGRES.getPostgresDatabase();
        try (Connection c = postgres.getConnection(); Statement st = c.createStatement()) {
            st.execute("create database existente");
        }
        DataSource existente = POSTGRES.getDatabase("postgres", "existente");
        try (Connection c = existente.getConnection(); Statement st = c.createStatement()) {
            st.execute(Files.readString(
                    Path.of("src/main/resources/db/migration/V1__esquema_inicial.sql"), StandardCharsets.UTF_8));
            st.execute("insert into apartamento (torre, apto, contrasena) values ('1', '101', 'x')");
        }

        // Misma configuración que application.properties
        Flyway flyway = Flyway.configure()
                .dataSource(existente)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
        MigrateResult resultado = flyway.migrate();

        // V1 no corre (la base ya tenía esas tablas): queda como línea base; V2 en adelante sí
        assertThat(flyway.info().applied()[0].getType().isBaseline()).isTrue();
        assertThat(flyway.info().applied()[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(resultado.migrationsExecuted).isEqualTo(flyway.info().applied().length - 1);
        MigrationInfo actual = flyway.info().current();
        assertThat(actual.getVersion().getVersion()).isEqualTo("4");
        // Los datos siguen ahí
        try (Connection c = existente.getConnection(); Statement st = c.createStatement();
             var rs = st.executeQuery("select count(*) from apartamento")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }
}
