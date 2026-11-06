package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Conjunto;
import com.residencial.repository.AdministradorRepository;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ConjuntoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P10: el teléfono de la portería lo cambia la administración y lo lee el residente (alerta de visita). */
@SpringBootTest
@AutoConfigureMockMvc
class TelefonoPorteriaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired ConjuntoRepository conjuntoRepo;
    @Autowired PasswordEncoder encoder;

    Conjunto conjunto;
    String admin;
    String residente;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        conjunto = conjuntoRepo.save(new Conjunto("Uno Teléfono", "Meta", "Villavicencio", null));
        ConjuntoContexto.en(conjunto.getId(), () -> {
            adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
            aptoRepo.save(new Apartamento(null, "5", "502", encoder.encode("clave502")));
        });
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        residente = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"5\",\"apto\":\"502\",\"contrasena\":\"clave502\"}");
    }

    @AfterEach
    void limpiar() {
        conjuntoRepo.findAll().stream().filter(c -> c.getNombre().endsWith(" Teléfono")).forEach(c -> {
            ConjuntoContexto.en(c.getId(), () -> { aptoRepo.deleteAll(); adminRepo.deleteAll(); });
            conjuntoRepo.delete(c);
        });
    }

    @Test
    void laAdministracionLoCambiaYElResidenteLoLee() throws Exception {
        mvc.perform(put("/api/telefono-porteria").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"601 555 1234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telefono").value("601 555 1234"));
        mvc.perform(get("/api/telefono-porteria").header("Authorization", residente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telefono").value("601 555 1234"));
    }

    @Test
    void elResidenteNoLoCambiaYNoSeAceptaTexto() throws Exception {
        mvc.perform(put("/api/telefono-porteria").header("Authorization", residente)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"3001234567\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/telefono-porteria").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"llamen\"}"))
                .andExpect(status().isBadRequest());
    }

    private String token(String url, String cuerpo) throws Exception {
        String r = mvc.perform(post(url).header("X-Conjunto", conjunto.getId()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(r, "$.token");
    }
}
