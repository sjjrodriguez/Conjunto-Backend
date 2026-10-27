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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P5 bloque 5: horario por día de la semana, fechas bloqueadas y editar la reserva pendiente. */
@SpringBootTest
@AutoConfigureMockMvc
class ZonasHorarioIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired ZonaComunRepository zonaRepo;
    @Autowired ReservaRepository reservaRepo;
    @Autowired ZonaDisponibilidadRepository dispRepo;
    @Autowired ZonaBloqueoRepository bloqueoRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    ZonaComun zona;
    String admin;
    String propietario;
    String vecino;
    // Un lunes y el sábado siguiente, a más de una semana
    LocalDate lunes = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(7).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    LocalDate sabado = lunes.plusDays(5);

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "1", "101", encoder.encode("clave101")));
        aptoRepo.save(new Apartamento(null, "1", "102", encoder.encode("clave102")));
        zona = zonaRepo.save(new ZonaComun(null, "Salón social", "Bueno", null, true));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        propietario = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"1\",\"apto\":\"101\",\"contrasena\":\"clave101\"}");
        vecino = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"1\",\"apto\":\"102\",\"contrasena\":\"clave102\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
        reservaRepo.deleteAll();
        bloqueoRepo.deleteAll();
        dispRepo.deleteAll();
        zonaRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void elHorarioSemanalYLasFechasBloqueadasLimitanLasReservas() throws Exception {
        // De lunes a viernes, de 8 a 20
        StringBuilder dias = new StringBuilder();
        for (int d = 1; d <= 5; d++) dias.append(d > 1 ? "," : "").append("{\"diaSemana\":").append(d).append(",\"apertura\":\"08:00\",\"cierre\":\"20:00\"}");
        enviar(put("/api/zonas/" + zona.getId() + "/horario"), admin, "{\"semanal\":[" + dias + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semanal.length()").value(5));
        enviar(put("/api/zonas/" + zona.getId() + "/horario"), propietario, "{\"semanal\":[]}").andExpect(status().isForbidden());
        enviar(put("/api/zonas/" + zona.getId() + "/horario"), admin,
                "{\"semanal\":[{\"diaSemana\":1,\"apertura\":\"20:00\",\"cierre\":\"08:00\"}]}").andExpect(status().isBadRequest());

        reservar(sabado, "10:00", "12:00").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("no abre los sábado")));
        reservar(lunes, "19:00", "21:00").andExpect(status().isBadRequest());

        int bloqueo = JsonPath.read(enviar(post("/api/zonas/" + zona.getId() + "/bloqueos"), admin,
                "{\"fecha\":\"" + lunes + "\",\"motivo\":\"Mantenimiento\"}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id");
        reservar(lunes, "10:00", "12:00").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("Mantenimiento")));
        mvc.perform(get("/api/zonas/" + zona.getId() + "/disponibilidad?desde=" + lunes + "&hasta=" + lunes.plusDays(1))
                        .header("Authorization", propietario))
                .andExpect(jsonPath("$.dias[0].abierta").value(false))
                .andExpect(jsonPath("$.dias[0].motivo").value("Mantenimiento"))
                .andExpect(jsonPath("$.dias[1].abierta").value(true))
                .andExpect(jsonPath("$.dias[1].apertura").value("08:00:00"));

        mvc.perform(delete("/api/zonas/" + zona.getId() + "/bloqueos/" + bloqueo).header("Authorization", admin))
                .andExpect(status().isNoContent());
        reservar(lunes, "10:00", "12:00").andExpect(status().isOk());
    }

    @Test
    void laReservaPendienteSeEditaYYaRevisadaNo() throws Exception {
        int id = JsonPath.read(reservar(lunes, "10:00", "12:00").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id");
        // Mover una hora (se cruza con ella misma: no cuenta)
        enviar(put("/api/reservas/" + id), propietario,
                "{\"fecha\":\"" + lunes + "\",\"hora\":\"11:00\",\"horaFin\":\"13:00\",\"asistentes\":10}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hora").value("11:00:00"))
                .andExpect(jsonPath("$.asistentes").value(10));
        enviar(put("/api/reservas/" + id), vecino, "{\"fecha\":\"" + lunes + "\",\"hora\":\"14:00\"}")
                .andExpect(status().isForbidden());

        enviar(put("/api/reservas/" + id + "/estado"), admin, "{\"estado\":\"APROBADA\"}").andExpect(status().isOk());
        enviar(put("/api/reservas/" + id), propietario, "{\"fecha\":\"" + lunes + "\",\"hora\":\"15:00\",\"horaFin\":\"16:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("pendiente")));

        mvc.perform(get("/api/reservas?estado=aprobada").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/reservas?estado=pendiente").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions reservar(LocalDate fecha, String hora, String fin) throws Exception {
        return enviar(post("/api/reservas"), propietario, "{\"zonaComunId\":" + zona.getId() + ",\"apartamentoId\":" + apto.getId()
                + ",\"fecha\":\"" + fecha + "\",\"hora\":\"" + hora + "\",\"horaFin\":\"" + fin + "\"}");
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String auth, String json) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(String url, String json) throws Exception {
        String respuesta = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
