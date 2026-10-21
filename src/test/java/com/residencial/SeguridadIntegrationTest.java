package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Pago;
import com.residencial.repository.AdministradorRepository;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.PagoRepository;
import com.residencial.repository.VehiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Comprueba la tabla de permisos de P0 con SECURITY_ENFORCE=true. */
@SpringBootTest
@AutoConfigureMockMvc
class SeguridadIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired PasswordEncoder encoder;

    Long apto1;
    Long apto2;

    @BeforeEach
    void datos() {
        vehiculoRepo.deleteAll();
        pagoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        Apartamento a1 = aptoRepo.save(new Apartamento(null, "1", "101", encoder.encode("clave101")));
        // Contraseña heredada en texto plano, como las que hay hoy en Supabase
        Apartamento a2 = aptoRepo.save(new Apartamento(null, "1", "102", "plano102"));
        apto1 = a1.getId();
        apto2 = a2.getId();
        pagoRepo.save(new Pago(null, 150000.0, "En Mora", null, a1));
        pagoRepo.save(new Pago(null, 0.0, "Al Día", null, a2));
    }

    // ─── Login ───────────────────────────────────────────────────────────────

    @Test
    void loginAdminDevuelveTokenSinContrasena() throws Exception {
        loginAdmin("admin", "admin123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.usuario").value("admin"))
                .andExpect(jsonPath("$.expiraEn").isNotEmpty())
                .andExpect(jsonPath("$.contrasena").doesNotExist());
    }

    @Test
    void loginApartamentoMantieneIdTorreYApto() throws Exception {
        loginApto("1", "101", "clave101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("PROPIETARIO"))
                .andExpect(jsonPath("$.id").value(apto1))
                .andExpect(jsonPath("$.torre").value("1"))
                .andExpect(jsonPath("$.apto").value("101"))
                .andExpect(jsonPath("$.contrasena").doesNotExist());
    }

    @Test
    void loginIncorrectoDevuelve401ConMensaje() throws Exception {
        loginApto("1", "101", "mala")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Torre, apto o contraseña incorrectos"));
    }

    @Test
    void contrasenaEnTextoPlanoSeMigraABcryptAlIniciarSesion() throws Exception {
        loginApto("1", "102", "plano102").andExpect(status().isOk());
        assertThat(aptoRepo.findById(apto2).orElseThrow().getContrasena()).startsWith("$2");
        // Y sigue funcionando después de migrada
        loginApto("1", "102", "plano102").andExpect(status().isOk());
    }

    @Test
    void demasiadosIntentosFallidosDevuelve429() throws Exception {
        for (int i = 0; i < 5; i++) {
            loginApto("9", "999", "mala").andExpect(status().isUnauthorized());
        }
        loginApto("9", "999", "mala")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    // ─── Autenticación ───────────────────────────────────────────────────────

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/zonas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void tokenInvalidoDevuelve401() throws Exception {
        mvc.perform(get("/api/zonas").header("Authorization", "Bearer no-es-un-token"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Permisos por rol ────────────────────────────────────────────────────

    @Test
    void adminListaApartamentosSinContrasenas() throws Exception {
        mvc.perform(get("/api/apartamentos").header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].contrasena").doesNotExist())
                .andExpect(jsonPath("$[1].contrasena").doesNotExist());
    }

    @Test
    void propietarioNoPuedeListarApartamentosNiReservasDeTodos() throws Exception {
        String token = bearer(tokenApto("1", "101", "clave101"));
        mvc.perform(get("/api/apartamentos").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        mvc.perform(get("/api/reservas").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    // ─── Solo su apartamento ─────────────────────────────────────────────────

    @Test
    void propietarioSoloVeSuApartamento() throws Exception {
        String token = bearer(tokenApto("1", "101", "clave101"));
        mvc.perform(get("/api/apartamentos/" + apto1).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contrasena").doesNotExist());
        mvc.perform(get("/api/apartamentos/" + apto2).header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void propietarioSoloVeSuCartera() throws Exception {
        String token = bearer(tokenApto("1", "101", "clave101"));
        mvc.perform(get("/api/pagos/apartamento/" + apto1).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoPendiente").value(150000.0));
        mvc.perform(get("/api/pagos/apartamento/" + apto2).header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pagos/apartamento/" + apto2 + "/simular").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void propietarioNoPuedeRegistrarVehiculoEnOtroApartamento() throws Exception {
        String token = bearer(tokenApto("1", "101", "clave101"));
        mvc.perform(post("/api/vehiculos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiculo(apto2)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/vehiculos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiculo(apto1)))
                .andExpect(status().isOk());
    }

    // ─── Restablecer contraseña ──────────────────────────────────────────────

    @Test
    void adminRestableceContrasena() throws Exception {
        String admin = bearer(tokenAdmin());
        mvc.perform(put("/api/apartamentos/" + apto1 + "/contrasena").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaContrasena\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        mvc.perform(put("/api/apartamentos/" + apto1 + "/contrasena").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaContrasena\":\"nueva123\"}"))
                .andExpect(status().isNoContent());
        loginApto("1", "101", "clave101").andExpect(status().isUnauthorized());
        loginApto("1", "101", "nueva123").andExpect(status().isOk());
    }

    @Test
    void propietarioNoPuedeRestablecerContrasenas() throws Exception {
        String token = bearer(tokenApto("1", "101", "clave101"));
        mvc.perform(put("/api/apartamentos/" + apto1 + "/contrasena").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaContrasena\":\"nueva123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void putApartamentoSinContrasenaConservaLaActual() throws Exception {
        mvc.perform(put("/api/apartamentos/" + apto1).header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"torre\":\"1\",\"apto\":\"101\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contrasena").doesNotExist());
        loginApto("1", "101", "clave101").andExpect(status().isOk());
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private ResultActions loginAdmin(String usuario, String contrasena) throws Exception {
        return mvc.perform(post("/api/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}"));
    }

    private ResultActions loginApto(String torre, String apto, String contrasena) throws Exception {
        return mvc.perform(post("/api/auth/apartamento/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"torre\":\"" + torre + "\",\"apto\":\"" + apto
                        + "\",\"contrasena\":\"" + contrasena + "\"}"));
    }

    private String tokenAdmin() throws Exception {
        return token(loginAdmin("admin", "admin123"));
    }

    private String tokenApto(String torre, String apto, String contrasena) throws Exception {
        return token(loginApto(torre, apto, contrasena));
    }

    private static String token(ResultActions login) throws Exception {
        String json = login.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String vehiculo(Long apartamentoId) {
        return "{\"placa\":\"ABC123\",\"marca\":\"Mazda\",\"color\":\"Rojo\",\"tipo\":\"Carro\",\"apartamentoId\":"
                + apartamentoId + "}";
    }
}
