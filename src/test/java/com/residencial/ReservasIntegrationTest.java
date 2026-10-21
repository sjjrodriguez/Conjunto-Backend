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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P1 bloque 3: reservas con bloques de 2 horas, validaciones y concurrencia. */
@SpringBootTest
@AutoConfigureMockMvc
class ReservasIntegrationTest {

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
    ZonaComun bbq;
    String admin;
    // Siempre en el futuro respecto a la hora de Colombia
    final LocalDate dia = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(3);

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "5", "501", encoder.encode("clave501")));
        bbq = zonaRepo.save(new ZonaComun(null, "BBQ", "Bueno", null, true));
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

    // ─── Choques por bloque de horas ─────────────────────────────────────────

    @Test
    void reservasQueSeCruzanChocan() throws Exception {
        reservar(bbq.getId(), dia, "10:00").andExpect(status().isOk());
        // 11:00 cae dentro de 10:00-12:00
        reservar(bbq.getId(), dia, "11:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(
                        "La zona ya está reservada el " + fecha(dia) + " de 10:00 a 12:00. Cada reserva ocupa 2 horas."));
        // 09:00-11:00 también se cruza
        reservar(bbq.getId(), dia, "09:00").andExpect(status().isConflict());
        // 12:00 empieza justo cuando termina la otra: no choca
        reservar(bbq.getId(), dia, "12:00").andExpect(status().isOk());
        // 08:00-10:00 termina justo cuando empieza la de las 10:00: no choca
        reservar(bbq.getId(), dia, "08:00").andExpect(status().isOk());
    }

    @Test
    void choqueQueCruzaLaMedianoche() throws Exception {
        reservar(bbq.getId(), dia, "23:00").andExpect(status().isOk());
        reservar(bbq.getId(), dia.plusDays(1), "00:30").andExpect(status().isConflict());
        reservar(bbq.getId(), dia.plusDays(1), "01:00").andExpect(status().isOk());
    }

    @Test
    void otraZonaYReservasRechazadasNoBloquean() throws Exception {
        ZonaComun salon = zonaRepo.save(new ZonaComun(null, "Salón", "Bueno", null, true));
        reservaRepo.save(new Reserva(null, dia, LocalTime.of(10, 0), "RECHAZADA", bbq, apto));
        reservar(bbq.getId(), dia, "10:00").andExpect(status().isOk());
        reservar(salon.getId(), dia, "10:00").andExpect(status().isOk());
    }

    // ─── Validaciones ────────────────────────────────────────────────────────

    @Test
    void zonaNoReservableDevuelve400() throws Exception {
        ZonaComun piscina = zonaRepo.save(new ZonaComun(null, "Piscina", "En mantenimiento", null, false));
        reservar(piscina.getId(), dia, "10:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La zona Piscina no se puede reservar"));
    }

    @Test
    void fechaPasadaDevuelve400() throws Exception {
        reservar(bbq.getId(), LocalDate.now(ZoneId.of("America/Bogota")).minusDays(1), "10:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No se puede reservar en una fecha u hora pasada"));
    }

    @Test
    void faltanDatosDevuelve400() throws Exception {
        mvc.perform(post("/api/reservas").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zonaComunId\":" + bbq.getId() + ",\"apartamentoId\":" + apto.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La zona, el apartamento, la fecha y la hora son obligatorios"));
    }

    // ─── Aprobación ──────────────────────────────────────────────────────────

    @Test
    void noSeApruebanDosReservasQueSeCruzan() throws Exception {
        // Datos antiguos: dos pendientes que se cruzan (antes solo chocaba la hora exacta)
        Reserva a = reservaRepo.save(new Reserva(null, dia, LocalTime.of(15, 0), "PENDIENTE", bbq, apto));
        Reserva b = reservaRepo.save(new Reserva(null, dia, LocalTime.of(16, 0), "PENDIENTE", bbq, apto));

        cambiarEstado(a.getId(), "APROBADA").andExpect(status().isOk());
        cambiarEstado(b.getId(), "APROBADA")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        assertThat(reservaRepo.findById(b.getId()).orElseThrow().getEstado()).isEqualTo("PENDIENTE");
        // Rechazarla sí se puede
        cambiarEstado(b.getId(), "RECHAZADA").andExpect(status().isOk());
    }

    // ─── Concurrencia ────────────────────────────────────────────────────────

    @Test
    void reservasSimultaneasDelMismoHorarioSoloGuardanUna() throws Exception {
        int hilos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                salida.await();
                return reservar(bbq.getId(), dia, "18:00").andReturn().getResponse().getStatus();
            }));
        }
        salida.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> f : resultados) estados.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(estados).filteredOn(s -> s == 200).hasSize(1);
        assertThat(estados).filteredOn(s -> s == 409).hasSize(hilos - 1);
        assertThat(reservaRepo.findAll()).hasSize(1);
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private ResultActions reservar(Long zonaId, LocalDate fecha, String hora) throws Exception {
        return mvc.perform(post("/api/reservas").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"" + fecha + "\",\"hora\":\"" + hora + "\",\"estado\":\"PENDIENTE\","
                        + "\"zonaComunId\":" + zonaId + ",\"apartamentoId\":" + apto.getId() + "}"));
    }

    private ResultActions cambiarEstado(Long id, String estado) throws Exception {
        return mvc.perform(put("/api/reservas/" + id + "/estado").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\"}"));
    }

    private static String fecha(LocalDate d) {
        return String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
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
