package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.*;
import com.residencial.push.EnviadorPush;
import com.residencial.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P4 bloque 2: cada notificación dice a qué lleva, se marca leída y se borra una por una, y
 * llega a los celulares del administrador registrados (con un enviador de prueba).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(NotificacionesAccionIntegrationTest.PushDePrueba.class)
class NotificacionesAccionIntegrationTest {

    /** Guarda lo que se habría enviado a Firebase y simula tokens que Firebase ya no acepta. */
    static class EnviadorDePrueba implements EnviadorPush {
        record Envio(List<String> tokens, Map<String, String> datos) {}
        final List<Envio> envios = new CopyOnWriteArrayList<>();
        volatile Set<String> invalidos = Set.of();

        @Override
        public Set<String> enviar(List<String> tokens, Map<String, String> datos) {
            envios.add(new Envio(tokens, datos));
            return invalidos;
        }
    }

    @TestConfiguration
    static class PushDePrueba {
        @Bean
        @Primary
        EnviadorDePrueba enviadorDePrueba() {
            return new EnviadorDePrueba();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired EnviadorDePrueba enviador;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired ZonaComunRepository zonaRepo;
    @Autowired ReservaRepository reservaRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    ZonaComun salon;
    String admin;
    String propietario;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "7", "701", encoder.encode("clave701")));
        salon = zonaRepo.save(new ZonaComun(null, "Salón", "Bueno", null, true));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        propietario = "Bearer " + token("/api/auth/apartamento/login",
                "{\"torre\":\"7\",\"apto\":\"701\",\"contrasena\":\"clave701\"}");
        enviador.envios.clear();
        enviador.invalidos = Set.of();
    }

    // La base H2 se comparte con los demás tests: no dejar filas que bloqueen sus borrados
    @AfterEach
    void limpiar() {
        dispositivoRepo.deleteAll();
        notificacionRepo.deleteAll();
        reservaRepo.deleteAll();
        zonaRepo.deleteAll();
        vehiculoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void laNotificacionDiceAQueLlevaYSeAbreYBorraUnaPorUna() throws Exception {
        String reserva = enviar(post("/api/reservas"), propietario, "{\"fecha\":\"" + dentroDe(3) + "\",\"hora\":\"10:00\","
                + "\"zonaComunId\":" + salon.getId() + ",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int reservaId = JsonPath.read(reserva, "$.id");

        String lista = mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("RESERVA"))
                .andExpect(jsonPath("$[0].referenciaId").value(reservaId))
                .andExpect(jsonPath("$[0].apartamentoId").value(apto.getId()))
                .andExpect(jsonPath("$[0].leida").value(false))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(lista, "$[0].id");

        // Abrirla la marca como leída, solo a ella
        mvc.perform(put("/api/notificaciones/" + id + "/leida").header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andExpect(jsonPath("$[0].leida").value(true));
        mvc.perform(get("/api/notificaciones/no-leidas").header("Authorization", admin))
                .andExpect(jsonPath("$.total").value(0));

        mvc.perform(delete("/api/notificaciones/" + id).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/notificaciones/" + id).header("Authorization", admin))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/notificaciones/" + id + "/leida").header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void elResidenteNoPuedeAbrirNiBorrarLasDelAdmin() throws Exception {
        // P4: el residente tiene sus propias notificaciones; las del admin le responden como si no
        // existieran (404), sin revelar que existen
        mvc.perform(put("/api/notificaciones/1/leida").header("Authorization", propietario))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/notificaciones/1").header("Authorization", propietario))
                .andExpect(status().isNotFound());
    }

    @Test
    void elAvisoLlegaSoloALosCelularesDelAdmin() throws Exception {
        enviar(post("/api/dispositivos"), admin, "{\"token\":\"celular-admin\"}").andExpect(status().isNoContent());
        enviar(post("/api/dispositivos"), propietario, "{\"token\":\"celular-residente\"}").andExpect(status().isNoContent());

        String vehiculo = enviar(post("/api/vehiculos"), propietario,
                "{\"placa\":\"QWE123\",\"tipo\":\"Carro\",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int vehiculoId = JsonPath.read(vehiculo, "$.id");

        EnviadorDePrueba.Envio envio = esperarEnvio();
        assertThat(envio.tokens()).containsExactly("celular-admin");
        assertThat(envio.datos())
                .containsEntry("tipo", "VEHICULO")
                .containsEntry("titulo", "Nuevo vehículo por aprobar")
                .containsEntry("referenciaId", String.valueOf(vehiculoId))
                .containsEntry("apartamentoId", String.valueOf(apto.getId()))
                .containsKey("notificacionId");
        assertThat(envio.datos().get("mensaje")).contains("QWE123");
    }

    @Test
    void losTokensQueFirebaseRechazaSeBorran() throws Exception {
        enviar(post("/api/dispositivos"), admin, "{\"token\":\"celular-viejo\"}").andExpect(status().isNoContent());
        enviador.invalidos = Set.of("celular-viejo");

        enviar(post("/api/vehiculos"), propietario,
                "{\"placa\":\"RTY456\",\"tipo\":\"Moto\",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isOk());

        esperarEnvio();
        for (int i = 0; i < 50 && dispositivoRepo.findByToken("celular-viejo").isPresent(); i++) Thread.sleep(100);
        assertThat(dispositivoRepo.findByToken("celular-viejo")).isEmpty();
    }

    @Test
    void elMismoCelularCambiaDeUsuarioYSeDaDeBajaAlCerrarSesion() throws Exception {
        enviar(post("/api/dispositivos"), propietario, "{\"token\":\"celular-compartido\"}").andExpect(status().isNoContent());
        assertThat(dispositivoRepo.findByToken("celular-compartido")).get()
                .extracting(Dispositivo::getRol).isEqualTo("PROPIETARIO");

        // Luego inicia sesión el administrador en ese mismo celular
        enviar(post("/api/dispositivos"), admin, "{\"token\":\"celular-compartido\"}").andExpect(status().isNoContent());
        assertThat(dispositivoRepo.findAll()).hasSize(1);
        assertThat(dispositivoRepo.findByToken("celular-compartido")).get()
                .extracting(Dispositivo::getRol).isEqualTo("ADMIN");

        // Al cerrar sesión la app ya no tiene token de sesión: basta con el del celular
        mvc.perform(post("/api/dispositivos/baja").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"celular-compartido\"}"))
                .andExpect(status().isNoContent());
        assertThat(dispositivoRepo.findAll()).isEmpty();
    }

    @Test
    void registrarUnCelularExigeSesionYToken() throws Exception {
        mvc.perform(post("/api/dispositivos").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        enviar(post("/api/dispositivos"), admin, "{\"token\":\"\"}").andExpect(status().isBadRequest());
    }

    private EnviadorDePrueba.Envio esperarEnvio() throws InterruptedException {
        // El envío va en segundo plano (@Async)
        for (int i = 0; i < 50 && enviador.envios.isEmpty(); i++) Thread.sleep(100);
        assertThat(enviador.envios).hasSize(1);
        return enviador.envios.get(0);
    }

    private static LocalDate dentroDe(int dias) {
        return LocalDate.now(ZoneId.of("America/Bogota")).plusDays(dias);
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
