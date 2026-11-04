package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.correo.Correo;
import com.residencial.model.Administrador;
import com.residencial.model.Conjunto;
import com.residencial.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P7 bloque 5: código por correo en cada celular nuevo, una sesión por celular y límite de
 * celulares por cuenta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DispositivosIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired DispositivoCuentaRepository dispositivoRepo;
    @Autowired CodigoVerificacionRepository codigoRepo;
    @Autowired ConjuntoRepository conjuntoRepo;
    @Autowired PasswordEncoder encoder;
    @MockBean Correo correo;

    static final String LOGIN = "{\"usuario\":\"laura\",\"contrasena\":\"laura12345\"}";

    @BeforeEach
    void datos() {
        limpiar();
        Administrador a = new Administrador(null, "laura", encoder.encode("laura12345"));
        a.setNombreCompleto("Laura Prada");
        a.setCorreo("laura.prada@correo.test");
        adminRepo.save(a);
        Administrador sinCorreo = new Administrador(null, "sincorreo", encoder.encode("sincorreo123"));
        adminRepo.save(sinCorreo);
    }

    @AfterEach
    void limpiar() {
        codigoRepo.deleteAll();
        dispositivoRepo.deleteAll();
        adminRepo.deleteAll();
        if (conjunto != null) {
            ConjuntoContexto.en(conjunto, () -> {
                codigoRepo.deleteAll();
                dispositivoRepo.deleteAll();
                adminRepo.deleteAll();
            });
            conjuntoRepo.deleteById(conjunto);
            conjuntoRepo.deleteById(relleno);
            conjunto = null;
        }
    }

    // El conjunto de la prueba del límite (las demás usan el predeterminado, sin fila en la base de pruebas)
    Long conjunto;
    Long relleno;

    @Test
    void unCelularNuevoPideElCodigoDelCorreoYLaSegundaVezYaNo() throws Exception {
        String respuesta = login("cel-a", "Pixel 9").andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.correoOculto").value("la***@correo.test"))
                .andReturn().getResponse().getContentAsString();
        String verificacion = JsonPath.read(respuesta, "$.verificacionId");
        String codigo = ultimoCodigo();

        // Otro celular no puede usar ese código; uno incorrecto descuenta intentos
        verificar(verificacion, codigo, "cel-b").andExpect(status().isBadRequest());
        verificar(verificacion, codigo.equals("000000") ? "111111" : "000000", "cel-a")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje", containsString("4 intentos")));

        String token = JsonPath.read(verificar(verificacion, codigo, "cel-a").andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andReturn().getResponse().getContentAsString(), "$.token");
        mvc.perform(get("/api/auth/dispositivos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Pixel 9"))
                .andExpect(jsonPath("$[0].actual").value(true));
        // El código ya se usó
        verificar(verificacion, codigo, "cel-a").andExpect(status().isBadRequest());

        // En el mismo celular ya no pide código
        login("cel-a", "Pixel 9").andExpect(status().isOk()).andExpect(jsonPath("$.token").exists());
        verify(correo, times(1)).enviar(eq("laura.prada@correo.test"), any(), any(), any());
    }

    @Test
    void conElLimiteSeCierraLaSesionMasAntiguaYCerrarSesionCierraSoloEsa() throws Exception {
        // El primero que se guarda en la base de pruebas sería el 1 (el predeterminado): se usa el segundo
        relleno = conjuntoRepo.save(new Conjunto("Relleno", "Bolívar", "Cartagena", null)).getId();
        Conjunto c = new Conjunto("Límite", "Bolívar", "Cartagena", null);
        c.setMaxDispositivos(2);
        conjunto = conjuntoRepo.save(c).getId();
        ConjuntoContexto.en(conjunto, () -> {
            Administrador a = new Administrador(null, "laura", encoder.encode("laura12345"));
            a.setCorreo("laura.prada@correo.test");
            adminRepo.save(a);
        });

        String a = entrar("cel-a", "Celular A");
        String b = entrar("cel-b", "Celular B");
        String respuestaC = JsonPath.read(verificar(pedir("cel-c", "Celular C"), ultimoCodigo(), "cel-c")
                .andExpect(jsonPath("$.sesionCerradaEn").value("Celular A"))
                .andReturn().getResponse().getContentAsString(), "$.token");
        String cc = "Bearer " + respuestaC;

        mvc.perform(get("/api/auth/dispositivos").header("Authorization", a)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/dispositivos").header("Authorization", b)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").header("Authorization", b)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/dispositivos").header("Authorization", b)).andExpect(status().isUnauthorized());
        // El otro celular sigue con su sesión; el que cerró sesión sigue verificado (no pide código)
        mvc.perform(get("/api/auth/dispositivos").header("Authorization", cc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre=='Celular B')].sesionAbierta", contains(false)))
                .andExpect(jsonPath("$[?(@.nombre=='Celular C')].actual", contains(true)));
        login("cel-b", "Celular B").andExpect(jsonPath("$.token").exists());
    }

    @Test
    void quitarUnCelularLoSacaYLaProximaVezPideCodigo() throws Exception {
        String a = entrar("cel-a", "Celular A");
        String b = entrar("cel-b", "Celular B");
        Number idB = JsonPath.<java.util.List<Number>>read(mvc.perform(get("/api/auth/dispositivos").header("Authorization", a))
                .andReturn().getResponse().getContentAsString(), "$[?(@.nombre=='Celular B')].id").get(0);

        mvc.perform(delete("/api/auth/dispositivos/" + idB).header("Authorization", a)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/dispositivos").header("Authorization", b)).andExpect(status().isUnauthorized());
        login("cel-b", "Celular B").andExpect(jsonPath("$.verificacionId").exists());
        // Un celular de otra cuenta no se puede quitar
        Number idA = JsonPath.read(mvc.perform(get("/api/auth/dispositivos").header("Authorization", a))
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        String otra = "Bearer " + JsonPath.read(mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"sincorreo\",\"contrasena\":\"sincorreo123\"}").header("X-Dispositivo", "cel-z"))
                .andReturn().getResponse().getContentAsString(), "$.token");
        mvc.perform(delete("/api/auth/dispositivos/" + idA).header("Authorization", otra)).andExpect(status().isNotFound());
    }

    @Test
    void cambiarLaContrasenaCierraLosDemasCelularesPeroNoElPropio() throws Exception {
        String a = entrar("cel-a", "Celular A");
        String b = entrar("cel-b", "Celular B");
        String nuevo = "Bearer " + JsonPath.read(mvc.perform(put("/api/auth/contrasena").header("Authorization", a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"laura12345\",\"contrasenaNueva\":\"laura-nueva-2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");

        mvc.perform(get("/api/auth/dispositivos").header("Authorization", b)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/dispositivos").header("Authorization", a)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/dispositivos").header("Authorization", nuevo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre=='Celular A')].actual", contains(true)))
                .andExpect(jsonPath("$[?(@.nombre=='Celular B')].sesionAbierta", contains(false)));
    }

    @Test
    void laAppAnteriorYLasCuentasSinCorreoEntranSinCodigo() throws Exception {
        // Sin X-Dispositivo (app anterior): como antes, sin código ni sesión de celular
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").exists());
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"sincorreo\",\"contrasena\":\"sincorreo123\"}").header("X-Dispositivo", "cel-z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").exists());
        verify(correo, never()).enviar(any(), any(), any(), any());
    }

    @Test
    void reenviarElCodigoEsperaUnMinuto() throws Exception {
        String verificacion = pedir("cel-a", "Celular A");
        mvc.perform(post("/api/auth/verificar/reenviar").header("X-Dispositivo", "cel-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"verificacionId\":\"" + verificacion + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    // ---- ayudas ----

    private ResultActions login(String celular, String nombre) throws Exception {
        var peticion = post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN)
                .header("X-Dispositivo", celular).header("X-Dispositivo-Nombre", nombre);
        return mvc.perform(conjunto == null ? peticion : peticion.header("X-Conjunto", conjunto));
    }

    /** Login en un celular nuevo: el id de la verificación. */
    private String pedir(String celular, String nombre) throws Exception {
        return JsonPath.read(login(celular, nombre).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.verificacionId");
    }

    /** Login con código en un celular nuevo: "Bearer ..." de su sesión. */
    private String entrar(String celular, String nombre) throws Exception {
        String verificacion = pedir(celular, nombre);
        return "Bearer " + JsonPath.read(verificar(verificacion, ultimoCodigo(), celular).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");
    }

    private ResultActions verificar(String verificacion, String codigo, String celular) throws Exception {
        var peticion = post("/api/auth/verificar").header("X-Dispositivo", celular)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"verificacionId\":\"" + verificacion + "\",\"codigo\":\"" + codigo + "\"}");
        return mvc.perform(conjunto == null ? peticion : peticion.header("X-Conjunto", conjunto));
    }

    /** El código del último correo enviado (va al inicio del asunto). */
    private String ultimoCodigo() {
        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        verify(correo, atLeastOnce()).enviar(any(), any(), asunto.capture(), any());
        return asunto.getValue().substring(0, 6);
    }
}
