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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P1 bloque 1: códigos de error coherentes con cuerpo {"mensaje": "..."}. */
@SpringBootTest
@AutoConfigureMockMvc
class ErroresIntegrationTest {

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
        apto = aptoRepo.save(new Apartamento(null, "3", "301", encoder.encode("clave301")));
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

    @Test
    void apartamentoInexistenteDevuelve404() throws Exception {
        mvc.perform(post("/api/vehiculos").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"XYZ987\",\"tipo\":\"Moto\",\"apartamentoId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Apartamento no encontrado"));
    }

    @Test
    void obraInexistenteAlRegistrarPersonalDevuelve404() throws Exception {
        mvc.perform(post("/api/personal-obra").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreTrabajador\":\"Ana\",\"cedula\":\"123\",\"arlEstado\":true,\"obraId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void borrarApartamentoConDatosDevuelve409() throws Exception {
        // P4 bloque 8: la cartera se borra con él; los residentes no
        residenteRepo.save(new Residente(null, "Ana", null, "1", "PROPIETARIO", apto));
        mvc.perform(delete("/api/apartamentos/" + apto.getId()).header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Primero quita sus residentes, vehículos, obras y reservas"));
        assertThat(aptoRepo.existsById(apto.getId())).isTrue();
    }

    @Test
    void borrarObraBorraTambienSuPersonal() throws Exception {
        Obra obra = obraRepo.save(new Obra(null, "Cambio de piso", LocalDate.now(),
                LocalDate.now().plusDays(5), "EN_PROCESO", apto));
        personalRepo.save(new PersonalObra(null, "Luis", "456", true, obra));

        mvc.perform(delete("/api/obras/" + obra.getId()).header("Authorization", admin))
                .andExpect(status().isNoContent());
        assertThat(obraRepo.existsById(obra.getId())).isFalse();
        assertThat(personalRepo.findByObraId(obra.getId())).isEmpty();
    }

    @Test
    void cambiarEstadoSinEstadoDevuelve400() throws Exception {
        ZonaComun zona = zonaRepo.save(new ZonaComun(null, "BBQ", "Bueno", null, true));
        Reserva reserva = reservaRepo.save(new Reserva(null, LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), "PENDIENTE", zona, apto));
        String url = "/api/reservas/" + reserva.getId() + "/estado";

        mvc.perform(put(url).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El estado de la reserva es obligatorio"));
        mvc.perform(put(url).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        assertThat(reservaRepo.findById(reserva.getId()).orElseThrow().getEstado()).isEqualTo("PENDIENTE");
    }

    @Test
    void parametroNoNumericoDevuelve400() throws Exception {
        mvc.perform(get("/api/apartamentos/abc").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void rutaInexistenteConservaSu404() throws Exception {
        mvc.perform(get("/api/no-existe").header("Authorization", admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void fechaMalFormadaDevuelve400() throws Exception {
        mvc.perform(post("/api/obras").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcionAdecuacion\":\"x\",\"fechaInicio\":\"20-10-2026\","
                                + "\"fechaFinEstimada\":\"2026-10-30\",\"estadoObra\":\"EN_PROCESO\",\"apartamentoId\":"
                                + apto.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
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
