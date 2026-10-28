package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.TextoLegal;
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

/** P6 bloque 4: huella con credencial opaca, textos legales con consentimiento y solicitudes de datos. */
@SpringBootTest
@AutoConfigureMockMvc
class BiometriaYLegalIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired CredencialDispositivoRepository credencialRepo;
    @Autowired ConsentimientoRepository consentimientoRepo;
    @Autowired SolicitudDatosRepository solicitudRepo;
    @Autowired TextoLegalRepository textoRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PasswordEncoder encoder;

    String admin;
    String residente;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        aptoRepo.save(new Apartamento(null, "6", "601", encoder.encode("clave601")));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        residente = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"6\",\"apto\":\"601\",\"contrasena\":\"clave601\"}");
        if (textoRepo.count() == 0) {
            for (String tipo : new String[]{TextoLegal.POLITICA_PRIVACIDAD, TextoLegal.TERMINOS}) {
                TextoLegal t = new TextoLegal();
                t.setTipo(tipo);
                t.setVersion(1);
                t.setTitulo(tipo);
                t.setContenido("Texto de prueba");
                t.setVigenteDesde(LocalDateTime.now());
                textoRepo.save(t);
            }
        }
    }

    @AfterEach
    void limpiar() {
        solicitudRepo.deleteAll();
        consentimientoRepo.deleteAll();
        credencialRepo.deleteAll();
        dispositivoRepo.deleteAll();
        textoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void laHuellaEntraConUnaCredencialOpacaYNuncaGuardaLaContrasena() throws Exception {
        String credencial = activar(admin, "celular-1", "ADMIN");
        assertThat(credencialRepo.findAll()).hasSize(1)
                .allMatch(c -> !c.getHashCredencial().equals(credencial) && c.getHashCredencial().length() == 64);

        // Entra con la credencial y obtiene una sesión que vale
        String sesion = JsonPath.read(entrar("celular-1", credencial, "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN")).andReturn().getResponse().getContentAsString(), "$.token");
        mvc.perform(get("/api/vehiculos").header("Authorization", "Bearer " + sesion)).andExpect(status().isOk());

        // En otro celular, con otra app o con una credencial inventada no entra
        entrar("celular-2", credencial, "ADMIN").andExpect(status().isUnauthorized());
        entrar("celular-1", credencial, "RESIDENTE").andExpect(status().isUnauthorized());
        entrar("celular-1", "inventada", "ADMIN").andExpect(status().isUnauthorized());
    }

    @Test
    void quitarLaHuellaOCambiarLaContrasenaRevocanLaCredencial() throws Exception {
        String c1 = activar(admin, "celular-1", "ADMIN");
        mvc.perform(delete("/api/auth/biometria").param("dispositivoId", "celular-1").header("X-App-Tipo", "ADMIN")
                .header("Authorization", admin)).andExpect(status().isNoContent());
        entrar("celular-1", c1, "ADMIN").andExpect(status().isUnauthorized());

        String c2 = activar(residente, "celular-3", "RESIDENTE");
        entrar("celular-3", c2, "RESIDENTE").andExpect(status().isOk());
        mvc.perform(put("/api/auth/contrasena").header("Authorization", residente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contrasenaActual\":\"clave601\",\"contrasenaNueva\":\"nueva6011\"}")).andExpect(status().isNoContent());
        entrar("celular-3", c2, "RESIDENTE").andExpect(status().isUnauthorized());
    }

    @Test
    void activarPideSesion() throws Exception {
        mvc.perform(post("/api/auth/biometria/activar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dispositivoId\":\"x\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void losTextosLegalesSeLeenSinSesionYSeAceptanUnaVez() throws Exception {
        mvc.perform(get("/api/legal/textos")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/legal/pendientes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/legal/pendientes").header("Authorization", residente))
                .andExpect(jsonPath("$.length()").value(2));

        String ids = mvc.perform(get("/api/legal/pendientes").header("Authorization", residente))
                .andReturn().getResponse().getContentAsString();
        int a = JsonPath.read(ids, "$[0].id");
        int b = JsonPath.read(ids, "$[1].id");
        enviar(post("/api/legal/consentimientos"), residente, "{\"textoIds\":[" + a + "," + b + "]}").andExpect(status().isNoContent());
        // Repetirlo no duplica y el pendiente queda vacío solo para quien aceptó
        enviar(post("/api/legal/consentimientos"), residente, "{\"textoIds\":[" + a + "]}").andExpect(status().isNoContent());
        assertThat(consentimientoRepo.count()).isEqualTo(2);
        mvc.perform(get("/api/legal/pendientes").header("Authorization", residente)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/legal/pendientes").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(2));
        enviar(post("/api/legal/consentimientos"), residente, "{\"textoIds\":[99999]}").andExpect(status().isBadRequest());

        // Una versión nueva vuelve a quedar pendiente
        TextoLegal v2 = new TextoLegal();
        v2.setTipo(TextoLegal.TERMINOS);
        v2.setVersion(2);
        v2.setTitulo("Términos");
        v2.setContenido("Nuevo");
        v2.setVigenteDesde(LocalDateTime.now());
        textoRepo.save(v2);
        mvc.perform(get("/api/legal/pendientes").header("Authorization", residente))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].version").value(2));
    }

    @Test
    void elResidentePidePeroSoloLaAdministracionResuelve() throws Exception {
        enviar(post("/api/solicitudes-datos"), residente, "{\"tipo\":\"OTRA\"}").andExpect(status().isBadRequest());
        int id = JsonPath.read(enviar(post("/api/solicitudes-datos"), residente,
                "{\"tipo\":\"supresion\",\"detalle\":\"Borren mis datos\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/solicitudes-datos/mias").header("Authorization", residente))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/solicitudes-datos").header("Authorization", residente)).andExpect(status().isForbidden());
        enviar(patch("/api/solicitudes-datos/" + id), residente, "{\"estado\":\"ATENDIDA\"}").andExpect(status().isForbidden());

        mvc.perform(get("/api/solicitudes-datos").header("Authorization", admin))
                .andExpect(jsonPath("$[0].nombre").value("Torre 6 · Apto 601"));
        enviar(patch("/api/solicitudes-datos/" + id), admin, "{\"estado\":\"ATENDIDA\",\"respuesta\":\"Listo\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ATENDIDA"));
        enviar(patch("/api/solicitudes-datos/" + id), admin, "{\"estado\":\"RECHAZADA\"}").andExpect(status().isConflict());
    }

    private String activar(String auth, String dispositivo, String app) throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/biometria/activar").header("Authorization", auth).header("X-App-Tipo", app)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dispositivoId\":\"" + dispositivo + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.credencial");
    }

    private ResultActions entrar(String dispositivo, String credencial, String app) throws Exception {
        return mvc.perform(post("/api/auth/biometria/login").header("X-App-Tipo", app).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dispositivoId\":\"" + dispositivo + "\",\"credencial\":\"" + credencial + "\"}"));
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
