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
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P1 bloque 2: estados y tipos validados y normalizados. */
@SpringBootTest
@AutoConfigureMockMvc
class EstadosIntegrationTest {

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
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    String admin;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "4", "401", encoder.encode("clave401")));
        admin = "Bearer " + tokenAdmin();
    }

    // La base H2 se comparte con los demás tests: no dejar filas que bloqueen sus borrados
    @AfterEach
    void limpiar() {
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

    // ─── Reservas ────────────────────────────────────────────────────────────

    @Test
    void estadoDeReservaSeNormalizaOSeRechaza() throws Exception {
        ZonaComun zona = zonaRepo.save(new ZonaComun(null, "Salón", "Bueno", null, true));
        Reserva r = reservaRepo.save(new Reserva(null, LocalDate.now().plusDays(2),
                LocalTime.of(15, 0), "PENDIENTE", zona, apto));
        String url = "/api/reservas/" + r.getId() + "/estado";

        enviar(put(url), "{\"estado\":\"aprobada\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));
        enviar(put(url), "{\"estado\":\"cancelada\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        "El estado de la reserva no es válido. Valores permitidos: PENDIENTE, APROBADA, RECHAZADA"));
    }

    // ─── Obras ───────────────────────────────────────────────────────────────

    @Test
    void obraNormalizaEstadoYValidaFechas() throws Exception {
        enviar(post("/api/obras"), obra("en proceso", "2026-11-01", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoObra").value("EN_PROCESO"));
        enviar(post("/api/obras"), obra("TERMINADA", "2026-11-01", "2026-11-10"))
                .andExpect(status().isBadRequest());
        enviar(post("/api/obras"), obra("EN_PROCESO", "2026-11-10", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La fecha fin no puede ser anterior a la fecha de inicio"));
        enviar(post("/api/obras"), "{\"descripcionAdecuacion\":\"x\",\"estadoObra\":\"EN_PROCESO\",\"apartamentoId\":"
                + apto.getId() + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Las fechas de inicio y fin son obligatorias"));
    }

    @Test
    void cambiarEstadoDeObraValidaElValor() throws Exception {
        Obra o = obraRepo.save(new Obra(null, "Pintura", LocalDate.now(), LocalDate.now().plusDays(3),
                "EN_PROCESO", apto));
        String url = "/api/obras/" + o.getId() + "/estado";
        enviar(put(url), "{\"estado\":\"finalizada\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoObra").value("FINALIZADA"));
        enviar(put(url), "{\"estado\":\"PAUSADA\"}")
                .andExpect(status().isBadRequest());
    }

    // ─── Residentes, vehículos y pagos ───────────────────────────────────────

    @Test
    void tipoDeHabitanteSeNormalizaOSeRechaza() throws Exception {
        enviar(post("/api/residentes"), residente("propietario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoHabitante").value("PROPIETARIO"));
        enviar(post("/api/residentes"), residente("dueño"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void tipoDeVehiculoSeNormalizaOSeRechaza() throws Exception {
        enviar(post("/api/vehiculos"), vehiculo("MOTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Moto"));
        enviar(post("/api/vehiculos"), vehiculo("bicicleta"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void estadoDeCuentaSeNormaliza() throws Exception {
        enviar(post("/api/pagos"), "{\"saldoPendiente\":0,\"estadoCuenta\":\"al dia\",\"observaciones\":\"\","
                + "\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCuenta").value("Al Día"));
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                                 String json) throws Exception {
        return mvc.perform(req.header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String obra(String estado, String inicio, String fin) {
        return "{\"descripcionAdecuacion\":\"Remodelación cocina\",\"fechaInicio\":\"" + inicio
                + "\",\"fechaFinEstimada\":\"" + fin + "\",\"estadoObra\":\"" + estado
                + "\",\"apartamentoId\":" + apto.getId() + "}";
    }

    private String residente(String tipo) {
        return "{\"nombreCompleto\":\"Marta Gómez\",\"telefono\":\"3001234567\",\"identificacion\":\"1010\","
                + "\"tipoHabitante\":\"" + tipo + "\",\"apartamentoId\":" + apto.getId() + "}";
    }

    private String vehiculo(String tipo) {
        return "{\"placa\":\"QWE123\",\"marca\":\"Yamaha\",\"color\":\"Negro\",\"tipo\":\"" + tipo
                + "\",\"apartamentoId\":" + apto.getId() + "}";
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
