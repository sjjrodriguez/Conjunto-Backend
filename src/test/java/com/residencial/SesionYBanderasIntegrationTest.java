package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Residente;
import com.residencial.repository.*;
import com.residencial.security.ConfiguracionService;
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
 * P6 bloque 2: cerrar sesión invalida el token, el saludo del login por apartamento, las banderas
 * del arrendatario (cartera, pagos, obras), vehículos solo del propietario y cuotas solo del administrador.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SesionYBanderasIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired CuotaRepository cuotaRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired ConfiguracionConjuntoRepository configRepo;
    @Autowired ConfiguracionService configuracion;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    String admin;
    String contador;
    String propietario;
    String arrendatario;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        adminRepo.save(new Administrador(null, "conta", encoder.encode("conta123"), Administrador.CONTADOR));
        apto = aptoRepo.save(new Apartamento(null, "5", "501", encoder.encode("clave501")));
        residenteRepo.save(new Residente(null, "Laura Dueña", "3001112233", "1111111", "PROPIETARIO", apto,
                encoder.encode("dueno123"), Residente.APROBADO));
        residenteRepo.save(new Residente(null, "Pedro Arrienda", "3002223344", "2222222", "ARRENDATARIO", apto,
                encoder.encode("arrienda1"), Residente.APROBADO));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        contador = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"conta\",\"contrasena\":\"conta123\"}");
        propietario = "Bearer " + loginResidente("PROPIETARIO", "dueno123");
        arrendatario = "Bearer " + loginResidente("ARRENDATARIO", "arrienda1");
    }

    @AfterEach
    void limpiar() {
        vehiculoRepo.deleteAll();
        cuotaRepo.deleteAll();
        residenteRepo.deleteAll();
        pagoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
        configRepo.deleteAll();
    }

    @Test
    void cerrarSesionInvalidaElTokenYUnoNuevoVale() throws Exception {
        mvc.perform(get("/api/vehiculos").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/vehiculos").header("Authorization", admin)).andExpect(status().isUnauthorized());

        String nuevo = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        mvc.perform(get("/api/vehiculos").header("Authorization", nuevo)).andExpect(status().isOk());
    }

    @Test
    void cerrarSesionSinTokenEs401() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void elLoginPorApartamentoSaludaConElPropietarioTitular() throws Exception {
        mvc.perform(post("/api/auth/apartamento/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"torre\":\"5\",\"apto\":\"501\",\"contrasena\":\"clave501\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Laura Dueña"))
                .andExpect(jsonPath("$.residenteId").isNumber())
                .andExpect(jsonPath("$.tipoOcupacion").value("PROPIETARIO"))
                .andExpect(jsonPath("$.arrendatarioVeCartera").value(false))
                .andExpect(jsonPath("$.vehiculosAltaSoloAdmin").value(false));
    }

    @Test
    void elArrendatarioSoloVeCarteraYObrasSiLaAdministracionLoPermite() throws Exception {
        String cartera = "/api/cuotas/apartamento/" + apto.getId();
        String obras = "/api/obras/apartamento/" + apto.getId();
        mvc.perform(get(cartera).header("Authorization", arrendatario)).andExpect(status().isForbidden());
        mvc.perform(get(obras).header("Authorization", arrendatario)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pagos/apartamento/" + apto.getId()).header("Authorization", arrendatario)).andExpect(status().isForbidden());
        mvc.perform(put("/api/pagos/apartamento/" + apto.getId() + "/simular").header("Authorization", arrendatario))
                .andExpect(status().isForbidden());
        // El propietario no depende de las banderas
        mvc.perform(get(cartera).header("Authorization", propietario)).andExpect(status().isOk());
        mvc.perform(get(obras).header("Authorization", propietario)).andExpect(status().isOk());

        enviar(patch("/api/apartamentos/" + apto.getId() + "/ocupacion-y-banderas"), admin,
                "{\"tipoOcupacion\":\"arrendatario\",\"arrendatarioVeCartera\":true,\"arrendatarioVeObras\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoOcupacion").value("ARRENDATARIO"))
                .andExpect(jsonPath("$.arrendatarioVeCartera").value(true))
                .andExpect(jsonPath("$.arrendatarioPuedePagar").value(false));

        mvc.perform(get(cartera).header("Authorization", arrendatario)).andExpect(status().isOk());
        mvc.perform(get(obras).header("Authorization", arrendatario)).andExpect(status().isOk());
        mvc.perform(put("/api/pagos/apartamento/" + apto.getId() + "/simular").header("Authorization", arrendatario))
                .andExpect(status().isForbidden());
    }

    @Test
    void soloLaAdministracionCambiaLaOcupacionYSoloConValoresValidos() throws Exception {
        String ruta = "/api/apartamentos/" + apto.getId() + "/ocupacion-y-banderas";
        enviar(patch(ruta), propietario, "{\"tipoOcupacion\":\"DESOCUPADO\"}").andExpect(status().isForbidden());
        enviar(patch(ruta), contador, "{\"tipoOcupacion\":\"DESOCUPADO\"}").andExpect(status().isForbidden());
        enviar(patch(ruta), admin, "{\"tipoOcupacion\":\"OTRA\"}").andExpect(status().isBadRequest());
        enviar(patch(ruta), admin, "{\"tipoOcupacion\":\"DESOCUPADO\"}").andExpect(status().isOk());
    }

    @Test
    void elArrendatarioSoloConsultaLosVehiculosYElPropietarioLosGestionaAunqueEsteArrendado() throws Exception {
        String cuerpo = "{\"tipo\":\"Bicicleta\",\"marca\":\"GW\",\"apartamentoId\":" + apto.getId() + "}";
        mvc.perform(get("/api/vehiculos/apartamento/" + apto.getId()).header("Authorization", arrendatario))
                .andExpect(status().isOk());
        enviar(post("/api/vehiculos"), arrendatario, cuerpo).andExpect(status().isForbidden());
        enviar(post("/api/vehiculos"), propietario, cuerpo).andExpect(status().isOk());

        // Con el ajuste del conjunto, solo la administración registra
        configuracion.fijar("vehiculos_alta_solo_admin", true);
        enviar(post("/api/vehiculos"), propietario, cuerpo).andExpect(status().isForbidden());
        enviar(post("/api/vehiculos"), admin, cuerpo).andExpect(status().isOk());
    }

    @Test
    void elContadorLeeLasCuotasPeroNoLasEditaNiLasBorra() throws Exception {
        mvc.perform(get("/api/cuotas?periodo=2026-11").header("Authorization", contador)).andExpect(status().isOk());
        enviar(put("/api/cuotas/1"), contador, "{\"valor\":1000}").andExpect(status().isForbidden());
        mvc.perform(delete("/api/cuotas/1").header("Authorization", contador)).andExpect(status().isForbidden());
        // El administrador sí llega al servicio (la cuota no existe, pero no es un 403)
        mvc.perform(delete("/api/cuotas/99999").header("Authorization", admin))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String auth, String json) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String loginResidente(String rol, String contrasena) throws Exception {
        return token("/api/auth/residente/login",
                "{\"torre\":\"5\",\"apto\":\"501\",\"rol\":\"" + rol + "\",\"contrasena\":\"" + contrasena + "\"}");
    }

    private String token(String ruta, String json) throws Exception {
        String cuerpo = mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }
}
