package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Apartamento;
import com.residencial.repository.ApartamentoRepository;
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

/**
 * Con SECURITY_ENFORCE=false la app antigua (sin token) sigue funcionando,
 * pero quien envía un token queda sujeto a los permisos.
 */
@SpringBootTest(properties = {
        "app.security.enforce=false",
        "spring.datasource.url=jdbc:h2:mem:transicion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ModoTransicionTest {

    @Autowired MockMvc mvc;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired PasswordEncoder encoder;

    Long apto1;
    Long apto2;

    @BeforeEach
    void datos() {
        aptoRepo.deleteAll();
        apto1 = aptoRepo.save(new Apartamento(null, "2", "201", encoder.encode("clave201"))).getId();
        apto2 = aptoRepo.save(new Apartamento(null, "2", "202", encoder.encode("clave202"))).getId();
    }

    @Test
    void sinTokenSigueFuncionandoComoAntes() throws Exception {
        mvc.perform(get("/api/apartamentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contrasena").doesNotExist());
        mvc.perform(get("/api/apartamentos/" + apto2)).andExpect(status().isOk());
    }

    @Test
    void conTokenSeAplicanLosPermisos() throws Exception {
        String json = mvc.perform(post("/api/auth/apartamento/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"torre\":\"2\",\"apto\":\"201\",\"contrasena\":\"clave201\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(json, "$.token");

        mvc.perform(get("/api/apartamentos").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/apartamentos/" + apto2).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/apartamentos/" + apto1).header("Authorization", bearer))
                .andExpect(status().isOk());
    }

    @Test
    void tokenInvalidoSeRechazaAunqueNoSeExija() throws Exception {
        mvc.perform(get("/api/apartamentos").header("Authorization", "Bearer basura"))
                .andExpect(status().isUnauthorized());
    }
}
