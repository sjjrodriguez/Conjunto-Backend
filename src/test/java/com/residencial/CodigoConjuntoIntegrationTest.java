package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Conjunto;
import com.residencial.repository.AdministradorRepository;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ConjuntoRepository;
import com.residencial.security.CodigoConjunto;
import com.residencial.security.LimitadorLogin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P9 bloque 2: el código diario del conjunto. Con app.conjunto.exigir-codigo=true, el login de
 * residentes solo funciona con el pase que da el código correcto, y solo en su conjunto. La
 * administración y la portería ven el código de hoy; los intentos están limitados.
 */
@SpringBootTest(properties = "app.conjunto.exigir-codigo=true")
@AutoConfigureMockMvc
class CodigoConjuntoIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ConjuntoRepository conjuntoRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired AdministradorRepository adminRepo;
    @Autowired CodigoConjunto codigos;
    @Autowired LimitadorLogin limitador;
    @Autowired PasswordEncoder encoder;

    Conjunto principal;
    Conjunto otro;

    @BeforeEach
    void datos() {
        limpiar();
        principal = conjuntoRepo.save(new Conjunto("Uno Código", "Meta", "Villavicencio", null));
        otro = conjuntoRepo.save(new Conjunto("Otro Código", "Meta", "Villavicencio", null));
        ConjuntoContexto.en(principal.getId(), () -> {
            aptoRepo.save(new Apartamento(null, "1", "101", encoder.encode("clave101")));
            adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
            adminRepo.save(new Administrador(null, "portero", encoder.encode("portero123"), Administrador.PORTERIA));
        });
    }

    @AfterEach
    void limpiar() {
        conjuntoRepo.findAll().stream().filter(c -> c.getNombre().endsWith(" Código")).forEach(c -> {
            ConjuntoContexto.en(c.getId(), () -> { aptoRepo.deleteAll(); adminRepo.deleteAll(); });
            conjuntoRepo.delete(c);
        });
    }

    @Test
    void sinPaseNoEntraYConElCodigoDeHoySi() throws Exception {
        String login = "{\"torre\":\"1\",\"apto\":\"101\",\"contrasena\":\"clave101\"}";
        mvc.perform(post("/api/auth/apartamento/login").header("X-Conjunto", principal.getId()).contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/api/auth/registro/verificar").header("X-Conjunto", principal.getId()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isPreconditionRequired());

        // El de ayer (fuera de los primeros minutos del día) y uno inventado no sirven
        String deAyer = codigos.codigo(principal, LocalDate.now(java.time.ZoneId.of("America/Bogota")).minusDays(1));
        if (java.time.LocalTime.now(java.time.ZoneId.of("America/Bogota")).isAfter(java.time.LocalTime.of(0, 20))
                && !deAyer.equals(codigos.codigoDeHoy(principal))) {
            mvc.perform(post("/api/conjuntos/" + principal.getId() + "/codigo").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"" + deAyer + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/conjuntos/" + principal.getId() + "/codigo").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"12ab56\"}"))
                .andExpect(status().isBadRequest());

        String cuerpo = mvc.perform(post("/api/conjuntos/" + principal.getId() + "/codigo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigos.codigoDeHoy(principal) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conjuntoId").value(principal.getId().intValue()))
                .andReturn().getResponse().getContentAsString();
        String pase = JsonPath.read(cuerpo, "$.pase");

        mvc.perform(post("/api/auth/apartamento/login").header("X-Conjunto", principal.getId()).header(CodigoConjunto.CABECERA, pase)
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk());
        // Un pase inventado o de otro conjunto no sirve
        mvc.perform(post("/api/auth/apartamento/login").header("X-Conjunto", principal.getId()).header(CodigoConjunto.CABECERA, pase + "x")
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isPreconditionRequired());
        String deOtro = codigos.emitirPase(otro);
        mvc.perform(post("/api/auth/apartamento/login").header("X-Conjunto", principal.getId()).header(CodigoConjunto.CABECERA, deOtro)
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isPreconditionRequired());
        // P11: la administración y la portería también lo necesitan
        mvc.perform(post("/api/auth/admin/login").header("X-Conjunto", principal.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}"))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/api/auth/admin/login").header("X-Conjunto", principal.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"portero\",\"contrasena\":\"portero123\"}"))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/api/auth/admin/login").header("X-Conjunto", principal.getId()).header(CodigoConjunto.CABECERA, pase)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void elCodigoCambiaCadaDiaYEsDistintoEnCadaConjunto() {
        LocalDate hoy = LocalDate.of(2026, 11, 5);
        String a = codigos.codigo(principal, hoy);
        assertThat(a).matches("\\d{6}");
        assertThat(codigos.codigo(principal, hoy)).isEqualTo(a);
        // Siete días seguidos: no se repite el mismo todos los días, y otro conjunto tiene otro
        assertThat(java.util.stream.IntStream.range(1, 8).mapToObj(i -> codigos.codigo(principal, hoy.plusDays(i))).distinct().count())
                .isGreaterThan(1);
        assertThat(codigos.codigo(otro, hoy)).isNotEqualTo(a);
    }

    @Test
    void laAdministracionYLaPorteriaVenElCodigoDeHoy() throws Exception {
        String admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        String porteria = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"portero\",\"contrasena\":\"portero123\"}");
        for (String t : new String[]{admin, porteria}) {
            mvc.perform(get("/api/codigo-conjunto").header("Authorization", t)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigo").value(codigos.codigoDeHoy(principal)))
                    .andExpect(jsonPath("$.cambiaEn").exists());
        }
        mvc.perform(get("/api/codigo-conjunto")).andExpect(status().isUnauthorized());
    }

    @Test
    void losIntentosEstanLimitados() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/conjuntos/" + otro.getId() + "/codigo").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"codigo\":\"000000\"}")).andExpect(status().is4xxClientError());
        }
        // Ya bloqueado: ni el correcto entra hasta que pase el tiempo
        mvc.perform(post("/api/conjuntos/" + otro.getId() + "/codigo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigos.codigoDeHoy(otro) + "\"}"))
                .andExpect(status().isTooManyRequests());
        limitador.limpiar("127.0.0.1|codigo-conjunto|" + otro.getId());
    }

    private String token(String ruta, String json) throws Exception {
        String cuerpo = mvc.perform(post(ruta).header("X-Conjunto", principal.getId())
                        .header(CodigoConjunto.CABECERA, codigos.emitirPase(principal)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }
}
