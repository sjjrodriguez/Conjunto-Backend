package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.VisitaPorteria;
import com.residencial.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P10 bloque 7: foto del visitante y del paquete, y el chat temporal cuando rechazan una visita. */
@SpringBootTest
@AutoConfigureMockMvc
class FotosYChatPorteriaIntegrationTest {

    // Lo mínimo para que el contenido se reconozca como JPEG
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired VisitaPorteriaRepository visitaRepo;
    @Autowired MensajeVisitaRepository mensajeRepo;
    @Autowired CorrespondenciaRepository correspondenciaRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired PasswordEncoder encoder;

    String porteria;
    String residente7;
    String residente8;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "portero1", encoder.encode("portero123"), Administrador.PORTERIA));
        aptoRepo.save(new Apartamento(null, "7", "701", encoder.encode("clave701")));
        aptoRepo.save(new Apartamento(null, "8", "801", encoder.encode("clave801")));
        porteria = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"portero1\",\"contrasena\":\"portero123\"}");
        residente7 = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"7\",\"apto\":\"701\",\"contrasena\":\"clave701\"}");
        residente8 = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"8\",\"apto\":\"801\",\"contrasena\":\"clave801\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        mensajeRepo.deleteAll();
        visitaRepo.deleteAll();
        correspondenciaRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void laVisitaLlegaConLaFotoDelVisitanteSoloParaSuApartamento() throws Exception {
        String foto = subirFoto(porteria).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String fotoId = JsonPath.read(foto, "$.fotoId");

        int id = JsonPath.read(enviar(post("/api/porteria/visitas"), porteria,
                        "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"Ana Gómez\",\"fotoId\":\"" + fotoId + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tieneFoto").value(true))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/porteria/visitas/pendientes").header("Authorization", residente7))
                .andExpect(jsonPath("$[0].tieneFoto").value(true)).andExpect(jsonPath("$[0].fotoUrl", notNullValue()));
        mvc.perform(get("/api/porteria/visitas/" + id + "/foto").header("Authorization", residente7))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url", notNullValue()));
        mvc.perform(get("/api/porteria/visitas/" + id + "/foto").header("Authorization", residente8))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/porteria/visitas/" + id + "/foto").header("Authorization", porteria))
                .andExpect(status().isOk());
    }

    @Test
    void soloLaPorteriaSubeFotosYSoloJpgOPng() throws Exception {
        subirFoto(residente7).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/porteria/fotos").file(new MockMultipartFile("archivo", "x.txt", "text/plain", "hola".getBytes()))
                .header("Authorization", porteria)).andExpect(status().isBadRequest());
        // Un fotoId inventado (o con otra ruta) no se acepta
        enviar(post("/api/porteria/visitas"), porteria,
                "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"Ana\",\"fotoId\":\"../conjunto-2/abc.jpg\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void alRechazarSeAbreElChatEntreElResidenteYLaPorteria() throws Exception {
        int id = JsonPath.read(enviar(post("/api/porteria/visitas"), porteria,
                        "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"Pedro\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        // Sin rechazar no hay chat
        mvc.perform(get("/api/porteria/visitas/" + id + "/chat").header("Authorization", residente7)).andExpect(status().isConflict());

        enviar(patch("/api/porteria/visitas/" + id + "/respuesta"), residente7, "{\"decision\":\"RECHAZAR\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.chatHasta", notNullValue()));
        mvc.perform(get("/api/porteria/visitas/chats").header("Authorization", residente7))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/porteria/visitas/chats").header("Authorization", residente8))
                .andExpect(jsonPath("$.length()").value(0));

        enviar(post("/api/porteria/visitas/" + id + "/chat"), residente7, "{\"texto\":\"No espero a nadie\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.deResidente").value(true));
        enviar(post("/api/porteria/visitas/" + id + "/chat"), porteria, "{\"texto\":\"Entendido, no sigue\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.deResidente").value(false));
        enviar(post("/api/porteria/visitas/" + id + "/chat"), residente8, "{\"texto\":\"Hola\"}")
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/porteria/visitas/" + id + "/chat").header("Authorization", porteria))
                .andExpect(jsonPath("$.abierto").value(true)).andExpect(jsonPath("$.mensajes.length()").value(2))
                .andExpect(jsonPath("$.mensajes[0].texto").value("No espero a nadie"));
        // La portería ve cuántos mensajes escribió el residente
        mvc.perform(get("/api/porteria/visitas").header("Authorization", porteria))
                .andExpect(jsonPath("$[0].mensajesResidente").value(1));

        // Pasada la media hora, el chat se cierra
        VisitaPorteria v = visitaRepo.findById((long) id).orElseThrow();
        v.setRespondidaEn(LocalDateTime.now().minusMinutes(VisitaPorteria.MINUTOS_CHAT + 1));
        visitaRepo.save(v);
        enviar(post("/api/porteria/visitas/" + id + "/chat"), residente7, "{\"texto\":\"¿Sigue ahí?\"}")
                .andExpect(status().isConflict());
        mvc.perform(get("/api/porteria/visitas/" + id + "/chat").header("Authorization", residente7))
                .andExpect(jsonPath("$.abierto").value(false));
    }

    @Test
    void elPaqueteLlegaConFotoParaSuApartamento() throws Exception {
        String fotoId = JsonPath.read(subirFoto(porteria).andReturn().getResponse().getContentAsString(), "$.fotoId");
        int id = JsonPath.read(enviar(post("/api/porteria/correspondencia"), porteria,
                        "{\"torre\":\"7\",\"apto\":\"701\",\"tipo\":\"PAQUETE\",\"remitente\":\"Rappi\",\"fotoId\":\"" + fotoId + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tieneFoto").value(true))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/porteria/correspondencia/mia").header("Authorization", residente7))
                .andExpect(jsonPath("$[0].tieneFoto").value(true));
        mvc.perform(get("/api/porteria/correspondencia/" + id + "/foto").header("Authorization", residente7))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url", notNullValue()));
        mvc.perform(get("/api/porteria/correspondencia/" + id + "/foto").header("Authorization", residente8))
                .andExpect(status().isForbidden());
        assertThat(correspondenciaRepo.findById((long) id).orElseThrow().getFotoRuta()).startsWith("porteria/conjunto-");
    }

    private ResultActions subirFoto(String auth) throws Exception {
        return mvc.perform(multipart("/api/porteria/fotos").file(new MockMultipartFile("archivo", "cara.jpg", "image/jpeg", JPEG))
                .header("Authorization", auth));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String auth, String cuerpo) throws Exception {
        return mvc.perform(peticion.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private String token(String url, String cuerpo) throws Exception {
        String r = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(r, "$.token");
    }
}
