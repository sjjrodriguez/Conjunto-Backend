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

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P1 bloque 4: las notificaciones del admin viven en el backend. */
@SpringBootTest
@AutoConfigureMockMvc
class NotificacionesIntegrationTest {

    @Autowired MockMvc mvc;
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

    Apartamento apto;
    ZonaComun gimnasio;
    String admin;
    String propietario;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "6", "601", encoder.encode("clave601")));
        gimnasio = zonaRepo.save(new ZonaComun(null, "Gimnasio", "Bueno", null, true));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        propietario = "Bearer " + token("/api/auth/apartamento/login",
                "{\"torre\":\"6\",\"apto\":\"601\",\"contrasena\":\"clave601\"}");
    }

    // La base H2 se comparte con los demás tests: no dejar filas que bloqueen sus borrados
    @AfterEach
    void limpiar() {
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
    void reservaDelPropietarioLlegaAlAdmin() throws Exception {
        LocalDate dia = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(2);
        enviar(post("/api/reservas"), propietario, "{\"fecha\":\"" + dia + "\",\"hora\":\"07:00\","
                + "\"zonaComunId\":" + gimnasio.getId() + ",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("RESERVA"))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("Gimnasio")))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("07:00")))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("Torre 6 Apto 601")))
                .andExpect(jsonPath("$[0].leida").value(false))
                .andExpect(jsonPath("$[0].fecha").isNotEmpty());
    }

    @Test
    void vehiculoDelPropietarioLlegaAlAdminPeroElDelAdminNo() throws Exception {
        enviar(post("/api/vehiculos"), propietario,
                "{\"placa\":\"JKL456\",\"marca\":\"Renault\",\"color\":\"Gris\",\"tipo\":\"Carro\",\"apartamentoId\":"
                        + apto.getId() + "}")
                .andExpect(status().isOk());
        enviar(post("/api/vehiculos"), admin,
                "{\"placa\":\"MNO789\",\"tipo\":\"Moto\",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("VEHICULO"))
                .andExpect(jsonPath("$[0].mensaje").value(
                        "Nuevo vehículo registrado — JKL456 (Carro Renault Gris) · Torre 6 Apto 601"));
    }

    @Test
    void marcarLeidasYLimpiar() throws Exception {
        enviar(post("/api/vehiculos"), propietario,
                "{\"placa\":\"AAA111\",\"tipo\":\"Moto\",\"apartamentoId\":" + apto.getId() + "}");

        mvc.perform(put("/api/notificaciones/leidas").header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andExpect(jsonPath("$[0].leida").value(true));

        mvc.perform(delete("/api/notificaciones").header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void soloElAdminVeLasNotificaciones() throws Exception {
        mvc.perform(get("/api/notificaciones").header("Authorization", propietario))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/notificaciones").header("Authorization", propietario))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/notificaciones"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                                 String auth, String json) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(String url, String json) throws Exception {
        String respuesta = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
