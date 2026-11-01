package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Conjunto;
import com.residencial.model.VisitaPorteria;
import com.residencial.repository.*;
import com.residencial.security.ConfiguracionService;
import com.residencial.service.PorteriaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P7 bloque 1: varios conjuntos en la misma base. Cada uno ve solo lo suyo, el token manda sobre
 * la cabecera X-Conjunto, el mismo usuario y el mismo apartamento pueden existir en dos conjuntos
 * y los conjuntos se listan sin sesión para elegirlos.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MultiConjuntoIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ConjuntoRepository conjuntoRepo;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired VisitaPorteriaRepository visitaRepo;
    @Autowired ConfiguracionConjuntoRepository configRepo;
    @Autowired ConfiguracionService configuracion;
    @Autowired PorteriaService porteria;
    @Autowired PasswordEncoder encoder;

    long uno;
    long dos;
    Apartamento aptoUno;
    Apartamento aptoDos;

    @BeforeEach
    void datos() {
        limpiar();
        // Primero el inactivo: así los dos de la prueba nunca son el 1, el de las demás pruebas
        Conjunto inactivo = new Conjunto("Cerrado", "Antioquia", "Envigado", null);
        inactivo.setActivo(false);
        conjuntoRepo.save(inactivo);
        uno = conjuntoRepo.save(new Conjunto("Los Pinos", "Bogotá D.C.", "Bogotá D.C.", "Calle 1")).getId();
        dos = conjuntoRepo.save(new Conjunto("El Retiro", "Antioquia", "Medellín", "Carrera 2")).getId();
        aptoUno = ConjuntoContexto.en(uno, () -> {
            adminRepo.save(new Administrador(null, "admin", encoder.encode("clave-uno")));
            return aptoRepo.save(new Apartamento(null, "1", "101", encoder.encode("apto-uno")));
        });
        aptoDos = ConjuntoContexto.en(dos, () -> {
            adminRepo.save(new Administrador(null, "admin", encoder.encode("clave-dos")));
            return aptoRepo.save(new Apartamento(null, "1", "101", encoder.encode("apto-dos")));
        });
    }

    @AfterEach
    void limpiar() {
        for (Conjunto c : conjuntoRepo.findAll()) {
            if (c.getId() == ConjuntoContexto.PREDETERMINADO) continue;
            ConjuntoContexto.en(c.getId(), () -> {
                visitaRepo.deleteAll();
                aptoRepo.deleteAll();
                adminRepo.deleteAll();
                configRepo.deleteAll();
            });
        }
        conjuntoRepo.deleteAll();
    }

    @Test
    void elMismoUsuarioEntraEnCadaConjuntoConSuContrasenaYVeSoloLoSuyo() throws Exception {
        String tokenDos = loginAdmin(dos, "clave-dos");
        // La contraseña del otro conjunto no sirve
        mvc.perform(post("/api/auth/admin/login").header("X-Conjunto", dos).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"contrasena\":\"clave-uno\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(claim(tokenDos, "cid")).isEqualTo(String.valueOf(dos));
        mvc.perform(get("/api/apartamentos").header("Authorization", "Bearer " + tokenDos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(aptoDos.getId()));
        // El apartamento de otro conjunto no existe para él
        mvc.perform(get("/api/apartamentos/" + aptoUno.getId()).header("Authorization", "Bearer " + tokenDos))
                .andExpect(status().isNotFound());
    }

    @Test
    void conSesionLaCabeceraNoCambiaDeConjunto() throws Exception {
        String tokenUno = loginAdmin(uno, "clave-uno");
        mvc.perform(get("/api/apartamentos").header("Authorization", "Bearer " + tokenUno).header("X-Conjunto", dos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(aptoUno.getId()));
    }

    @Test
    void elLoginDelApartamentoBuscaEnElConjuntoElegido() throws Exception {
        mvc.perform(post("/api/auth/apartamento/login").header("X-Conjunto", dos).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"torre\":\"1\",\"apto\":\"101\",\"contrasena\":\"apto-dos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aptoDos.getId()))
                .andExpect(jsonPath("$.conjuntoId").value(dos))
                .andExpect(jsonPath("$.conjuntoNombre").value("El Retiro"));
        mvc.perform(post("/api/auth/apartamento/login").header("X-Conjunto", uno).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"torre\":\"1\",\"apto\":\"101\",\"contrasena\":\"apto-dos\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unaCabeceraInvalidaEs400() throws Exception {
        mvc.perform(post("/api/auth/admin/login").header("X-Conjunto", "abc").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"contrasena\":\"clave-uno\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void losConjuntosActivosSeListanSinSesion() throws Exception {
        mvc.perform(get("/api/conjuntos/ubicaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].departamento").value("Antioquia"))
                .andExpect(jsonPath("$[0].municipios", hasSize(1)))
                .andExpect(jsonPath("$[0].municipios[0]").value("Medellín"));
        mvc.perform(get("/api/conjuntos").param("departamento", "antioquia").param("municipio", "medellín"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("El Retiro"));
    }

    @Test
    void losAjustesSonDeCadaConjunto() {
        ConjuntoContexto.en(dos, () -> configuracion.fijar("vehiculos_alta_solo_admin", true));
        assertThat(ConjuntoContexto.en(dos, () -> configuracion.vehiculosAltaSoloAdmin())).isTrue();
        assertThat(ConjuntoContexto.en(uno, () -> configuracion.vehiculosAltaSoloAdmin())).isFalse();
    }

    @Test
    void lasVisitasVencidasExpiranEnTodosLosConjuntos() {
        Long visita = ConjuntoContexto.en(dos, () -> {
            VisitaPorteria v = new VisitaPorteria();
            v.setApartamento(aptoDos);
            v.setVisitanteNombre("Ana");
            v.setCreadaEn(LocalDateTime.now().minusMinutes(10));
            v.setVenceEn(LocalDateTime.now().minusMinutes(5));
            return visitaRepo.save(v).getId();
        });
        porteria.expirar();
        assertThat(ConjuntoContexto.en(dos, () -> visitaRepo.findById(visita).orElseThrow().getEstado()))
                .isEqualTo(VisitaPorteria.EXPIRADA);
        // Y la visita de un conjunto no se ve desde otro
        assertThat(ConjuntoContexto.en(uno, () -> visitaRepo.findById(visita))).isEmpty();
    }

    private String loginAdmin(long conjunto, String contrasena) throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/admin/login").header("X-Conjunto", conjunto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }

    private static String claim(String token, String nombre) {
        String datos = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return String.valueOf((Object) JsonPath.read(datos, "$." + nombre));
    }
}
