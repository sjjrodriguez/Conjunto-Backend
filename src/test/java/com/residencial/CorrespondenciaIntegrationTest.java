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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P9 bloque 3: paquetes, cartas y recibos en la portería, su aviso y la entrega con quien recoge. */
@SpringBootTest
@AutoConfigureMockMvc
class CorrespondenciaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired CorrespondenciaRepository correspondenciaRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired PasswordEncoder encoder;

    String admin;
    String porteria;
    String residente502;
    String residente1001;
    Long apto502;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        adminRepo.save(new Administrador(null, "portero1", encoder.encode("portero123"), Administrador.PORTERIA));
        apto502 = aptoRepo.save(new Apartamento(null, "5", "502", encoder.encode("clave502"))).getId();
        aptoRepo.save(new Apartamento(null, "10", "1001", encoder.encode("clave1001")));
        aptoRepo.save(new Apartamento(null, "9", "901", encoder.encode("x")));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        porteria = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"portero1\",\"contrasena\":\"portero123\"}");
        residente502 = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"5\",\"apto\":\"502\",\"contrasena\":\"clave502\"}");
        residente1001 = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"10\",\"apto\":\"1001\",\"contrasena\":\"clave1001\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        correspondenciaRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void paqueteAvisaAlApartamentoYSeEntregaConQuienRecoge() throws Exception {
        int id = JsonPath.read(enviar(post("/api/porteria/correspondencia"), porteria,
                        "{\"torre\":\"5\",\"apto\":\"502\",\"tipo\":\"paquete\",\"remitente\":\"Servientrega\",\"nota\":\"Caja mediana\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE")).andExpect(jsonPath("$.tipo").value("PAQUETE"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(notificacionRepo.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getTipo()).isEqualTo("CORRESPONDENCIA");
            assertThat(n.getDestinatarioApartamentoId()).isEqualTo(apto502);
            assertThat(n.getMensaje()).contains("paquete de Servientrega").contains("Caja mediana");
        });

        // El residente la ve; otro apartamento no
        mvc.perform(get("/api/porteria/correspondencia/mia").header("Authorization", residente502)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].remitente").value("Servientrega"));
        mvc.perform(get("/api/porteria/correspondencia/mia").header("Authorization", residente1001)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/porteria/resumen").header("Authorization", porteria))
                .andExpect(jsonPath("$.correspondenciaPendiente").value(1));

        // P12 bloque 3: sin la autorización del apartamento no se entrega
        enviar(post("/api/porteria/correspondencia/" + id + "/entrega"), porteria, "{\"recibidaPor\":\"Laura Muñoz\"}")
                .andExpect(status().isConflict());
        // Sin nombre no se pide
        enviar(post("/api/porteria/correspondencia/" + id + "/solicitud"), porteria, "{\"recoge\":\" \"}")
                .andExpect(status().isBadRequest());
        enviar(post("/api/porteria/correspondencia/" + id + "/solicitud"), porteria, "{\"recoge\":\"Laura Muñoz\",\"documento\":\"52.111\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.solicitud.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.solicitud.recoge").value("Laura Muñoz"));
        // Mientras espera no se pide otra vez
        enviar(post("/api/porteria/correspondencia/" + id + "/solicitud"), porteria, "{\"recoge\":\"Otra\"}")
                .andExpect(status().isConflict());
        assertThat(notificacionRepo.findAll()).anySatisfy(n -> {
            assertThat(n.getTipo()).isEqualTo("ENTREGA_CORRESPONDENCIA");
            assertThat(n.getDestinatarioApartamentoId()).isEqualTo(apto502);
            assertThat(n.getMensaje()).startsWith("Laura Muñoz quiere recoger un paquete de Servientrega");
        });
        mvc.perform(get("/api/porteria/correspondencia/solicitudes").header("Authorization", residente502))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].solicitud.documento").value("52.111"));
        mvc.perform(get("/api/porteria/correspondencia/solicitudes").header("Authorization", residente1001))
                .andExpect(jsonPath("$", hasSize(0)));
        // Otro apartamento no responde; el 502 autoriza (y no dos veces)
        enviar(patch("/api/porteria/correspondencia/" + id + "/solicitud/respuesta"), residente1001, "{\"decision\":\"AUTORIZAR\"}")
                .andExpect(status().isForbidden());
        enviar(patch("/api/porteria/correspondencia/" + id + "/solicitud/respuesta"), residente502, "{\"decision\":\"AUTORIZAR\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.solicitud.estado").value("AUTORIZADA"))
                .andExpect(jsonPath("$.solicitud.canal").value("APP"));
        enviar(patch("/api/porteria/correspondencia/" + id + "/solicitud/respuesta"), residente502, "{\"decision\":\"RECHAZAR\"}")
                .andExpect(status().isConflict());
        // Sin nombre se entrega a quien autorizaron
        enviar(post("/api/porteria/correspondencia/" + id + "/entrega"), porteria, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADA")).andExpect(jsonPath("$.entregadaA").value("Laura Muñoz"))
                .andExpect(jsonPath("$.entregadaPor").value("portero1"));
        // Dos veces no
        enviar(post("/api/porteria/correspondencia/" + id + "/entrega"), porteria, "{\"recibidaPor\":\"Otro\"}")
                .andExpect(status().isConflict());

        assertThat(notificacionRepo.findAll()).anySatisfy(n -> {
            assertThat(n.getTipo()).isEqualTo("CORRESPONDENCIA_ENTREGADA");
            assertThat(n.getMensaje()).startsWith("Un paquete de Servientrega se entregó a Laura Muñoz");
        });
        mvc.perform(get("/api/porteria/correspondencia").header("Authorization", porteria))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/porteria/correspondencia?estado=ENTREGADA&q=laura").header("Authorization", porteria))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void loEntregadoSeQuitaDeCadaListaSinBorrarElRegistro() throws Exception {
        // P11 bloque 2: el residente y la portería lo quitan cada uno de su lista
        int id = JsonPath.read(enviar(post("/api/porteria/correspondencia"), porteria,
                        "{\"torre\":\"5\",\"apto\":\"502\",\"tipo\":\"paquete\",\"remitente\":\"Rappi\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        // Pendiente: todavía no se puede quitar
        mvc.perform(delete("/api/porteria/correspondencia/mia/" + id).header("Authorization", residente502))
                .andExpect(status().isBadRequest());
        enviar(post("/api/porteria/correspondencia/" + id + "/solicitud"), porteria, "{\"recoge\":\"Laura\"}").andExpect(status().isOk());
        // La portería registra la autorización dada por llamada
        enviar(patch("/api/porteria/correspondencia/" + id + "/solicitud/respuesta"), porteria, "{\"decision\":\"AUTORIZAR\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.solicitud.canal").value("LLAMADA"));
        enviar(post("/api/porteria/correspondencia/" + id + "/entrega"), porteria, "{\"recibidaPor\":\"Laura\"}")
                .andExpect(status().isOk());

        // Otro apartamento no la encuentra; la portería no usa la ruta del residente
        mvc.perform(delete("/api/porteria/correspondencia/mia/" + id).header("Authorization", residente1001))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/porteria/correspondencia/mia/" + id).header("Authorization", porteria))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/porteria/correspondencia/" + id).header("Authorization", residente502))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/porteria/correspondencia/mia/" + id).header("Authorization", residente502))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/porteria/correspondencia/mia").header("Authorization", residente502))
                .andExpect(jsonPath("$", hasSize(0)));
        // La portería todavía la ve hasta quitarla ella
        mvc.perform(get("/api/porteria/correspondencia?estado=ENTREGADA").header("Authorization", porteria))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/api/porteria/correspondencia/" + id).header("Authorization", porteria))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/porteria/correspondencia?estado=ENTREGADA").header("Authorization", porteria))
                .andExpect(jsonPath("$", hasSize(0)));
        // El registro sigue en la base
        assertThat(correspondenciaRepo.findById((long) id)).isPresent();
    }

    @Test
    void reciboParaTodosLlegaACadaApartamento() throws Exception {
        enviar(post("/api/porteria/correspondencia/recibos"), admin, "{\"remitente\":\"Enel\",\"nota\":\"Octubre\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.creadas").value(3));

        assertThat(correspondenciaRepo.findAll()).hasSize(3).allSatisfy(c -> assertThat(c.getTipo()).isEqualTo("RECIBO"));
        assertThat(notificacionRepo.findAll()).hasSize(3).allSatisfy(n -> {
            assertThat(n.getTipo()).isEqualTo("RECIBO");
            assertThat(n.getMensaje()).contains("el recibo de Enel");
        });
        // La búsqueda por apartamento en la portería
        mvc.perform(get("/api/porteria/correspondencia?q=10-1001").header("Authorization", porteria))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].apto").value("1001"));
        // Sin empresa no
        enviar(post("/api/porteria/correspondencia/recibos"), porteria, "{\"remitente\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void validacionesYPermisos() throws Exception {
        enviar(post("/api/porteria/correspondencia"), porteria, "{\"torre\":\"7\",\"apto\":\"700\",\"tipo\":\"CARTA\"}")
                .andExpect(status().isNotFound());
        enviar(post("/api/porteria/correspondencia"), porteria, "{\"torre\":\"5\",\"apto\":\"502\",\"tipo\":\"MUEBLE\"}")
                .andExpect(status().isBadRequest());
        // El residente no registra ni entrega ni ve la de todos
        enviar(post("/api/porteria/correspondencia"), residente502, "{\"torre\":\"5\",\"apto\":\"502\",\"tipo\":\"CARTA\"}")
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/porteria/correspondencia").header("Authorization", residente502)).andExpect(status().isForbidden());
        // La portería no usa "mia"
        mvc.perform(get("/api/porteria/correspondencia/mia").header("Authorization", porteria)).andExpect(status().isForbidden());
        // El mensaje del aviso de una carta sin remitente
        enviar(post("/api/porteria/correspondencia"), porteria, "{\"torre\":\"5\",\"apto\":\"502\",\"tipo\":\"CARTA\"}")
                .andExpect(status().isCreated());
        assertThat(notificacionRepo.findAll()).singleElement()
                .satisfies(n -> assertThat(n.getMensaje()).isEqualTo("Llegó una carta a la portería. Recógelo cuando puedas"));
    }

    @Test
    void siElResidenteRechazaLaEntregaHayChatYSePuedePedirOtraVez() throws Exception {
        int id = JsonPath.read(enviar(post("/api/porteria/correspondencia"), porteria,
                        "{\"torre\":\"5\",\"apto\":\"502\",\"tipo\":\"paquete\",\"remitente\":\"Amazon\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        // Sin pedirla no hay chat ni respuesta
        mvc.perform(get("/api/porteria/correspondencia/" + id + "/chat").header("Authorization", porteria)).andExpect(status().isConflict());
        enviar(patch("/api/porteria/correspondencia/" + id + "/solicitud/respuesta"), residente502, "{\"decision\":\"AUTORIZAR\"}")
                .andExpect(status().isConflict());

        enviar(post("/api/porteria/correspondencia/" + id + "/solicitud"), porteria, "{\"recoge\":\"Pedro Pérez\"}").andExpect(status().isOk());
        enviar(patch("/api/porteria/correspondencia/" + id + "/solicitud/respuesta"), residente502, "{\"decision\":\"RECHAZAR\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.solicitud.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.solicitud.chatHasta").exists());
        enviar(post("/api/porteria/correspondencia/" + id + "/entrega"), porteria, "{}").andExpect(status().isConflict());

        // Chat: el residente explica y la portería contesta; otro apartamento no entra
        enviar(post("/api/porteria/correspondencia/" + id + "/chat"), residente502, "{\"texto\":\"No conozco a Pedro\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.autor").value("Apto 5-502"));
        enviar(post("/api/porteria/correspondencia/" + id + "/chat"), porteria, "{\"texto\":\"Entendido, no se entrega\"}")
                .andExpect(status().isCreated());
        mvc.perform(get("/api/porteria/correspondencia/" + id + "/chat").header("Authorization", residente1001)).andExpect(status().isForbidden());
        mvc.perform(get("/api/porteria/correspondencia/" + id + "/chat").header("Authorization", residente502))
                .andExpect(status().isOk()).andExpect(jsonPath("$.abierto").value(true))
                .andExpect(jsonPath("$.que").value("Paquete de Amazon")).andExpect(jsonPath("$.mensajes", hasSize(2)));
        mvc.perform(get("/api/porteria/correspondencia").header("Authorization", porteria))
                .andExpect(jsonPath("$[0].solicitud.mensajesResidente").value(1));

        // Se puede pedir otra vez para otra persona
        enviar(post("/api/porteria/correspondencia/" + id + "/solicitud"), porteria, "{\"recoge\":\"Laura\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.solicitud.estado").value("PENDIENTE"));
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
