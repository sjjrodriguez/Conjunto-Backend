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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P4 bloque 6: cuenta personal de cada residente (documento + contraseña) y arrendatarios
 * que agrega el propietario y aprueba el administrador.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResidentesCuentasIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired CupoVehiculoRepository cupoRepo;
    @Autowired ReservaRepository reservaRepo;
    @Autowired ZonaComunRepository zonaRepo;
    @Autowired PersonalObraRepository personalRepo;
    @Autowired ObraRepository obraRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    Apartamento vecino;
    String admin;
    String propietario;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "8", "801", encoder.encode("clave801")));
        vecino = aptoRepo.save(new Apartamento(null, "8", "802", encoder.encode("clave802")));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        propietario = "Bearer " + token("/api/auth/apartamento/login",
                "{\"torre\":\"8\",\"apto\":\"801\",\"contrasena\":\"clave801\"}");
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
        cupoRepo.deleteAll();
        residenteRepo.deleteAll();
        pagoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void elArrendatarioQueAgregaElPropietarioEntraConSuDocumentoCuandoLoAprueban() throws Exception {
        int id = enviar(post("/api/residentes"), propietario, arrendatario("555", "clave555", apto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.tieneAcceso").value(true))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andReturn().getResponse().getContentAsString().transform(r -> JsonPath.read(r, "$.id"));

        // Al administrador le llega el aviso y lo ve en pendientes, con su apartamento
        mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andExpect(jsonPath("$[0].tipo").value("RESIDENTE"))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("Torre 8 Apto 801")));
        mvc.perform(get("/api/residentes/pendientes").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].torre").value("8"))
                .andExpect(jsonPath("$[0].apto").value("801"));

        // Mientras está pendiente no puede entrar
        login("555", "clave555").andExpect(status().isUnauthorized());

        enviar(put("/api/residentes/" + id + "/estado"), admin, "{\"estado\":\"aprobado\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));
        mvc.perform(get("/api/notificaciones").header("Authorization", propietario))
                .andExpect(jsonPath("$[0].tipo").value("RESIDENTE_APROBADO"));

        String respuesta = login("555", "clave555")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ARRENDATARIO"))
                .andExpect(jsonPath("$.id").value(apto.getId()))
                .andExpect(jsonPath("$.residenteId").value(id))
                .andExpect(jsonPath("$.nombre").value("Ana Arrendataria"))
                .andExpect(jsonPath("$.torre").value("8"))
                .andReturn().getResponse().getContentAsString();
        String arrendatario = "Bearer " + JsonPath.read(respuesta, "$.token");

        // Usa la app como residente de su apartamento, pero no gestiona residentes
        mvc.perform(get("/api/residentes/apartamento/" + apto.getId()).header("Authorization", arrendatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contrasena").doesNotExist());
        mvc.perform(get("/api/vehiculos/apartamento/" + apto.getId()).header("Authorization", arrendatario))
                .andExpect(status().isOk());
        mvc.perform(get("/api/residentes/apartamento/" + vecino.getId()).header("Authorization", arrendatario))
                .andExpect(status().isForbidden());
        enviar(post("/api/residentes"), arrendatario, arrendatario("556", "clave556", apto))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/residentes/" + id).header("Authorization", arrendatario))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/residentes/pendientes").header("Authorization", arrendatario))
                .andExpect(status().isForbidden());
    }

    @Test
    void elPropietarioSoloAgregaYQuitaArrendatariosDeSuApartamento() throws Exception {
        enviar(post("/api/residentes"), propietario,
                "{\"nombreCompleto\":\"Otro Dueño\",\"identificacion\":\"777\",\"tipoHabitante\":\"PROPIETARIO\","
                        + "\"contrasena\":\"clave777\",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("Solo puedes agregar arrendatarios")));
        enviar(post("/api/residentes"), propietario, arrendatario("555", "clave555", vecino))
                .andExpect(status().isForbidden());
        enviar(post("/api/residentes"), propietario, arrendatario("555", "123", apto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("al menos 6")));

        Residente dueno = residenteRepo.save(new Residente(null, "Dueño", null, "100", "PROPIETARIO", apto));
        mvc.perform(delete("/api/residentes/" + dueno.getId()).header("Authorization", propietario))
                .andExpect(status().isForbidden());

        int id = enviar(post("/api/residentes"), propietario, arrendatario("555", "clave555", apto))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(r -> JsonPath.read(r, "$.id"));
        // El mismo documento dos veces en el apartamento no
        enviar(post("/api/residentes"), propietario, arrendatario("555", "clave555", apto))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/residentes/" + id).header("Authorization", propietario))
                .andExpect(status().isNoContent());
    }

    @Test
    void elAdministradorDaAccesoYElResidenteCambiaSuContrasena() throws Exception {
        Residente dueno = residenteRepo.save(new Residente(null, "Laura Dueña", null, "100", "PROPIETARIO", apto));
        login("100", "propia100").andExpect(status().isUnauthorized());

        enviar(put("/api/residentes/" + dueno.getId() + "/acceso"), propietario, "{\"contrasena\":\"propia100\"}")
                .andExpect(status().isForbidden());
        enviar(put("/api/residentes/" + dueno.getId() + "/acceso"), admin, "{\"contrasena\":\"propia100\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneAcceso").value(true));

        String respuesta = login(" 100 ", "propia100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("PROPIETARIO"))
                .andReturn().getResponse().getContentAsString();
        String residente = "Bearer " + JsonPath.read(respuesta, "$.token");

        // Como propietario puede agregar arrendatarios
        enviar(post("/api/residentes"), residente, arrendatario("555", "clave555", apto))
                .andExpect(status().isOk());

        // Cambia solo su contraseña: la del apartamento sigue igual
        enviar(put("/api/auth/contrasena"), residente,
                "{\"contrasenaActual\":\"propia100\",\"contrasenaNueva\":\"nueva100\"}")
                .andExpect(status().isNoContent());
        login("100", "nueva100").andExpect(status().isOk());
        token("/api/auth/apartamento/login", "{\"torre\":\"8\",\"apto\":\"801\",\"contrasena\":\"clave801\"}");

        // Sin contraseña se le quita el acceso
        enviar(put("/api/residentes/" + dueno.getId() + "/acceso"), admin, "{\"contrasena\":\"\"}")
                .andExpect(jsonPath("$.tieneAcceso").value(false));
        login("100", "nueva100").andExpect(status().isUnauthorized());
    }

    @Test
    void elArrendatarioRechazadoNoEntraYAlApartamentoLeLlegaElMotivo() throws Exception {
        int id = enviar(post("/api/residentes"), propietario, arrendatario("555", "clave555", apto))
                .andReturn().getResponse().getContentAsString().transform(r -> JsonPath.read(r, "$.id"));
        enviar(put("/api/residentes/" + id + "/estado"), admin, "{\"estado\":\"pendiente\"}")
                .andExpect(status().isBadRequest());
        enviar(put("/api/residentes/" + id + "/estado"), admin, "{\"estado\":\"rechazado\",\"motivo\":\"Falta el contrato\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"));

        login("555", "clave555").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notificaciones").header("Authorization", propietario))
                .andExpect(jsonPath("$[0].tipo").value("RESIDENTE_RECHAZADO"))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("Falta el contrato")));
        mvc.perform(get("/api/residentes/pendientes").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(0));
        // El propietario lo ve rechazado en su lista
        mvc.perform(get("/api/residentes/apartamento/" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$[0].estado").value("RECHAZADO"))
                .andExpect(content().string(not(containsString("clave555"))));
    }

    private String arrendatario(String documento, String contrasena, Apartamento a) {
        return "{\"nombreCompleto\":\"Ana Arrendataria\",\"identificacion\":\"" + documento + "\","
                + "\"tipoHabitante\":\"arrendatario\",\"contrasena\":\"" + contrasena + "\",\"apartamentoId\":" + a.getId() + "}";
    }

    private ResultActions login(String documento, String contrasena) throws Exception {
        return mvc.perform(post("/api/auth/residente/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documento\":\"" + documento + "\",\"contrasena\":\"" + contrasena + "\"}"));
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
