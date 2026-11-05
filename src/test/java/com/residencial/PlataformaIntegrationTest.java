package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.model.Conjunto;
import com.residencial.model.OperadorPlataforma;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P7 bloque 3: la plataforma crea conjuntos con su administrador principal, los edita, los
 * suspende (nadie del conjunto entra) y restablece la contraseña del principal. Su token no sirve
 * para los datos de ningún conjunto, y lo que hace queda en la auditoría.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlataformaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ConjuntoRepository conjuntoRepo;
    @Autowired AdministradorRepository adminRepo;
    @Autowired OperadorPlataformaRepository operadorRepo;
    @Autowired AuditoriaPlataformaRepository auditoriaRepo;
    @Autowired PasswordEncoder encoder;

    String token;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        operadorRepo.save(new OperadorPlataforma("operador", encoder.encode("clave-plataforma"), "Operadora"));
        String cuerpo = mvc.perform(post("/api/plataforma/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"Operador\",\"contrasena\":\"clave-plataforma\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("PLATAFORMA"))
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(cuerpo, "$.token");
    }

    @AfterEach
    void limpiar() {
        for (Conjunto c : conjuntoRepo.findAll()) {
            if (c.getId() == ConjuntoContexto.PREDETERMINADO) continue;
            ConjuntoContexto.en(c.getId(), () -> adminRepo.deleteAll());
            conjuntoRepo.delete(c);
        }
        auditoriaRepo.deleteAll();
        operadorRepo.deleteAll();
    }

    @Test
    void creaElConjuntoYSuPrincipalEntraConLaContrasenaTemporal() throws Exception {
        String acceso = crear("Torres del Parque", "valle del cauca", "Cali", "admin.torres");
        long id = ((Number) JsonPath.read(acceso, "$.conjuntoId")).longValue();
        String temporal = JsonPath.read(acceso, "$.contrasenaTemporal");

        mvc.perform(post("/api/auth/admin/login").header("X-Conjunto", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin.torres\",\"contrasena\":\"" + temporal + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(true))
                .andExpect(jsonPath("$.conjuntoNombre").value("Torres del Parque"));

        mvc.perform(get("/api/plataforma/conjuntos/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departamento").value("Valle del Cauca"))
                .andExpect(jsonPath("$.principal").value("admin.torres"))
                .andExpect(jsonPath("$.cuentasAdministracion").value(1));
        // Ya se puede elegir en las apps
        mvc.perform(get("/api/conjuntos").param("departamento", "Valle del Cauca").param("municipio", "cali"))
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));
        mvc.perform(get("/api/plataforma/auditoria").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].accion").value("CREAR_CONJUNTO"))
                .andExpect(jsonPath("$[0].operador").value("operador"))
                .andExpect(jsonPath("$[1].accion").value("INICIO_SESION"));
    }

    @Test
    void panelDetalleYAuditoriaConFiltros() throws Exception {
        String a = crear("Panel Torres", "Valle del Cauca", "Palmira", "admin.panel1");
        long torres = ((Number) JsonPath.read(a, "$.conjuntoId")).longValue();
        crear("Panel Mirador", "Antioquia", "Envigado", "admin.panel2");
        mvc.perform(put("/api/plataforma/conjuntos/" + torres + "/estado").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false,\"motivo\":\"Mora en el pago\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/plataforma/panel").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.conjuntosSuspendidos").value(1))
                .andExpect(jsonPath("$.conjuntosActivos").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.conjuntosNuevosMes").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.porDepartamento[*].departamento", hasItems("Valle del Cauca", "Antioquia")))
                .andExpect(jsonPath("$.ultimosEventos[0].accion").value("SUSPENDER_CONJUNTO"))
                .andExpect(jsonPath("$.ultimosEventos[0].conjunto").value("Panel Torres"));

        mvc.perform(get("/api/plataforma/conjuntos/" + torres + "/detalle").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conjunto.nombre").value("Panel Torres"))
                .andExpect(jsonPath("$.conjunto.activo").value(false))
                .andExpect(jsonPath("$.cifras.apartamentos").value(0))
                .andExpect(jsonPath("$.cifras.guardias").value(0))
                .andExpect(jsonPath("$.cuentas[0].usuario").value("admin.panel1"))
                .andExpect(jsonPath("$.eventos.length()").value(2))
                .andExpect(jsonPath("$.eventos[0].detalle").value("Mora en el pago"));
        mvc.perform(get("/api/plataforma/conjuntos/999999/detalle").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/plataforma/auditoria/buscar").param("accion", "crear_conjunto").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.eventos.length()").value(2)).andExpect(jsonPath("$.hayMas").value(false));
        mvc.perform(get("/api/plataforma/auditoria/buscar").param("q", "MORA").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.eventos.length()").value(1));
        mvc.perform(get("/api/plataforma/auditoria/buscar").param("q", "panel mirador").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.eventos.length()").value(1));
        mvc.perform(get("/api/plataforma/auditoria/buscar").param("conjunto", String.valueOf(torres))
                        .param("desde", java.time.LocalDate.now().toString()).param("hasta", java.time.LocalDate.now().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.eventos.length()").value(2));
        mvc.perform(get("/api/plataforma/auditoria/buscar").param("desde", java.time.LocalDate.now().plusDays(1).toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.eventos.length()").value(0));
        mvc.perform(get("/api/plataforma/auditoria/buscar").param("accion", "BORRAR_TODO").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        // Sin token de plataforma, nada
        mvc.perform(get("/api/plataforma/panel")).andExpect(status().isUnauthorized());
    }

    @Test
    void elTokenDePlataformaNoSirveParaLosDatosDeUnConjuntoNiAlReves() throws Exception {
        mvc.perform(get("/api/apartamentos").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/plataforma/conjuntos")).andExpect(status().isUnauthorized());

        String acceso = crear("Bosque Alto", "Antioquia", "Envigado", "jefe");
        String admin = loginAdmin(acceso);
        mvc.perform(get("/api/plataforma/conjuntos").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspenderCortaElAccesoDelConjuntoYReactivarLoDevuelve() throws Exception {
        String acceso = crear("Mirador", "Santander", "Bucaramanga", "mirador");
        long id = ((Number) JsonPath.read(acceso, "$.conjuntoId")).longValue();
        String admin = loginAdmin(acceso);

        mvc.perform(put("/api/plataforma/conjuntos/" + id + "/estado").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false,\"motivo\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/plataforma/conjuntos/" + id + "/estado").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false,\"motivo\":\"Contrato vencido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mvc.perform(get("/api/apartamentos").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", containsString("suspendido")));
        mvc.perform(post("/api/auth/admin/login").header("X-Conjunto", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"mirador\",\"contrasena\":\"" + JsonPath.read(acceso, "$.contrasenaTemporal") + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/conjuntos")).andExpect(jsonPath("$[*].id", not(hasItem((int) id))));

        mvc.perform(put("/api/plataforma/conjuntos/" + id + "/estado").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true,\"motivo\":\"Renovado\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/apartamentos").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(get("/api/plataforma/auditoria").param("conjunto", String.valueOf(id)).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].accion").value("REACTIVAR_CONJUNTO"))
                .andExpect(jsonPath("$[1].accion").value("SUSPENDER_CONJUNTO"))
                .andExpect(jsonPath("$[1].detalle").value("Contrato vencido"));
    }

    @Test
    void restablecerLaContrasenaDelPrincipalCierraSusSesiones() throws Exception {
        String acceso = crear("Altos del Lago", "Cundinamarca", "Chía", "lago");
        long id = ((Number) JsonPath.read(acceso, "$.conjuntoId")).longValue();
        String admin = loginAdmin(acceso);

        String nuevo = mvc.perform(post("/api/plataforma/conjuntos/" + id + "/principal/restablecer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("lago"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/apartamentos").header("Authorization", "Bearer " + admin)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/admin/login").header("X-Conjunto", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"lago\",\"contrasena\":\"" + JsonPath.read(acceso, "$.contrasenaTemporal") + "\"}"))
                .andExpect(status().isUnauthorized());
        loginAdmin(nuevo);
    }

    @Test
    void validaElDepartamentoNoRepiteNombresYEdita() throws Exception {
        mvc.perform(post("/api/plataforma/conjuntos").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Uno", "Gotham", "Cali", "uno")))
                .andExpect(status().isBadRequest());
        String acceso = crear("Los Robles", "Meta", "Villavicencio", "robles");
        long id = ((Number) JsonPath.read(acceso, "$.conjuntoId")).longValue();
        mvc.perform(post("/api/plataforma/conjuntos").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("los robles", "Meta", "villavicencio", "otro")))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/plataforma/conjuntos/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Los  Robles Club\",\"departamento\":\"meta\",\"municipio\":\"Villavicencio\",\"direccion\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Los Robles Club"))
                .andExpect(jsonPath("$.direccion").value(nullValue()));
        mvc.perform(get("/api/plataforma/conjuntos/" + id + "/administracion").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].usuario").value("robles"))
                .andExpect(jsonPath("$[0].principal").value(true))
                .andExpect(jsonPath("$[0].contrasena").doesNotExist());
        mvc.perform(put("/api/plataforma/conjuntos/999999").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"departamento\":\"Meta\",\"municipio\":\"Y\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cerrarSesionInvalidaElToken() throws Exception {
        mvc.perform(post("/api/plataforma/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/plataforma/conjuntos").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/plataforma/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"operador\",\"contrasena\":\"otra\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String crear(String nombre, String departamento, String municipio, String usuario) throws Exception {
        return mvc.perform(post("/api/plataforma/conjuntos").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nombre, departamento, municipio, usuario)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contrasenaTemporal", hasLength(12)))
                .andReturn().getResponse().getContentAsString();
    }

    private static String cuerpo(String nombre, String departamento, String municipio, String usuario) {
        return "{\"nombre\":\"" + nombre + "\",\"departamento\":\"" + departamento + "\",\"municipio\":\"" + municipio
                + "\",\"direccion\":\"Calle 5\",\"usuarioPrincipal\":\"" + usuario + "\",\"nombrePrincipal\":\"Admin "
                + nombre + "\",\"correoPrincipal\":\"\"}";
    }

    private String loginAdmin(String acceso) throws Exception {
        long id = ((Number) JsonPath.read(acceso, "$.conjuntoId")).longValue();
        String cuerpo = mvc.perform(post("/api/auth/admin/login").header("X-Conjunto", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + JsonPath.read(acceso, "$.usuario") + "\",\"contrasena\":\""
                                + JsonPath.read(acceso, "$.contrasenaTemporal") + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }
}
