package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P7 bloque 4: la administración crea guardias con PIN; en la recepción (cuenta de portería) cada
 * uno abre su turno con su PIN y las visitas que registra llevan su nombre.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GuardiasIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired GuardiaPorteriaRepository guardiaRepo;
    @Autowired VisitaPorteriaRepository visitaRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired PasswordEncoder encoder;

    String admin;
    String porteria;
    String residente;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        adminRepo.save(new Administrador(null, "recepcion", encoder.encode("recepcion123"), Administrador.PORTERIA));
        aptoRepo.save(new Apartamento(null, "3", "301", encoder.encode("clave301")));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        porteria = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"recepcion\",\"contrasena\":\"recepcion123\"}");
        residente = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"3\",\"apto\":\"301\",\"contrasena\":\"clave301\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        visitaRepo.deleteAll();
        guardiaRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void laAdministracionCreaGuardiasYLaRecepcionSoloVeSusNombres() throws Exception {
        enviar(post("/api/porteria/guardias"), admin, guardia("Carlos Ruiz", "79111222", "1234"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje", containsString("fácil")));
        enviar(post("/api/porteria/guardias"), admin, guardia("Carlos Ruiz", "79111222", "12a4"))
                .andExpect(status().isBadRequest());
        crearGuardia("Carlos Ruiz", "79111222", "2580");
        enviar(post("/api/porteria/guardias"), admin, guardia("Otro", "79111222", "4826"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/porteria/guardias").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documento").value("79111222"))
                .andExpect(jsonPath("$[0].pin").doesNotExist());
        // La recepción no maneja guardias: solo ve quién puede abrir turno
        mvc.perform(get("/api/porteria/guardias").header("Authorization", porteria)).andExpect(status().isForbidden());
        enviar(post("/api/porteria/guardias"), porteria, guardia("X", "1", "4826")).andExpect(status().isForbidden());
        mvc.perform(get("/api/porteria/turno/guardias").header("Authorization", porteria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Carlos Ruiz"))
                .andExpect(jsonPath("$[0].documento").doesNotExist());
        mvc.perform(get("/api/porteria/turno/guardias").header("Authorization", residente)).andExpect(status().isForbidden());
    }

    @Test
    void elTurnoSeAbreConElPinYLasVisitasLlevanElNombreDelGuardia() throws Exception {
        int carlos = crearGuardia("Carlos Ruiz", "79111222", "2580");
        enviar(post("/api/porteria/turno"), porteria, "{\"guardiaId\":" + carlos + ",\"pin\":\"1111\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("PIN incorrecto"));
        // La administración no abre turnos: es el celular de la recepción
        enviar(post("/api/porteria/turno"), admin, "{\"guardiaId\":" + carlos + ",\"pin\":\"2580\"}")
                .andExpect(status().isForbidden());

        String turno = "Bearer " + JsonPath.read(enviar(post("/api/porteria/turno"), porteria,
                        "{\"guardiaId\":" + carlos + ",\"pin\":\"2580\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("PORTERIA"))
                .andExpect(jsonPath("$.guardiaNombre").value("Carlos Ruiz"))
                .andReturn().getResponse().getContentAsString(), "$.token");

        enviar(post("/api/porteria/visitas"), turno, "{\"torre\":\"3\",\"apto\":\"301\",\"visitante\":\"Luisa\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.guardia").value("Carlos Ruiz"));
        // Sin turno (la app anterior) se registra igual, sin guardia
        enviar(post("/api/porteria/visitas"), porteria, "{\"torre\":\"3\",\"apto\":\"301\",\"visitante\":\"Mario\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.guardia").doesNotExist());
        mvc.perform(get("/api/porteria/visitas").header("Authorization", turno))
                .andExpect(jsonPath("$[?(@.visitante=='Luisa')].guardia", contains("Carlos Ruiz")));
        // El residente no ve quién estaba en la recepción
        mvc.perform(get("/api/porteria/visitas/pendientes").header("Authorization", residente))
                .andExpect(jsonPath("$[0].guardia").doesNotExist());
    }

    @Test
    void unGuardiaDeBajaYaNoAbreTurnoNiRegistraConElQueTenia() throws Exception {
        int ana = crearGuardia("Ana Gómez", "52333444", "1357");
        String turno = "Bearer " + JsonPath.read(enviar(post("/api/porteria/turno"), porteria,
                "{\"guardiaId\":" + ana + ",\"pin\":\"1357\"}").andReturn().getResponse().getContentAsString(), "$.token");

        enviar(put("/api/porteria/guardias/" + ana), admin, "{\"nombre\":\"Ana Gómez\",\"documento\":\"52333444\",\"activo\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));

        enviar(post("/api/porteria/visitas"), turno, "{\"torre\":\"3\",\"apto\":\"301\",\"visitante\":\"Luisa\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje", containsString("turno")));
        enviar(post("/api/porteria/turno"), porteria, "{\"guardiaId\":" + ana + ",\"pin\":\"1357\"}")
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/porteria/turno/guardias").header("Authorization", porteria))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void cambiarElPinYLimiteDeIntentos() throws Exception {
        int carlos = crearGuardia("Carlos Ruiz", "79111222", "2580");
        enviar(put("/api/porteria/guardias/" + carlos), admin, "{\"nombre\":\"Carlos Ruiz\",\"documento\":\"79111222\",\"pin\":\"4826\"}")
                .andExpect(status().isOk());
        enviar(post("/api/porteria/turno"), porteria, "{\"guardiaId\":" + carlos + ",\"pin\":\"2580\"}")
                .andExpect(status().isBadRequest());
        enviar(post("/api/porteria/turno"), porteria, "{\"guardiaId\":" + carlos + ",\"pin\":\"4826\"}")
                .andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            enviar(post("/api/porteria/turno"), porteria, "{\"guardiaId\":" + carlos + ",\"pin\":\"0001\"}");
        }
        enviar(post("/api/porteria/turno"), porteria, "{\"guardiaId\":" + carlos + ",\"pin\":\"4826\"}")
                .andExpect(status().isTooManyRequests());
    }

    private int crearGuardia(String nombre, String documento, String pin) throws Exception {
        return JsonPath.read(enviar(post("/api/porteria/guardias"), admin, guardia(nombre, documento, pin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pin").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String guardia(String nombre, String documento, String pin) {
        return "{\"nombre\":\"" + nombre + "\",\"documento\":\"" + documento + "\",\"pin\":\"" + pin + "\"}";
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String token, String cuerpo) throws Exception {
        return mvc.perform(peticion.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private String token(String ruta, String cuerpo) throws Exception {
        return JsonPath.read(mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }
}
