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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P6 bloque 7: personal, turnos sin cruce, nómina, presupuesto y quién puede verlos. */
@SpringBootTest
@AutoConfigureMockMvc
class PersonalConjuntoIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired TurnoTrabajadorRepository turnoRepo;
    @Autowired NominaTrabajadorRepository nominaRepo;
    @Autowired PresupuestoPersonalRepository presupuestoRepo;
    @Autowired TrabajadorConjuntoRepository trabajadorRepo;
    @Autowired EmpresaPrestadoraRepository empresaRepo;
    @Autowired PasswordEncoder encoder;

    String admin;
    String contador;
    String porteria;
    String residente;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        adminRepo.save(new Administrador(null, "conta", encoder.encode("conta123"), Administrador.CONTADOR));
        adminRepo.save(new Administrador(null, "porte", encoder.encode("porte123"), Administrador.PORTERIA));
        aptoRepo.save(new Apartamento(null, "1", "101", encoder.encode("clave101")));
        admin = login("admin", "admin123");
        contador = login("conta", "conta123");
        porteria = login("porte", "porte123");
        residente = "Bearer " + JsonPath.read(mvc.perform(post("/api/auth/apartamento/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"torre\":\"1\",\"apto\":\"101\",\"contrasena\":\"clave101\"}")).andReturn().getResponse().getContentAsString(), "$.token");
    }

    @AfterEach
    void limpiar() {
        turnoRepo.deleteAll();
        nominaRepo.deleteAll();
        presupuestoRepo.deleteAll();
        trabajadorRepo.deleteAll();
        empresaRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void soloElAdministradorManejaElPersonalYElContadorLeeElResumen() throws Exception {
        for (String auth : new String[]{contador, porteria, residente}) {
            mvc.perform(get("/api/personal-conjunto/trabajadores").header("Authorization", auth)).andExpect(status().isForbidden());
            mvc.perform(get("/api/personal-conjunto/nomina").param("periodo", "2026-11").header("Authorization", auth)).andExpect(status().isForbidden());
        }
        enviar(put("/api/personal-conjunto/presupuesto?periodo=2026-11"), contador, "[]").andExpect(status().isForbidden());
        mvc.perform(get("/api/personal-conjunto/presupuesto/resumen").param("periodo", "2026-11").header("Authorization", contador))
                .andExpect(status().isOk());
        mvc.perform(get("/api/personal-conjunto/presupuesto/resumen").param("periodo", "2026-11").header("Authorization", porteria))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/personal-conjunto/trabajadores").header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void trabajadoresConEmpresaYValidaciones() throws Exception {
        int empresa = JsonPath.read(enviar(post("/api/personal-conjunto/empresas"), admin, "{\"nombre\":\"Vigilancia SA\",\"nit\":\"900\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        enviar(post("/api/personal-conjunto/empresas"), admin, "{\"nombre\":\"vigilancia sa\"}").andExpect(status().isBadRequest());

        // Por empresa exige la empresa; directo no la lleva
        enviar(post("/api/personal-conjunto/trabajadores"), admin, trabajador("111", "VIGILANTE", "EMPRESA", null)).andExpect(status().isBadRequest());
        enviar(post("/api/personal-conjunto/trabajadores"), admin, trabajador("111", "VIGILANTE", "EMPRESA", empresa))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.empresa").value("Vigilancia SA"));
        enviar(post("/api/personal-conjunto/trabajadores"), admin, trabajador("111", "ASEO", "DIRECTO", null)).andExpect(status().isBadRequest());
        enviar(post("/api/personal-conjunto/trabajadores"), admin, trabajador("222", "PILOTO", "DIRECTO", null)).andExpect(status().isBadRequest());
        enviar(post("/api/personal-conjunto/trabajadores"), admin, trabajador("333", "aseo", "DIRECTO", null))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cargo").value("ASEO")).andExpect(jsonPath("$.empresa").doesNotExist());
        // Una empresa con trabajadores no se borra
        mvc.perform(delete("/api/personal-conjunto/empresas/" + empresa).header("Authorization", admin)).andExpect(status().isBadRequest());
    }

    @Test
    void losTurnosDeUnTrabajadorNoSeCruzan() throws Exception {
        int w = trabajador("444", "VIGILANTE");
        enviar(post("/api/personal-conjunto/turnos"), admin, turno(w, "06:00", "14:00")).andExpect(status().isCreated());
        enviar(post("/api/personal-conjunto/turnos"), admin, turno(w, "13:00", "20:00")).andExpect(status().isConflict());
        enviar(post("/api/personal-conjunto/turnos"), admin, turno(w, "14:00", "22:00")).andExpect(status().isCreated());
        enviar(post("/api/personal-conjunto/turnos"), admin, turno(w, "10:00", "09:00")).andExpect(status().isBadRequest());
        // Otro trabajador sí puede cubrir ese horario
        int otro = trabajador("555", "VIGILANTE");
        enviar(post("/api/personal-conjunto/turnos"), admin, turno(otro, "06:00", "14:00")).andExpect(status().isCreated());
        mvc.perform(get("/api/personal-conjunto/turnos").param("desde", "2026-11-10").param("hasta", "2026-11-10").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void laNominaSeGeneraUnaVezYElResumenCompararConElPresupuesto() throws Exception {
        trabajador("666", "VIGILANTE", "1500000");
        trabajador("777", "ASEO", "1200000");
        mvc.perform(post("/api/personal-conjunto/nomina/generar").param("periodo", "2026-11").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/personal-conjunto/nomina/generar").param("periodo", "2026-11").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/personal-conjunto/nomina/generar").param("periodo", "noviembre").header("Authorization", admin))
                .andExpect(status().isBadRequest());

        String lista = mvc.perform(get("/api/personal-conjunto/nomina").param("periodo", "2026-11").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Integer> ids = JsonPath.read(lista, "$[?(@.cargo=='VIGILANTE')].id");
        int id = ids.get(0);
        enviar(patch("/api/personal-conjunto/nomina/" + id), admin, "{\"bonificaciones\":100000,\"descuentos\":50000}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.neto").value(1550000));
        enviar(patch("/api/personal-conjunto/nomina/" + id), admin, "{\"descuentos\":99999999}").andExpect(status().isBadRequest());
        enviar(patch("/api/personal-conjunto/nomina/" + id), admin, "{\"estado\":\"PAGADA\"}").andExpect(status().isOk());
        enviar(patch("/api/personal-conjunto/nomina/" + id), admin, "{\"bonificaciones\":1}").andExpect(status().isBadRequest());

        enviar(put("/api/personal-conjunto/presupuesto?periodo=2026-11"), admin,
                "[{\"cargo\":\"VIGILANTE\",\"monto\":1600000},{\"cargo\":\"ASEO\",\"monto\":1000000}]").andExpect(status().isOk());
        mvc.perform(get("/api/personal-conjunto/presupuesto/resumen").param("periodo", "2026-11").header("Authorization", contador))
                .andExpect(jsonPath("$.totalPresupuestado").value(2600000))
                .andExpect(jsonPath("$.totalNomina").value(2750000))
                .andExpect(jsonPath("$.diferencia").value(-150000))
                .andExpect(jsonPath("$.lineas[1].diferencia").value(-200000));
    }

    private int trabajador(String documento, String cargo) throws Exception {
        return trabajador(documento, cargo, "1000000");
    }

    private int trabajador(String documento, String cargo, String salario) throws Exception {
        String cuerpo = "{\"nombreCompleto\":\"Persona " + documento + "\",\"documento\":\"" + documento + "\",\"cargo\":\"" + cargo
                + "\",\"vinculo\":\"DIRECTO\",\"salarioBase\":" + salario + "}";
        return JsonPath.read(enviar(post("/api/personal-conjunto/trabajadores"), admin, cuerpo)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String trabajador(String documento, String cargo, String vinculo, Integer empresa) {
        return "{\"nombreCompleto\":\"Persona\",\"documento\":\"" + documento + "\",\"cargo\":\"" + cargo + "\",\"vinculo\":\"" + vinculo
                + "\",\"salarioBase\":1000000" + (empresa == null ? "" : ",\"empresaId\":" + empresa) + "}";
    }

    private static String turno(int trabajador, String inicio, String fin) {
        return "{\"trabajadorId\":" + trabajador + ",\"fecha\":\"2026-11-10\",\"horaInicio\":\"" + inicio + ":00\",\"horaFin\":\"" + fin + ":00\"}";
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String auth, String json) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String login(String usuario, String contrasena) throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(cuerpo, "$.token");
    }
}
