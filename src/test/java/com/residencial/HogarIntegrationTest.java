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

/**
 * P5 bloque 2: Mi hogar (personas, visitantes, citófonos) y el propietario que administra a
 * su arrendatario. Con arrendatario activo, el propietario ya no gestiona lo de quien vive.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HogarIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired VisitanteFrecuenteRepository visitanteRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired CupoVehiculoRepository cupoRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired CuotaRepository cuotaRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    String admin;
    String propietario;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "4", "401", encoder.encode("clave401")));
        Residente dueno = new Residente(null, "Laura Dueña", "3001112233", "1111111", "PROPIETARIO", apto, encoder.encode("dueno123"), Residente.APROBADO);
        residenteRepo.save(dueno);
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        propietario = "Bearer " + login("PROPIETARIO", "dueno123");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
        visitanteRepo.deleteAll();
        vehiculoRepo.deleteAll();
        cupoRepo.deleteAll();
        cuotaRepo.deleteAll();
        residenteRepo.deleteAll();
        pagoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void elResidenteGestionaLasPersonasDeSuHogarLosVisitantesYLosCitofonos() throws Exception {
        int id = enviar(post("/api/residentes"), propietario,
                "{\"nombreCompleto\":\"Hijo Dueño\",\"identificacion\":\"2222222\",\"tipoHabitante\":\"PROPIETARIO\",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.titular").value(false))
                .andReturn().getResponse().getContentAsString().transform(r -> JsonPath.read(r, "$.id"));
        enviar(put("/api/residentes/" + id), propietario,
                "{\"nombreCompleto\":\"Hijo Dueño López\",\"identificacion\":\"2222222\",\"telefono\":\"3110000000\",\"tipoHabitante\":\"PROPIETARIO\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.telefono").value("3110000000"));
        // Al titular no lo edita desde la app
        long dueno = residenteRepo.findTitulares(apto.getId(), "PROPIETARIO").get(0).getId();
        enviar(put("/api/residentes/" + dueno), propietario,
                "{\"nombreCompleto\":\"Otro\",\"identificacion\":\"1111111\",\"tipoHabitante\":\"PROPIETARIO\"}")
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/residentes/" + id).header("Authorization", propietario)).andExpect(status().isNoContent());

        int visitante = enviar(post("/api/visitantes"), propietario,
                "{\"nombreCompleto\":\"Abuela\",\"telefono\":\"3200000000\",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(r -> JsonPath.read(r, "$.id"));
        enviar(put("/api/visitantes/" + visitante), propietario, "{\"nombreCompleto\":\"Abuela Rosa\"}")
                .andExpect(jsonPath("$.nombreCompleto").value("Abuela Rosa"));
        mvc.perform(get("/api/visitantes/apartamento/" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/visitantes/" + visitante).header("Authorization", propietario)).andExpect(status().isNoContent());

        // Citófono 1: por defecto el teléfono del propietario
        mvc.perform(get("/api/apartamentos/" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$.citofono1").value("3001112233"))
                .andExpect(jsonPath("$.arrendado").value(false));
        enviar(put("/api/apartamentos/" + apto.getId() + "/citofonos"), propietario,
                "{\"citofono1\":\"3009998877\",\"citofono2\":\"6041234567\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citofono2").value("6041234567"));
        enviar(put("/api/apartamentos/" + apto.getId() + "/citofonos"), propietario, "{\"citofono1\":\"abc\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void elPropietarioApruebaASuArrendatarioYMientrasViveAhiNoGestionaElHogar() throws Exception {
        registrarArrendatario();
        int id = mvc.perform(get("/api/residentes/apartamento/" + apto.getId() + "/arrendatario").header("Authorization", propietario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobadoPropietario").value(false))
                .andReturn().getResponse().getContentAsString().transform(r -> JsonPath.read(r, "$.id"));

        enviar(put("/api/residentes/" + id + "/aprobacion-propietario"), propietario, "{\"aprobado\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.aprobadoPropietario").value(true));
        enviar(put("/api/residentes/" + id + "/aprobacion-propietario"), propietario, "{\"aprobado\":true}")
                .andExpect(status().isBadRequest());
        enviar(put("/api/residentes/" + id + "/estado"), admin, "{\"estado\":\"aprobado\"}").andExpect(status().isOk());

        String arrendatario = "Bearer " + login("ARRENDATARIO", "arrienda1");
        mvc.perform(get("/api/apartamentos").header("Authorization", admin))
                .andExpect(jsonPath("$[0].arrendado").value(true));

        // P6: los vehículos son del propietario aunque esté arrendado (el arrendatario solo consulta);
        // los visitantes ya no los registra el propietario sino quien vive ahí
        enviar(post("/api/vehiculos"), arrendatario,
                "{\"tipo\":\"Bicicleta\",\"marca\":\"GW\",\"apartamentoId\":" + apto.getId() + "}").andExpect(status().isForbidden());
        enviar(post("/api/vehiculos"), propietario,
                "{\"tipo\":\"Bicicleta\",\"marca\":\"GW\",\"apartamentoId\":" + apto.getId() + "}").andExpect(status().isOk());
        enviar(post("/api/visitantes"), propietario,
                "{\"nombreCompleto\":\"X\",\"apartamentoId\":" + apto.getId() + "}").andExpect(status().isForbidden());
        enviar(post("/api/visitantes"), arrendatario,
                "{\"nombreCompleto\":\"Mamá de Ana\",\"apartamentoId\":" + apto.getId() + "}").andExpect(status().isOk());
        // El arrendatario no administra su propio acceso
        enviar(put("/api/residentes/" + id + "/acceso"), arrendatario, "{\"contrasena\":\"otra1234\"}").andExpect(status().isForbidden());

        // El propietario le cambia la contraseña y luego lo quita: recupera el apartamento
        enviar(put("/api/residentes/" + id + "/acceso"), propietario, "{\"contrasena\":\"nueva123\"}").andExpect(status().isOk());
        login("ARRENDATARIO", "nueva123");
        mvc.perform(delete("/api/residentes/" + id).header("Authorization", propietario)).andExpect(status().isNoContent());
        mvc.perform(get("/api/apartamentos/" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$.arrendado").value(false));
        enviar(post("/api/visitantes"), propietario,
                "{\"nombreCompleto\":\"X\",\"apartamentoId\":" + apto.getId() + "}").andExpect(status().isOk());
    }

    @Test
    void siElPropietarioLoRechazaNoEntraYSePuedeRegistrarOtro() throws Exception {
        registrarArrendatario();
        long id = residenteRepo.findTitulares(apto.getId(), "ARRENDATARIO").get(0).getId();
        enviar(put("/api/residentes/" + id + "/aprobacion-propietario"), propietario, "{\"aprobado\":false,\"motivo\":\"No lo conozco\"}")
                .andExpect(jsonPath("$.estado").value("RECHAZADO"));
        mvc.perform(post("/api/auth/residente/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"torre\":\"4\",\"apto\":\"401\",\"rol\":\"ARRENDATARIO\",\"contrasena\":\"arrienda1\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/registro/verificar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"torre\":\"4\",\"apto\":\"401\"}"))
                .andExpect(jsonPath("$.rol").value("ARRENDATARIO"));
        mvc.perform(get("/api/residentes/apartamento/" + apto.getId() + "/arrendatario").header("Authorization", propietario))
                .andExpect(status().isNoContent());
    }

    private void registrarArrendatario() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"torre\":\"4\",\"apto\":\"401\",\"nombres\":\"Ana\",\"apellidos\":\"Arrienda\",\"cedula\":\"3333333\","
                                + "\"telefono\":\"3001234567\",\"correo\":\"ana@correo.com\",\"contrasena\":\"arrienda1\","
                                + "\"aceptaDatos\":true,\"aceptaTerminos\":true}"))
                .andExpect(status().isCreated());
    }

    private String login(String rol, String contrasena) throws Exception {
        return token("/api/auth/residente/login",
                "{\"torre\":\"4\",\"apto\":\"401\",\"rol\":\"" + rol + "\",\"contrasena\":\"" + contrasena + "\"}");
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
