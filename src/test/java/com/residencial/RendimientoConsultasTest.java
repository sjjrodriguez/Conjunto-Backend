package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.*;
import com.residencial.repository.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P2 bloque 1: cuántas consultas SQL hace cada listado. Con 3 apartamentos,
 * 3 obras y 9 trabajadores, un listado bien hecho no debe crecer con los datos.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RendimientoConsultasTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManagerFactory emf;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired ObraRepository obraRepo;
    @Autowired PersonalObraRepository personalRepo;
    @Autowired ZonaComunRepository zonaRepo;
    @Autowired ReservaRepository reservaRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired PasswordEncoder encoder;

    String admin;
    Obra primeraObra;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        for (int i = 1; i <= 3; i++) {
            Apartamento a = aptoRepo.save(new Apartamento(null, "7", "70" + i, encoder.encode("clave70" + i)));
            pagoRepo.save(new Pago(null, 1000.0 * i, "En Mora", null, a));
            Obra o = obraRepo.save(new Obra(null, "Obra " + i, LocalDate.now(), LocalDate.now().plusDays(10),
                    "EN_PROCESO", a));
            if (primeraObra == null) primeraObra = o;
            for (int j = 1; j <= 3; j++) {
                personalRepo.save(new PersonalObra(null, "Trabajador " + i + "-" + j, "CC" + i + j, true, o));
            }
        }
        admin = "Bearer " + tokenAdmin();
    }

    // La base H2 se comparte con los demás tests: no dejar filas que bloqueen sus borrados
    @AfterEach
    void limpiar() {
        primeraObra = null;
        notificacionRepo.deleteAll();
        personalRepo.deleteAll();
        obraRepo.deleteAll();
        reservaRepo.deleteAll();
        zonaRepo.deleteAll();
        vehiculoRepo.deleteAll();
        residenteRepo.deleteAll();
        pagoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void todoElPersonalEnUnaConsulta() throws Exception {
        long consultas = contarConsultas(() ->
                mvc.perform(get("/api/personal-obra").header("Authorization", admin))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(9))
                        .andExpect(jsonPath("$[0].torrePiso").value("7"))
                        .andExpect(jsonPath("$[0].aptoNum").isNotEmpty()));
        System.out.println("[rendimiento] GET /api/personal-obra -> " + consultas + " consultas SQL");
        assertThat(consultas).isEqualTo(1);
    }

    @Test
    void personalDeUnaObraEnPocasConsultas() throws Exception {
        long consultas = contarConsultas(() ->
                mvc.perform(get("/api/personal-obra/obra/" + primeraObra.getId()).header("Authorization", admin))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(3))
                        .andExpect(jsonPath("$[0].aptoNum").value("701")));
        System.out.println("[rendimiento] GET /api/personal-obra/obra/{id} -> " + consultas + " consultas SQL");
        // 1 para comprobar a qué apartamento pertenece la obra + 1 para el personal
        assertThat(consultas).isLessThanOrEqualTo(2);
    }

    @Test
    void carteraCompletaEnUnaConsulta() throws Exception {
        long consultas = contarConsultas(() ->
                mvc.perform(get("/api/pagos").header("Authorization", admin))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(3))
                        .andExpect(jsonPath("$[0].apartamentoId").isNotEmpty()));
        System.out.println("[rendimiento] GET /api/pagos -> " + consultas + " consultas SQL");
        assertThat(consultas).isEqualTo(1);
    }

    @Test
    void registrarPersonalDevuelveTorreYAptoSinOpenInView() throws Exception {
        // Antes de P2 esto solo funcionaba gracias a open-in-view (acceso perezoso a obra.apartamento)
        mvc.perform(post("/api/personal-obra").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreTrabajador\":\"Nuevo\",\"cedula\":\"999\",\"arlEstado\":false,\"obraId\":"
                                + primeraObra.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.torrePiso").value("7"))
                .andExpect(jsonPath("$.aptoNum").value("701"));
    }

    @Test
    void carteraSoloParaElAdmin() throws Exception {
        String json = mvc.perform(post("/api/auth/apartamento/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"torre\":\"7\",\"apto\":\"701\",\"contrasena\":\"clave701\"}"))
                .andReturn().getResponse().getContentAsString();
        String propietario = "Bearer " + JsonPath.read(json, "$.token");
        mvc.perform(get("/api/pagos").header("Authorization", propietario))
                .andExpect(status().isForbidden());
    }

    @Test
    void indicesCreados() throws Exception {
        try (var conexion = emf.unwrap(SessionFactory.class).getSessionFactoryOptions()
                .getServiceRegistry().getService(org.hibernate.engine.jdbc.connections.spi.ConnectionProvider.class)
                .getConnection();
             var rs = conexion.createStatement().executeQuery(
                     "select lower(index_name) from information_schema.indexes where lower(index_name) like 'idx_%'")) {
            var indices = new java.util.HashSet<String>();
            while (rs.next()) indices.add(rs.getString(1));
            assertThat(indices).contains("idx_residente_apartamento", "idx_vehiculo_apartamento",
                    "idx_obra_apartamento", "idx_reserva_apartamento", "idx_reserva_zona_fecha",
                    "idx_personal_obra_obra");
        }
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    interface Peticion { void ejecutar() throws Exception; }

    private long contarConsultas(Peticion peticion) throws Exception {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        peticion.ejecutar();
        return stats.getPrepareStatementCount();
    }

    private String tokenAdmin() throws Exception {
        String json = mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
