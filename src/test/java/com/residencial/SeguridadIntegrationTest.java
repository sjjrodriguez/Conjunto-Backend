package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Pago;
import com.residencial.model.Residente;
import com.residencial.repository.ResidenteRepository;
import org.springframework.http.HttpMethod;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
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
    @Autowired ResidenteRepository residenteRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired PasswordEncoder encoder;

    Long apto1;
    Long apto2;

    @BeforeEach
    void datos() {
        residenteRepo.deleteAll();
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

    // ─── Matriz de roles (P6) ────────────────────────────────────────────────

    /**
     * Cada fila: método, ruta, cuerpo y los roles que SÍ pasan (cualquier respuesta distinta de 401/403:
     * después puede ser 404 o 400 por los datos de prueba). El resto debe recibir exactamente 403.
     */
    @Test
    void matrizDeRolesPorEndpoint() throws Exception {
        adminRepo.save(new Administrador(null, "conta", encoder.encode("conta123"), Administrador.CONTADOR));
        adminRepo.save(new Administrador(null, "porte", encoder.encode("porte123"), Administrador.PORTERIA));
        residenteRepo.save(new Residente(null, "Pedro Arrienda", "3002223344", "2222222", "ARRENDATARIO",
                aptoRepo.findById(apto1).orElseThrow(), encoder.encode("arrienda1"), Residente.APROBADO));
        try {
            Map<String, String> tokens = new LinkedHashMap<>();
            tokens.put("ADMIN", bearer(tokenAdmin()));
            tokens.put("CONTADOR", bearer(token(loginAdmin("conta", "conta123"))));
            tokens.put("PORTERIA", bearer(token(loginAdmin("porte", "porte123"))));
            tokens.put("PROPIETARIO", bearer(tokenApto("1", "101", "clave101")));
            tokens.put("ARRENDATARIO", bearer(token(mvc.perform(post("/api/auth/residente/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"torre\":\"1\",\"apto\":\"101\",\"rol\":\"ARRENDATARIO\",\"contrasena\":\"arrienda1\"}")))));

            String json = "{}";
            String[][] filas = {
                    // Administración
                    {"GET", "/api/apartamentos", null, "ADMIN,CONTADOR"},
                    {"GET", "/api/residentes", null, "ADMIN"},
                    {"GET", "/api/vehiculos", null, "ADMIN"},
                    {"GET", "/api/obras", null, "ADMIN"},
                    {"GET", "/api/reservas", null, "ADMIN"},
                    {"GET", "/api/administradores", null, "ADMIN"},
                    // Cartera: el contador lee y propone; editar o borrar una cuota es del administrador
                    {"GET", "/api/cuotas?periodo=2026-11", null, "ADMIN,CONTADOR"},
                    {"GET", "/api/cuotas/apartamento/" + apto1, null, "ADMIN,CONTADOR,PROPIETARIO"},
                    {"PUT", "/api/cuotas/99999", json, "ADMIN"},
                    {"DELETE", "/api/cuotas/99999", null, "ADMIN"},
                    // Feed y publicaciones
                    {"GET", "/api/publicaciones", null, "ADMIN,CONTADOR,PROPIETARIO,ARRENDATARIO"},
                    {"PATCH", "/api/publicaciones/99999/fijar", "{\"fijado\":true}", "ADMIN"},
                    {"POST", "/api/publicaciones/99999/lectura", null, "ADMIN,CONTADOR,PROPIETARIO,ARRENDATARIO"},
                    // Portería
                    {"GET", "/api/porteria/visitas", null, "ADMIN,PORTERIA"},
                    {"POST", "/api/porteria/visitas", "{\"torre\":\"1\",\"apto\":\"999\",\"visitante\":\"X\"}", "ADMIN,PORTERIA"},
                    {"GET", "/api/porteria/visitas/pendientes", null, "PROPIETARIO,ARRENDATARIO"},
                    {"PATCH", "/api/porteria/visitas/99999/respuesta", "{\"decision\":\"AUTORIZAR\"}", "ADMIN,PORTERIA,PROPIETARIO,ARRENDATARIO"},
                    // Personal y presupuesto del conjunto: solo el administrador; el contador lee el resumen
                    {"GET", "/api/personal-conjunto/trabajadores", null, "ADMIN"},
                    {"POST", "/api/personal-conjunto/nomina/generar?periodo=2026-11", null, "ADMIN"},
                    {"GET", "/api/personal-conjunto/presupuesto/resumen?periodo=2026-11", null, "ADMIN,CONTADOR"},
                    // Datos personales y textos legales
                    {"GET", "/api/solicitudes-datos", null, "ADMIN"},
                    {"GET", "/api/solicitudes-datos/mias", null, "ADMIN,CONTADOR,PORTERIA,PROPIETARIO,ARRENDATARIO"},
                    {"GET", "/api/legal/pendientes", null, "ADMIN,CONTADOR,PORTERIA,PROPIETARIO,ARRENDATARIO"},
                    // Avisos y cuenta: la portería también
                    {"GET", "/api/notificaciones", null, "ADMIN,CONTADOR,PORTERIA,PROPIETARIO,ARRENDATARIO"},
                    {"POST", "/api/auth/logout", null, "ADMIN,CONTADOR,PORTERIA,PROPIETARIO,ARRENDATARIO"},
            };
            for (String[] fila : filas) {
                for (Map.Entry<String, String> rol : tokens.entrySet()) {
                    // El cierre de sesión invalida el token: va al final y con uno nuevo
                    var peticion = request(HttpMethod.valueOf(fila[0]), fila[1])
                            .header("Authorization", rol.getValue()).contentType(MediaType.APPLICATION_JSON);
                    if (fila[2] != null) peticion.content(fila[2]);
                    int estado = mvc.perform(peticion).andReturn().getResponse().getStatus();
                    boolean permitido = Arrays.asList(fila[3].split(",")).contains(rol.getKey());
                    String que = rol.getKey() + " " + fila[0] + " " + fila[1] + " -> " + estado;
                    if (permitido) assertThat(estado).as(que).isNotIn(401, 403);
                    else assertThat(estado).as(que).isEqualTo(403);
                    // La clave del apartamento la comparten todos los que viven ahí: su logout no invalida el token
                    if (fila[1].equals("/api/auth/logout") && !rol.getKey().equals("PROPIETARIO")) {
                        // Con la sesión cerrada, el mismo token ya no vale
                        mvc.perform(get("/api/notificaciones").header("Authorization", rol.getValue()))
                                .andExpect(status().isUnauthorized());
                    }
                }
            }
        } finally {
            residenteRepo.deleteAll();
        }
    }

    @Test
    void lasRutasPublicasYLasProtegidasSinTokenResponden401() throws Exception {
        // Públicas
        mvc.perform(get("/api/legal/textos")).andExpect(status().isOk());
        // El webhook no tiene sesión: sin firma de Wompi válida es 401, nunca 200
        mvc.perform(post("/api/pagos/webhook/wompi").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        // Protegidas
        for (String ruta : new String[]{"/api/porteria/visitas", "/api/personal-conjunto/trabajadores", "/api/publicaciones",
                "/api/solicitudes-datos", "/api/legal/pendientes"}) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/pagos/checkout").contentType(MediaType.APPLICATION_JSON).content("{\"cuotaId\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/biometria/activar").contentType(MediaType.APPLICATION_JSON).content("{\"dispositivoId\":\"x\"}"))
                .andExpect(status().isUnauthorized());
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
