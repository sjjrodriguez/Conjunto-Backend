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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P5 bloque 8: chat entre la administración y el propietario o el arrendatario, cada uno con el suyo. */
@SpringBootTest
@AutoConfigureMockMvc
class ChatIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired MensajeChatRepository mensajeRepo;
    @Autowired ConversacionRepository conversacionRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    String admin;
    String contador;
    String propietario;
    String arrendatario;
    String vecino;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        adminRepo.save(new Administrador(null, "conta", encoder.encode("conta123"), Administrador.CONTADOR));
        apto = aptoRepo.save(new Apartamento(null, "5", "501", encoder.encode("clave501")));
        aptoRepo.save(new Apartamento(null, "5", "502", encoder.encode("clave502")));
        residenteRepo.save(new Residente(null, "Laura Dueña", "3001112233", "1111111", "PROPIETARIO", apto,
                encoder.encode("dueno123"), Residente.APROBADO));
        Residente arr = new Residente(null, "Pedro Arrienda", "3002223344", "2222222", "ARRENDATARIO", apto,
                encoder.encode("arrienda1"), Residente.APROBADO);
        arr.setTitular(true);
        arr.setAprobadoPropietario(true);
        residenteRepo.save(arr);
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        contador = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"conta\",\"contrasena\":\"conta123\"}");
        propietario = "Bearer " + token("/api/auth/residente/login",
                "{\"torre\":\"5\",\"apto\":\"501\",\"rol\":\"PROPIETARIO\",\"contrasena\":\"dueno123\"}");
        arrendatario = "Bearer " + token("/api/auth/residente/login",
                "{\"torre\":\"5\",\"apto\":\"501\",\"rol\":\"ARRENDATARIO\",\"contrasena\":\"arrienda1\"}");
        vecino = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"5\",\"apto\":\"502\",\"contrasena\":\"clave502\"}");
    }

    @AfterEach
    void limpiar() {
        mensajeRepo.deleteAll();
        conversacionRepo.deleteAll();
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
        residenteRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void elResidenteYLaAdministracionSeEscribenYCadaUnoVeSusNoLeidos() throws Exception {
        // El residente abre su chat (sin decir nada: su sesión dice cuál es)
        int chat = JsonPath.read(mvc.perform(post("/api/chat/conversaciones").header("Authorization", propietario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participante").value("PROPIETARIO"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        // Abrir otra vez da el mismo
        mvc.perform(post("/api/chat/conversaciones").header("Authorization", propietario)).andExpect(jsonPath("$.id").value(chat));
        // Vacío: la administración aún no lo ve en su bandeja
        mvc.perform(get("/api/chat/conversaciones").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(0));

        enviar(post("/api/chat/conversaciones/" + chat + "/mensajes"), propietario, "{\"texto\":\"  \"}").andExpect(status().isBadRequest());
        int primero = JsonPath.read(enviar(post("/api/chat/conversaciones/" + chat + "/mensajes"), propietario,
                        "{\"texto\":\"Hola, ¿cuándo arreglan el ascensor?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorNombre").value("Laura Dueña"))
                .andExpect(jsonPath("$.propio").value(true))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/chat/conversaciones").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].torre").value("5"))
                .andExpect(jsonPath("$[0].noLeidos").value(1))
                .andExpect(jsonPath("$[0].ultimoMensaje").value("Hola, ¿cuándo arreglan el ascensor?"));
        mvc.perform(get("/api/chat/conversaciones/" + chat + "/mensajes").header("Authorization", admin))
                .andExpect(jsonPath("$[0].propio").value(false));
        mvc.perform(put("/api/chat/conversaciones/" + chat + "/leido").header("Authorization", admin))
                .andExpect(jsonPath("$.noLeidos").value(0));

        enviar(post("/api/chat/conversaciones/" + chat + "/mensajes"), admin, "{\"texto\":\"Mañana en la mañana\"}")
                .andExpect(jsonPath("$.autorNombre").value("Administración"));
        // La app pide solo los nuevos
        mvc.perform(get("/api/chat/conversaciones/" + chat + "/mensajes?despuesDe=" + primero).header("Authorization", propietario))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].autor").value("ADMINISTRACION"))
                .andExpect(jsonPath("$[0].propio").value(false));
        mvc.perform(get("/api/chat/conversaciones").header("Authorization", propietario))
                .andExpect(jsonPath("$[0].noLeidos").value(1));
    }

    @Test
    void elArrendatarioTieneSuPropioChatYNadieMasLoVe() throws Exception {
        int delPropietario = JsonPath.read(mvc.perform(post("/api/chat/conversaciones").header("Authorization", propietario))
                .andReturn().getResponse().getContentAsString(), "$.id");
        // La administración abre el del arrendatario
        int delArrendatario = JsonPath.read(enviar(post("/api/chat/conversaciones"), admin,
                        "{\"apartamentoId\":" + apto.getId() + ",\"participante\":\"arrendatario\"}")
                .andExpect(jsonPath("$.participante").value("ARRENDATARIO"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/chat/conversaciones").header("Authorization", arrendatario)).andExpect(jsonPath("$.id").value(delArrendatario));

        mvc.perform(get("/api/chat/conversaciones/" + delPropietario + "/mensajes").header("Authorization", arrendatario))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/chat/conversaciones/" + delPropietario + "/mensajes").header("Authorization", vecino))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/chat/conversaciones").header("Authorization", contador)).andExpect(status().isForbidden());
        enviar(post("/api/chat/conversaciones"), admin, "{\"apartamentoId\":" + apto.getId() + ",\"participante\":\"vecino\"}")
                .andExpect(status().isBadRequest());
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
