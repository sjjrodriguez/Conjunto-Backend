package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.repository.AdministradorRepository;
import com.residencial.repository.ApartamentoRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P4 bloque 3: cada usuario cambia su propia contraseña desde Cuenta → Perfil. */
@SpringBootTest
@AutoConfigureMockMvc
class CambioContrasenaIntegrationTest {

    private static final String LOGIN_ADMIN = "/api/auth/admin/login";
    private static final String LOGIN_APTO = "/api/auth/apartamento/login";

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void datos() {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        aptoRepo.save(new Apartamento(null, "8", "801", encoder.encode("clave801")));
    }

    // La base H2 se comparte con los demás tests
    @AfterEach
    void limpiar() {
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void elResidenteCambiaSuContrasenaYEntraConLaNueva() throws Exception {
        String token = token(LOGIN_APTO, apto("clave801"));
        cambiar(token, "clave801", "nueva8010").andExpect(status().isNoContent());

        login(LOGIN_APTO, apto("clave801")).andExpect(status().isUnauthorized());
        login(LOGIN_APTO, apto("nueva8010")).andExpect(status().isOk());
    }

    @Test
    void elAdministradorCambiaSuContrasena() throws Exception {
        String token = token(LOGIN_ADMIN, admin("admin123"));
        cambiar(token, "admin123", "otraClave9").andExpect(status().isNoContent());

        login(LOGIN_ADMIN, admin("admin123")).andExpect(status().isUnauthorized());
        login(LOGIN_ADMIN, admin("otraClave9")).andExpect(status().isOk());
    }

    @Test
    void conLaActualIncorrectaNoCambiaYResponde400() throws Exception {
        String token = token(LOGIN_APTO, apto("clave801"));
        // 400 y no 401: la app trata 401 como sesión vencida y cerraría la sesión
        cambiar(token, "equivocada", "nueva8010")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La contraseña actual no es correcta"));
        login(LOGIN_APTO, apto("clave801")).andExpect(status().isOk());
    }

    @Test
    void validaLaNuevaContrasena() throws Exception {
        String token = token(LOGIN_APTO, apto("clave801"));
        cambiar(token, "clave801", "123").andExpect(status().isBadRequest());
        cambiar(token, "clave801", "clave801")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La nueva contraseña debe ser distinta de la actual"));
    }

    @Test
    void sinSesionNoSePuede() throws Exception {
        mvc.perform(put("/api/auth/contrasena").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"clave801\",\"contrasenaNueva\":\"nueva8010\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions cambiar(String token, String actual, String nueva) throws Exception {
        return mvc.perform(put("/api/auth/contrasena").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contrasenaActual\":\"" + actual + "\",\"contrasenaNueva\":\"" + nueva + "\"}"));
    }

    private static String apto(String clave) {
        return "{\"torre\":\"8\",\"apto\":\"801\",\"contrasena\":\"" + clave + "\"}";
    }

    private static String admin(String clave) {
        return "{\"usuario\":\"admin\",\"contrasena\":\"" + clave + "\"}";
    }

    private ResultActions login(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(String url, String json) throws Exception {
        String respuesta = login(url, json).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
