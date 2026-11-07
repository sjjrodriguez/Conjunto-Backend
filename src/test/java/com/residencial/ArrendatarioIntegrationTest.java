package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Residente;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P11 bloque 9: al pasar un apartamento arrendado a "vive el propietario", la administración
 * desactiva al arrendatario (no entra, se puede reactivar) o lo elimina.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ArrendatarioIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    Residente inquilino;
    String admin;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "6", "601", encoder.encode("clave601")));
        residenteRepo.save(new Residente(null, "Laura Dueña", "3001112233", "1111111", "PROPIETARIO", apto,
                encoder.encode("dueno123"), Residente.APROBADO));
        inquilino = residenteRepo.save(new Residente(null, "Pedro Arrienda", "3002223344", "2222222", "ARRENDATARIO", apto,
                encoder.encode("arrienda1"), Residente.APROBADO));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
    }

    @AfterEach
    void limpiar() {
        residenteRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void desactivarCierraSuSesionNoLoDejaEntrarYSePuedeReactivar() throws Exception {
        String suToken = "Bearer " + login("arrienda1");
        mvc.perform(get("/api/residentes/apartamento/" + apto.getId()).header("Authorization", suToken)).andExpect(status().isOk());

        ocupacion("{\"tipoOcupacion\":\"PROPIETARIO\",\"arrendatario\":\"DESACTIVAR\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoOcupacion").value("PROPIETARIO"))
                .andExpect(jsonPath("$.arrendado").value(false));
        assertThat(residenteRepo.findById(inquilino.getId()).orElseThrow().getEstado()).isEqualTo(Residente.INACTIVO);

        // Su token viejo ya no vale y no puede volver a entrar
        mvc.perform(get("/api/residentes/apartamento/" + apto.getId()).header("Authorization", suToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/residente/login").contentType(MediaType.APPLICATION_JSON).content(cuerpoLogin("arrienda1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("desactivada")));

        mvc.perform(put("/api/apartamentos/" + apto.getId() + "/arrendatario/activar").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoOcupacion").value("ARRENDATARIO"))
                .andExpect(jsonPath("$.arrendado").value(true));
        login("arrienda1");
    }

    @Test
    void eliminarBorraAlArrendatario() throws Exception {
        ocupacion("{\"tipoOcupacion\":\"DESOCUPADO\",\"arrendatario\":\"ELIMINAR\"}").andExpect(status().isOk());
        assertThat(residenteRepo.findById(inquilino.getId())).isEmpty();
        assertThat(residenteRepo.findByApartamentoIdOrderByIdAsc(apto.getId())).hasSize(1);
        mvc.perform(put("/api/apartamentos/" + apto.getId() + "/arrendatario/activar").header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noSeReactivaSiYaHayOtroArrendatarioYLaAccionDebeSerValida() throws Exception {
        ocupacion("{\"tipoOcupacion\":\"PROPIETARIO\",\"arrendatario\":\"OTRA\"}").andExpect(status().isBadRequest());
        ocupacion("{\"tipoOcupacion\":\"PROPIETARIO\",\"arrendatario\":\"DESACTIVAR\"}").andExpect(status().isOk());
        residenteRepo.save(new Residente(null, "Nuevo Inquilino", "3003334455", "3333333", "ARRENDATARIO", apto,
                encoder.encode("nuevo123"), Residente.APROBADO));
        mvc.perform(put("/api/apartamentos/" + apto.getId() + "/arrendatario/activar").header("Authorization", admin))
                .andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions ocupacion(String json) throws Exception {
        return mvc.perform(patch("/api/apartamentos/" + apto.getId() + "/ocupacion-y-banderas")
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String cuerpoLogin(String contrasena) {
        return "{\"torre\":\"6\",\"apto\":\"601\",\"rol\":\"ARRENDATARIO\",\"contrasena\":\"" + contrasena + "\"}";
    }

    private String login(String contrasena) throws Exception {
        return token("/api/auth/residente/login", cuerpoLogin(contrasena));
    }

    private String token(String ruta, String json) throws Exception {
        String cuerpo = mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }
}
