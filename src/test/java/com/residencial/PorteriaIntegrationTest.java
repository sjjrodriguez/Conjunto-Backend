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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P6 bloque 5: la portería registra visitas, el apartamento responde en 2 minutos y la portería no ve nada más. */
@SpringBootTest
@AutoConfigureMockMvc
class PorteriaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired VisitaPorteriaRepository visitaRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired ParqueaderoRepository parqueaderoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto7;
    String admin;
    String porteria;
    String residente7;
    String residente8;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto7 = aptoRepo.save(new Apartamento(null, "7", "701", encoder.encode("clave701")));
        aptoRepo.save(new Apartamento(null, "8", "801", encoder.encode("clave801")));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        // El administrador principal crea la cuenta de portería desde el equipo
        enviar(post("/api/administradores"), admin, "{\"usuario\":\"portero1\",\"contrasena\":\"portero123\",\"rol\":\"PORTERIA\","
                + "\"nombreCompleto\":\"Pedro Portero\",\"documento\":\"123\",\"telefono\":\"3000000\",\"correo\":\"p@x.co\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("PORTERIA"));
        porteria = "Bearer " + JsonPath.read(mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"portero1\",\"contrasena\":\"portero123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("PORTERIA"))
                .andReturn().getResponse().getContentAsString(), "$.token");
        residente7 = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"7\",\"apto\":\"701\",\"contrasena\":\"clave701\"}");
        residente8 = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"8\",\"apto\":\"801\",\"contrasena\":\"clave801\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        visitaRepo.deleteAll();
        parqueaderoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void elApartamentoAutorizaUnaVisitaYNoSePuedeResponderDosVeces() throws Exception {
        int id = registrar("Juan Pérez");
        assertThat(notificacionRepo.findAll()).hasSize(1).allMatch(n -> "VISITA_PORTERIA".equals(n.getTipo()));

        mvc.perform(get("/api/porteria/visitas/pendientes").header("Authorization", residente7))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].visitante").value("Juan Pérez"));
        mvc.perform(get("/api/porteria/visitas/pendientes").header("Authorization", residente8))
                .andExpect(jsonPath("$.length()").value(0));
        // Otro apartamento no puede responder
        enviar(patch("/api/porteria/visitas/" + id + "/respuesta"), residente8, "{\"decision\":\"AUTORIZAR\"}")
                .andExpect(status().isForbidden());

        enviar(patch("/api/porteria/visitas/" + id + "/respuesta"), residente7, "{\"decision\":\"AUTORIZAR\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("AUTORIZADA"))
                .andExpect(jsonPath("$.canal").value("APP"));
        enviar(patch("/api/porteria/visitas/" + id + "/respuesta"), residente7, "{\"decision\":\"RECHAZAR\"}")
                .andExpect(status().isConflict());

        mvc.perform(get("/api/porteria/visitas").header("Authorization", porteria))
                .andExpect(jsonPath("$[0].estado").value("AUTORIZADA"));
    }

    @Test
    void laVisitaSinRespuestaExpiraALosDosMinutos() throws Exception {
        int id = registrar("Ana Gómez");
        VisitaPorteria v = visitaRepo.findById((long) id).orElseThrow();
        assertThat(v.getVenceEn()).isAfter(v.getCreadaEn().plusSeconds(100)).isBefore(v.getCreadaEn().plusSeconds(130));
        v.setVenceEn(LocalDateTime.now().minusSeconds(1));
        visitaRepo.save(v);

        mvc.perform(get("/api/porteria/visitas/pendientes").header("Authorization", residente7))
                .andExpect(jsonPath("$.length()").value(0));
        enviar(patch("/api/porteria/visitas/" + id + "/respuesta"), residente7, "{\"decision\":\"AUTORIZAR\"}")
                .andExpect(status().isConflict());
        mvc.perform(get("/api/porteria/visitas").header("Authorization", porteria))
                .andExpect(jsonPath("$[0].estado").value("EXPIRADA"));
    }

    @Test
    void laPorteriaPuedeRegistrarLaRespuestaDadaPorLlamada() throws Exception {
        int id = registrar("Luis Mora");
        enviar(patch("/api/porteria/visitas/" + id + "/respuesta"), porteria, "{\"decision\":\"RECHAZAR\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.canal").value("LLAMADA"));
        enviar(patch("/api/porteria/visitas/" + id + "/respuesta"), porteria, "{\"decision\":\"TALVEZ\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void laPorteriaNoEntraAlRestoDeLaAplicacionNiElResidenteRegistraVisitas() throws Exception {
        for (String ruta : new String[]{"/api/apartamentos/" + apto7.getId(), "/api/publicaciones", "/api/vehiculos/apartamento/" + apto7.getId(),
                "/api/cuotas/apartamento/" + apto7.getId(), "/api/obras/apartamento/" + apto7.getId(), "/api/chat/conversaciones",
                "/api/visitantes/apartamento/" + apto7.getId(), "/api/administradores"}) {
            mvc.perform(get(ruta).header("Authorization", porteria)).andExpect(status().isForbidden());
        }
        enviar(post("/api/porteria/visitas"), residente7, "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"X\"}")
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/porteria/visitas").header("Authorization", residente7)).andExpect(status().isForbidden());
        // La portería sí tiene sus avisos y cierra sesión
        mvc.perform(get("/api/notificaciones").header("Authorization", porteria)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", porteria)).andExpect(status().isNoContent());
    }

    @Test
    void validaElApartamentoYElNombre() throws Exception {
        enviar(post("/api/porteria/visitas"), porteria, "{\"torre\":\"7\",\"apto\":\"999\",\"visitante\":\"X\"}").andExpect(status().isNotFound());
        enviar(post("/api/porteria/visitas"), porteria, "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\" \"}").andExpect(status().isBadRequest());
    }

    @Test
    void enVehiculoOcupaUnParqueaderoDeInvitadosHastaLaSalida() throws Exception {
        var inv1 = parqueaderoRepo.save(new com.residencial.model.Parqueadero(null, "INV-01", "Invitados", null));
        var inv2 = parqueaderoRepo.save(new com.residencial.model.Parqueadero(null, "INV-02", "Invitados", null));
        parqueaderoRepo.save(new com.residencial.model.Parqueadero(null, "C-01", "Carro", null));

        // Sin placa no entra en vehículo
        enviar(post("/api/porteria/visitas"), porteria, "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"Ana\",\"tipoIngreso\":\"VEHICULO\"}")
                .andExpect(status().isBadRequest());
        // Sin elegir: el primero libre
        int a = JsonPath.read(enviar(post("/api/porteria/visitas"), porteria,
                "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"Ana\",\"tipoIngreso\":\"vehiculo\",\"placa\":\"abc 123\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipoIngreso").value("VEHICULO"))
                .andExpect(jsonPath("$.placa").value("ABC123")).andExpect(jsonPath("$.parqueadero").value("INV-01"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(notificacionRepo.findAll()).anyMatch(n -> n.getMensaje().contains("en vehículo ABC123 (parqueadero INV-01)"));
        // El ocupado no se puede volver a elegir; uno de carros tampoco
        enviar(post("/api/porteria/visitas"), porteria, "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"Beto\",\"tipoIngreso\":\"VEHICULO\",\"placa\":\"XYZ9\",\"parqueaderoId\":" + inv1.getId() + "}")
                .andExpect(status().isConflict());
        int b = JsonPath.read(enviar(post("/api/porteria/visitas"), porteria,
                "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"Beto\",\"tipoIngreso\":\"VEHICULO\",\"placa\":\"XYZ9\",\"parqueaderoId\":" + inv2.getId() + "}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.parqueadero").value("INV-02"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        // Todos ocupados
        enviar(post("/api/porteria/visitas"), porteria, "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"Carla\",\"tipoIngreso\":\"VEHICULO\",\"placa\":\"QQQ1\"}")
                .andExpect(status().isConflict());
        // A pie sí entra
        enviar(post("/api/porteria/visitas"), porteria, "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"Carla\",\"tipoIngreso\":\"PEATONAL\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.parqueadero").doesNotExist());

        mvc.perform(get("/api/porteria/parqueaderos").header("Authorization", porteria))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].numero").value("INV-01")).andExpect(jsonPath("$[0].libre").value(false))
                .andExpect(jsonPath("$[0].placa").value("ABC123")).andExpect(jsonPath("$[0].visitaId").value(a));
        mvc.perform(get("/api/porteria/parqueaderos").header("Authorization", residente7)).andExpect(status().isForbidden());

        // Rechazada: el parqueadero se libera solo
        enviar(patch("/api/porteria/visitas/" + b + "/respuesta"), residente7, "{\"decision\":\"RECHAZAR\"}").andExpect(status().isOk());
        // Salida de la autorizada: también queda libre, y no se registra dos veces
        enviar(patch("/api/porteria/visitas/" + a + "/respuesta"), residente7, "{\"decision\":\"AUTORIZAR\"}").andExpect(status().isOk());
        mvc.perform(post("/api/porteria/visitas/" + a + "/salida").header("Authorization", residente7)).andExpect(status().isForbidden());
        mvc.perform(post("/api/porteria/visitas/" + a + "/salida").header("Authorization", porteria))
                .andExpect(status().isOk()).andExpect(jsonPath("$.salidaEn").exists());
        mvc.perform(post("/api/porteria/visitas/" + a + "/salida").header("Authorization", porteria)).andExpect(status().isConflict());
        mvc.perform(get("/api/porteria/parqueaderos").header("Authorization", porteria))
                .andExpect(jsonPath("$[0].libre").value(true)).andExpect(jsonPath("$[1].libre").value(true));
    }

    @Test
    void sinParqueaderosDeInvitadosElVehiculoEntraSinPuesto() throws Exception {
        enviar(post("/api/porteria/visitas"), porteria, "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"Dani\",\"tipoIngreso\":\"VEHICULO\",\"placa\":\"AAA111\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipoIngreso").value("VEHICULO"))
                .andExpect(jsonPath("$.parqueadero").doesNotExist());
        enviar(post("/api/porteria/visitas"), porteria, "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"Dani\",\"tipoIngreso\":\"BICI\"}")
                .andExpect(status().isBadRequest());
    }

    private int registrar(String visitante) throws Exception {
        String cuerpo = enviar(post("/api/porteria/visitas"), porteria,
                "{\"torre\":\"7\",\"apto\":\"701\",\"visitante\":\"" + visitante + "\",\"placa\":\"abc123\",\"motivo\":\"Entrega\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.placa").value("ABC123")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.id");
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String auth, String json) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(String ruta, String json) throws Exception {
        String cuerpo = mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }
}
