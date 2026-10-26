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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P5 bloque 1: el residente se registra desde la app (propietario si el apartamento no tiene,
 * arrendatario si ya tiene), queda pendiente y entra con torre, apto, rol y contraseña.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistroIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired VisitanteFrecuenteRepository visitanteRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired CuotaRepository cuotaRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    String admin;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "5", "501", encoder.encode("clave501")));
        admin = "Bearer " + JsonPath.read(mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}")).andReturn().getResponse().getContentAsString(), "$.token");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
        visitanteRepo.deleteAll();
        cuotaRepo.deleteAll();
        residenteRepo.deleteAll();
        pagoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void elPrimeroQueSeRegistraEsPropietarioYEntraCuandoLoApruebaLaAdministracion() throws Exception {
        verificar("5", "501").andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("PROPIETARIO"));
        verificar("5", "999").andExpect(status().isNotFound());

        registrar("1111111", "laura@correo.com", true,
                ",\"personas\":[{\"nombreCompleto\":\"Hijo Pérez\",\"identificacion\":\"2222222\"}],"
                        + "\"visitantes\":[{\"nombreCompleto\":\"Abuela Pérez\",\"telefono\":\"3000000000\"}]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("PROPIETARIO"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.requierePropietario").value(false));
        assertThat(visitanteRepo.findByApartamentoIdOrderByNombreCompletoAsc(apto.getId())).hasSize(1);

        // Pendiente: con la contraseña correcta dice por qué no entra; con otra, credenciales incorrectas
        login("PROPIETARIO", "secreta1").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(containsString("administración")));
        login("PROPIETARIO", "otra-cosa").andExpect(status().isUnauthorized());

        // La administración lo ve en pendientes (solo el titular, no su hijo) y le llega el aviso
        mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andExpect(jsonPath("$[0].tipo").value("REGISTRO"));
        String pendientes = mvc.perform(get("/api/residentes/pendientes").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].correo").value("laura@correo.com"))
                .andExpect(jsonPath("$[0].titular").value(true))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(pendientes, "$[0].id");
        mvc.perform(put("/api/residentes/" + id + "/estado").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"aprobado\"}"))
                .andExpect(status().isOk());

        login("PROPIETARIO", "secreta1").andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("PROPIETARIO"))
                .andExpect(jsonPath("$.nombre").value("Laura Pérez"))
                .andExpect(jsonPath("$.torre").value("5"));
        // Su hijo quedó aprobado con él
        assertThat(residenteRepo.findByApartamentoIdOrderByIdAsc(apto.getId()))
                .allMatch(r -> Residente.APROBADO.equals(r.getEstado()));
    }

    @Test
    void siYaHayPropietarioElSiguienteEsArrendatarioYNecesitaTambienAlPropietario() throws Exception {
        residenteRepo.save(new Residente(null, "Dueño", null, "1111111", "PROPIETARIO", apto, encoder.encode("dueno123"), Residente.APROBADO));
        verificar("5", "501").andExpect(jsonPath("$.rol").value("ARRENDATARIO"));
        registrar("3333333", "ana@correo.com", true, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ARRENDATARIO"))
                .andExpect(jsonPath("$.requierePropietario").value(true));
        // Al apartamento (al propietario) le llega la solicitud
        assertThat(notificacionRepo.findAll()).anyMatch(n -> "SOLICITUD_ARRENDATARIO".equals(n.getTipo())
                && apto.getId().equals(n.getDestinatarioApartamentoId()));

        // Con propietario y arrendatario, ya no se puede registrar nadie más
        verificar("5", "501").andExpect(status().isConflict());

        Residente ana = residenteRepo.findTitulares(apto.getId(), "ARRENDATARIO").get(0);
        mvc.perform(put("/api/residentes/" + ana.getId() + "/estado").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"aprobado\"}"));
        login("ARRENDATARIO", "secreta1").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(containsString("propietario")));
        // Cuando también lo aprueba el propietario (bloque 2), entra
        ana = residenteRepo.findById(ana.getId()).orElseThrow();
        ana.setAprobadoPropietario(true);
        residenteRepo.save(ana);
        login("ARRENDATARIO", "secreta1").andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ARRENDATARIO"));
    }

    @Test
    void elRegistroExigeLosDatosYAceptarLosTerminos() throws Exception {
        registrar("1111111", "laura@correo.com", false, "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("términos")));
        registrar("1111111", "no-es-correo", true, "").andExpect(status().isBadRequest());
        registrar("12", "laura@correo.com", true, "").andExpect(status().isBadRequest());
        // El residente que ya había registrado la administración se convierte en titular
        residenteRepo.save(new Residente(null, "Laura", null, "1111111", "PROPIETARIO", apto));
        registrar("1111111", "laura@correo.com", true, "").andExpect(status().isCreated());
        assertThat(residenteRepo.findByApartamentoIdOrderByIdAsc(apto.getId())).hasSize(1)
                .allMatch(Residente::isTitular);
    }

    private ResultActions verificar(String torre, String numero) throws Exception {
        return mvc.perform(post("/api/auth/registro/verificar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"torre\":\"" + torre + "\",\"apto\":\"" + numero + "\"}"));
    }

    private ResultActions registrar(String cedula, String correo, boolean acepta, String extra) throws Exception {
        return mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(
                "{\"torre\":\"5\",\"apto\":\"501\",\"nombres\":\"" + (cedula.equals("3333333") ? "Ana" : "Laura")
                        + "\",\"apellidos\":\"Pérez\",\"cedula\":\"" + cedula + "\",\"telefono\":\"3001112233\","
                        + "\"correo\":\"" + correo + "\",\"contrasena\":\"secreta1\",\"aceptaDatos\":" + acepta
                        + ",\"aceptaTerminos\":" + acepta + extra + "}"));
    }

    private ResultActions login(String rol, String contrasena) throws Exception {
        return mvc.perform(post("/api/auth/residente/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"torre\":\"5\",\"apto\":\"501\",\"rol\":\"" + rol + "\",\"contrasena\":\"" + contrasena + "\"}"));
    }
}
