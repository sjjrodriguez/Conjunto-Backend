package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.*;
import com.residencial.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P5 bloque 7: equipo del administrador principal (asistentes y contadores con cupos) y cartera de la administración. */
@SpringBootTest
@AutoConfigureMockMvc
class EquipoCarteraIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired CupoEquipoRepository cupoRepo;
    @Autowired SolicitudContableRepository solicitudRepo;
    @Autowired CuotaRepository cuotaRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PasswordEncoder encoder;

    String principal;
    final String mes = YearMonth.now(ZoneId.of("America/Bogota")).toString();

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        Administrador p = new Administrador(null, "jefe", encoder.encode("jefe1234"));
        p.setPrincipal(true);
        adminRepo.save(p);
        principal = "Bearer " + token("{\"usuario\":\"jefe\",\"contrasena\":\"jefe1234\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
        solicitudRepo.deleteAll();
        cuotaRepo.deleteAll();
        pagoRepo.deleteAll();
        aptoRepo.deleteAll();
        cupoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void elPrincipalCreaAsistentesYContadoresHastaElCupoYElAsistenteNoVeElEquipo() throws Exception {
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"jefe\",\"contrasena\":\"jefe1234\"}"))
                .andExpect(jsonPath("$.principal").value(true));

        enviar(put("/api/administradores/cupos"), principal, "[{\"rol\":\"ADMIN\",\"maximo\":1},{\"rol\":\"CONTADOR\",\"maximo\":1}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maximo").value(1))
                .andExpect(jsonPath("$[0].usados").value(0));

        // Sin datos personales no se crea
        enviar(post("/api/administradores"), principal, "{\"usuario\":\"ana\",\"contrasena\":\"ana12345\",\"rol\":\"admin\"}")
                .andExpect(status().isBadRequest());
        enviar(post("/api/administradores"), principal, cuenta("ana", "admin", "ana@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("Persona ana"))
                .andExpect(jsonPath("$.principal").value(false));
        enviar(post("/api/administradores"), principal, cuenta("beto", "admin", "beto@correo.com"))
                .andExpect(status().isBadRequest());
        enviar(post("/api/administradores"), principal, cuenta("caro", "contador", "no-es-correo"))
                .andExpect(status().isBadRequest());
        enviar(post("/api/administradores"), principal, cuenta("caro", "contador", "caro@correo.com"))
                .andExpect(status().isOk());
        // No se baja el cupo por debajo de lo que ya hay
        enviar(put("/api/administradores/cupos"), principal, "[{\"rol\":\"ADMIN\",\"maximo\":0}]")
                .andExpect(status().isBadRequest());

        // El asistente administra el conjunto, pero no el equipo
        String asistente = "Bearer " + token("{\"usuario\":\"ana\",\"contrasena\":\"ana12345\"}");
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"ana\",\"contrasena\":\"ana12345\"}"))
                .andExpect(jsonPath("$.principal").value(false));
        mvc.perform(get("/api/administradores").header("Authorization", asistente)).andExpect(status().isForbidden());
        mvc.perform(get("/api/apartamentos").header("Authorization", asistente)).andExpect(status().isOk());

        long jefe = adminRepo.findByUsuario("jefe").orElseThrow().getId();
        mvc.perform(delete("/api/administradores/" + jefe).header("Authorization", principal)).andExpect(status().isBadRequest());
        long ana = adminRepo.findByUsuario("ana").orElseThrow().getId();
        enviar(put("/api/administradores/" + ana), principal, "{\"telefono\":\"3110000000\"}")
                .andExpect(jsonPath("$.telefono").value("3110000000"))
                .andExpect(jsonPath("$.nombreCompleto").value("Persona ana"));
        mvc.perform(delete("/api/administradores/" + ana).header("Authorization", principal)).andExpect(status().isNoContent());
        mvc.perform(get("/api/administradores/cupos").header("Authorization", principal))
                .andExpect(jsonPath("$[0].usados").value(0))
                .andExpect(jsonPath("$[1].usados").value(1));
    }

    @Test
    void elPagoEnEfectivoDelContadorEsperaAlAdministradorYLasEstadisticasLoCuentan() throws Exception {
        enviar(post("/api/administradores"), principal, cuenta("caro", "contador", "caro@correo.com")).andExpect(status().isOk());
        String contador = "Bearer " + token("{\"usuario\":\"caro\",\"contrasena\":\"caro12345\"}");
        Apartamento a = aptoRepo.save(new Apartamento(null, "4", "401", encoder.encode("clave401")));
        Apartamento b = aptoRepo.save(new Apartamento(null, "4", "402", encoder.encode("clave402")));
        LocalDate limite = YearMonth.parse(mes).atEndOfMonth();
        Cuota ca = cuotaRepo.save(new Cuota(a, mes, new BigDecimal("100000"), limite));
        cuotaRepo.save(new Cuota(b, mes, new BigDecimal("100000"), limite));

        enviar(put("/api/cuotas/" + ca.getId() + "/estado"), contador, "{\"estado\":\"pagada\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.pagoPorAprobar").value(true));
        enviar(put("/api/cuotas/" + ca.getId() + "/estado"), contador, "{\"estado\":\"pagada\"}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/cuotas?periodo=" + mes).header("Authorization", contador))
                .andExpect(jsonPath("$[0].pagoPorAprobar").value(true));

        mvc.perform(get("/api/cartera/estadisticas").header("Authorization", principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value(mes))
                .andExpect(jsonPath("$.cuotas").value(2))
                .andExpect(jsonPath("$.pagadas").value(0))
                .andExpect(jsonPath("$.solicitudesPendientes").value(1));

        long id = solicitudRepo.findAll().get(0).getId();
        enviar(put("/api/cartera/solicitudes/" + id), principal, "{\"aprobada\":true}")
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.torre").value("4"));
        assertThat(cuotaRepo.findById(ca.getId()).orElseThrow().getEstado()).isEqualTo("PAGADA");

        mvc.perform(get("/api/cartera/estadisticas?periodo=" + mes).header("Authorization", contador))
                .andExpect(jsonPath("$.pagadas").value(1))
                .andExpect(jsonPath("$.facturado").value(200000))
                .andExpect(jsonPath("$.recaudado").value(100000))
                .andExpect(jsonPath("$.porcentajeRecaudo").value(50))
                .andExpect(jsonPath("$.solicitudesPendientes").value(0));

        // Rechazada: la cuota sigue sin pagar
        Cuota cb = cuotaRepo.findByApartamentoIdOrderByPeriodoDesc(b.getId()).get(0);
        enviar(put("/api/cuotas/" + cb.getId() + "/estado"), contador, "{\"estado\":\"pagada\"}").andExpect(status().isOk());
        long otra = solicitudRepo.findByEstadoOrderByCreadaEnDesc("PENDIENTE").get(0).getId();
        enviar(put("/api/cartera/solicitudes/" + otra), principal, "{\"aprobada\":false,\"motivo\":\"No llegó el dinero\"}")
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("No llegó el dinero"));
        assertThat(cuotaRepo.findById(cb.getId()).orElseThrow().getEstado()).isEqualTo("PENDIENTE");
    }

    private String cuenta(String usuario, String rol, String correo) {
        return "{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + usuario + "12345\",\"rol\":\"" + rol
                + "\",\"nombreCompleto\":\"Persona " + usuario + "\",\"documento\":\"1000" + usuario.length()
                + "\",\"telefono\":\"3000000000\",\"correo\":\"" + correo + "\"}";
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String auth, String json) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(String json) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
