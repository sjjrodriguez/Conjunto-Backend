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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P4 bloque 5: reservas con hora de fin y asistentes, horario y aforo de la zona, y la
 * disponibilidad día por día para el calendario.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservasHorarioIntegrationTest {

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
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    Apartamento vecino;
    ZonaComun salon;
    String admin;
    String propietario;
    String otroPropietario;
    // Siempre en el futuro respecto a la hora de Colombia
    final LocalDate dia = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(3);

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "4", "401", encoder.encode("clave401")));
        vecino = aptoRepo.save(new Apartamento(null, "4", "402", encoder.encode("clave402")));
        // Aforo 30, se puede reservar de 08:00 a 20:00
        salon = zonaRepo.save(new ZonaComun(null, "Salón", "Bueno", null, true, 30, LocalTime.of(8, 0), LocalTime.of(20, 0)));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        propietario = "Bearer " + token("/api/auth/apartamento/login",
                "{\"torre\":\"4\",\"apto\":\"401\",\"contrasena\":\"clave401\"}");
        otroPropietario = "Bearer " + token("/api/auth/apartamento/login",
                "{\"torre\":\"4\",\"apto\":\"402\",\"contrasena\":\"clave402\"}");
    }

    // La base H2 se comparte con los demás tests: no dejar filas que bloqueen sus borrados
    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
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
    void reservaConHoraDeFinYAsistentesYBloqueaEsaFranja() throws Exception {
        reservar(propietario, apto, dia, "10:00", "13:00", 12)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hora").value("10:00:00"))
                .andExpect(jsonPath("$.horaInicio").value("10:00:00"))
                .andExpect(jsonPath("$.horaFin").value("13:00:00"))
                .andExpect(jsonPath("$.asistentes").value(12));

        // Otro apartamento no puede reservar algo que se cruce
        reservar(otroPropietario, vecino, dia, "12:00", "14:00", 5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La zona ya está reservada el " + fecha(dia) + " de 10:00 a 13:00."));
        // Justo después sí
        reservar(otroPropietario, vecino, dia, "13:00", "15:00", 5).andExpect(status().isOk());
    }

    @Test
    void validaHoraDeFinDuracionAforoYHorario() throws Exception {
        reservar(propietario, apto, dia, "10:00", "10:00", 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La hora de fin debe ser después de la de inicio"));
        // Máximo 8 horas por defecto (y además pasaría del cierre)
        reservar(propietario, apto, dia, "09:00", "18:00", 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Una reserva puede durar máximo 8 horas"));
        reservar(propietario, apto, dia, "10:00", "12:00", 31)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El aforo de Salón es de 30 personas"));
        reservar(propietario, apto, dia, "10:00", "12:00", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Debe asistir al menos una persona"));
        reservar(propietario, apto, dia, "07:00", "09:00", 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Salón se puede reservar de 08:00 a 20:00"));
        reservar(propietario, apto, dia, "19:00", "21:00", 5).andExpect(status().isBadRequest());
        // Sin hora de fin (app anterior) dura 2 horas y también debe caber en el horario
        reservarSinFin(propietario, apto, dia, "19:00").andExpect(status().isBadRequest());
    }

    @Test
    void sinHoraDeFinSigueDurandoDosHoras() throws Exception {
        reservarSinFin(propietario, apto, dia, "10:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaFin").value("12:00:00"))
                .andExpect(jsonPath("$.asistentes").doesNotExist());
        reservar(otroPropietario, vecino, dia, "11:00", "12:00", 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("Cada reserva ocupa 2 horas")));
    }

    @Test
    void laDisponibilidadMuestraLoOcupadoSinDecirDeQuienEs() throws Exception {
        reservar(propietario, apto, dia, "15:00", "17:00", 10).andExpect(status().isOk());
        int ajena = reservar(otroPropietario, vecino, dia, "09:00", "11:00", 4).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(r -> JsonPath.read(r, "$.id"));
        // Una rechazada ya no ocupa
        reservar(otroPropietario, vecino, dia.plusDays(1), "09:00", "10:00", 2).andExpect(status().isOk());
        int rechazada = reservaRepo.findAll().stream().filter(r -> r.getFecha().equals(dia.plusDays(1))).findFirst()
                .orElseThrow().getId().intValue();
        mvc.perform(put("/api/reservas/" + rechazada + "/estado").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"RECHAZADA\"}"));

        mvc.perform(get("/api/zonas/" + salon.getId() + "/disponibilidad")
                        .param("desde", dia.toString()).param("hasta", dia.plusDays(1).toString())
                        .header("Authorization", propietario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aforo").value(30))
                .andExpect(jsonPath("$.horaApertura").value("08:00:00"))
                .andExpect(jsonPath("$.horaCierre").value("20:00:00"))
                .andExpect(jsonPath("$.dias.length()").value(2))
                .andExpect(jsonPath("$.dias[0].fecha").value(dia.toString()))
                .andExpect(jsonPath("$.dias[0].ocupadas.length()").value(2))
                // Ordenadas por hora; la ajena no es "propia" y no dice de qué apartamento es
                .andExpect(jsonPath("$.dias[0].ocupadas[0].inicio").value("09:00:00"))
                .andExpect(jsonPath("$.dias[0].ocupadas[0].propia").value(false))
                .andExpect(jsonPath("$.dias[0].ocupadas[0].apartamentoId").doesNotExist())
                .andExpect(jsonPath("$.dias[0].ocupadas[1].inicio").value("15:00:00"))
                .andExpect(jsonPath("$.dias[0].ocupadas[1].fin").value("17:00:00"))
                .andExpect(jsonPath("$.dias[0].ocupadas[1].propia").value(true))
                .andExpect(jsonPath("$.dias[1].ocupadas.length()").value(0))
                .andExpect(content().string(not(containsString("\"402\""))));
        assert ajena > 0;
    }

    @Test
    void unaReservaDeNocheSigueEnElDiaSiguiente() throws Exception {
        // Zona sin horario, reserva de la app anterior a las 23:00 (dura 2 horas)
        ZonaComun bbq = zonaRepo.save(new ZonaComun(null, "BBQ", "Bueno", null, true));
        reservarEn(bbq, propietario, apto, dia, "{\"hora\":\"23:00\"}").andExpect(status().isOk());

        mvc.perform(get("/api/zonas/" + bbq.getId() + "/disponibilidad")
                        .param("desde", dia.toString()).param("hasta", dia.plusDays(1).toString())
                        .header("Authorization", propietario))
                .andExpect(jsonPath("$.dias[0].ocupadas[0].inicio").value("23:00:00"))
                .andExpect(jsonPath("$.dias[0].ocupadas[0].fin").value("23:59:59"))
                .andExpect(jsonPath("$.dias[1].ocupadas[0].inicio").value("00:00:00"))
                .andExpect(jsonPath("$.dias[1].ocupadas[0].fin").value("01:00:00"));
    }

    @Test
    void elRangoDeLaDisponibilidadSeValida() throws Exception {
        mvc.perform(get("/api/zonas/" + salon.getId() + "/disponibilidad")
                        .param("desde", dia.toString()).param("hasta", dia.minusDays(1).toString())
                        .header("Authorization", propietario))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/zonas/" + salon.getId() + "/disponibilidad")
                        .param("desde", dia.toString()).param("hasta", dia.plusDays(70).toString())
                        .header("Authorization", propietario))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Se pueden consultar máximo 62 días a la vez"));
    }

    @Test
    void elAdministradorConfiguraAforoYHorarioDeLaZona() throws Exception {
        mvc.perform(post("/api/zonas").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Terraza\",\"estadoRecibido\":\"Bueno\",\"esReservable\":true,"
                                + "\"aforo\":15,\"horaApertura\":\"07:00\",\"horaCierre\":\"21:30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aforo").value(15))
                .andExpect(jsonPath("$.horaCierre").value("21:30:00"));
        mvc.perform(put("/api/zonas/" + salon.getId()).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Salón\",\"estadoRecibido\":\"Bueno\",\"esReservable\":true,"
                                + "\"aforo\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El aforo debe ser de al menos una persona"));
        mvc.perform(put("/api/zonas/" + salon.getId()).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Salón\",\"estadoRecibido\":\"Bueno\",\"esReservable\":true,"
                                + "\"horaApertura\":\"20:00\",\"horaCierre\":\"08:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La hora de cierre debe ser después de la de apertura"));
    }

    private ResultActions reservar(String auth, Apartamento a, LocalDate fecha, String inicio, String fin, int asistentes)
            throws Exception {
        return reservarEn(salon, auth, a, fecha,
                "{\"horaInicio\":\"" + inicio + "\",\"horaFin\":\"" + fin + "\",\"asistentes\":" + asistentes + "}");
    }

    private ResultActions reservarSinFin(String auth, Apartamento a, LocalDate fecha, String hora) throws Exception {
        return reservarEn(salon, auth, a, fecha, "{\"hora\":\"" + hora + "\"}");
    }

    // Une los campos de la hora con la zona, la fecha y el apartamento
    private ResultActions reservarEn(ZonaComun zona, String auth, Apartamento a, LocalDate fecha, String horas)
            throws Exception {
        String json = horas.substring(0, horas.length() - 1) + ",\"fecha\":\"" + fecha + "\",\"zonaComunId\":"
                + zona.getId() + ",\"apartamentoId\":" + a.getId() + "}";
        return mvc.perform(post("/api/reservas").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String fecha(LocalDate d) {
        return String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    private String token(String url, String json) throws Exception {
        String respuesta = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
