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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P5 bloque 4: parqueaderos por categoría, asignación al aprobar, reasignación y cupos por apartamento. */
@SpringBootTest
@AutoConfigureMockMvc
class ParqueaderosIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired ParqueaderoRepository parqueaderoRepo;
    @Autowired CupoApartamentoRepository cupoAptoRepo;
    @Autowired CupoVehiculoRepository cupoRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    Apartamento vecino;
    String admin;
    String propietario;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "2", "201", encoder.encode("clave201")));
        vecino = aptoRepo.save(new Apartamento(null, "2", "202", encoder.encode("clave202")));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        propietario = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"2\",\"apto\":\"201\",\"contrasena\":\"clave201\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
        parqueaderoRepo.deleteAll();
        vehiculoRepo.deleteAll();
        cupoAptoRepo.deleteAll();
        cupoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void alAprobarElVehiculoSeLeAsignaParqueaderoYLuegoSePuedeReasignar() throws Exception {
        enviar(post("/api/parqueaderos/generar"), admin, "{\"tipo\":\"carro\",\"cantidad\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].numero").value("C-01"));
        enviar(post("/api/parqueaderos/generar"), admin, "{\"tipo\":\"invitados\",\"cantidad\":2}")
                .andExpect(jsonPath("$[1].numero").value("INV-02"));
        // La numeración sigue la que ya hay
        enviar(post("/api/parqueaderos/generar"), admin, "{\"tipo\":\"Carro\",\"cantidad\":1}")
                .andExpect(jsonPath("$[0].numero").value("C-04"));

        int vehiculo = idDe(enviar(post("/api/vehiculos"), propietario,
                "{\"placa\":\"AAA111\",\"tipo\":\"Carro\",\"apartamentoId\":" + apto.getId() + "}"));
        long c01 = parqueaderoRepo.findAll().stream().filter(p -> p.getNumero().equals("C-01")).findFirst().orElseThrow().getId();
        enviar(put("/api/vehiculos/" + vehiculo + "/estado"), admin, "{\"estado\":\"aprobado\",\"parqueaderoId\":" + c01 + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parqueadero").value("C-01"));
        assertThat(notificacionRepo.findAll()).anyMatch(n -> n.getMensaje().contains("Parqueadero C-01"));
        mvc.perform(get("/api/vehiculos/apartamento/" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$[0].parqueadero").value("C-01"));

        mvc.perform(get("/api/parqueaderos/resumen").header("Authorization", admin))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.asignados").value(1))
                .andExpect(jsonPath("$.libres").value(3))
                .andExpect(jsonPath("$.invitados").value(2));
        mvc.perform(get("/api/parqueaderos?estado=asignado").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].torre").value("2"))
                .andExpect(jsonPath("$[0].placa").value("AAA111"));

        // Reasignarlo al carro del vecino: el primero queda sin parqueadero
        Vehiculo otro = vehiculoRepo.save(new Vehiculo(null, "BBB222", null, null, "Carro", vecino, Vehiculo.APROBADO, null));
        Vehiculo moto = vehiculoRepo.save(new Vehiculo(null, "MOT11A", null, null, "Moto", vecino, Vehiculo.APROBADO, null));
        enviar(put("/api/parqueaderos/" + c01 + "/asignacion"), admin, "{\"vehiculoId\":" + moto.getId() + "}")
                .andExpect(status().isBadRequest());
        enviar(put("/api/parqueaderos/" + c01 + "/asignacion"), admin, "{\"vehiculoId\":" + otro.getId() + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("libéralo")));
        enviar(put("/api/parqueaderos/" + c01 + "/asignacion"), admin, "{\"vehiculoId\":null}")
                .andExpect(jsonPath("$.estado").value("LIBRE"));
        enviar(put("/api/parqueaderos/" + c01 + "/asignacion"), admin, "{\"vehiculoId\":" + otro.getId() + "}")
                .andExpect(jsonPath("$.apto").value("202"));
        long invitados = parqueaderoRepo.findAll().stream().filter(p -> p.getTipo().equals("Invitados")).findFirst().orElseThrow().getId();
        enviar(put("/api/parqueaderos/" + invitados + "/asignacion"), admin, "{\"vehiculoId\":" + otro.getId() + "}")
                .andExpect(status().isBadRequest());

        // Ocupado no se borra; al eliminar el vehículo queda libre
        mvc.perform(delete("/api/parqueaderos/" + c01).header("Authorization", admin)).andExpect(status().isBadRequest());
        vehiculoRepo.delete(vehiculoRepo.findById(otro.getId()).orElseThrow());
        mvc.perform(delete("/api/parqueaderos/" + c01).header("Authorization", admin)).andExpect(status().isNoContent());

        mvc.perform(get("/api/parqueaderos").header("Authorization", propietario)).andExpect(status().isForbidden());
    }

    @Test
    void cadaApartamentoPuedeTenerSusPropiosCupos() throws Exception {
        enviar(put("/api/vehiculos/cupos/apartamento/" + apto.getId()), admin, "[{\"tipo\":\"Carro\",\"maximo\":2}]")
                .andExpect(status().isOk());
        mvc.perform(get("/api/vehiculos/cupos?apartamentoId=" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$[0].tipo").value("Carro"))
                .andExpect(jsonPath("$[0].maximo").value(2))
                .andExpect(jsonPath("$[0].personalizado").value(true))
                .andExpect(jsonPath("$[1].personalizado").value(false));
        enviar(post("/api/vehiculos"), propietario, "{\"placa\":\"AAA111\",\"tipo\":\"Carro\",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isOk());
        enviar(post("/api/vehiculos"), propietario, "{\"placa\":\"AAA112\",\"tipo\":\"Carro\",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isOk());
        enviar(post("/api/vehiculos"), propietario, "{\"placa\":\"AAA113\",\"tipo\":\"Carro\",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isBadRequest());
        // Vuelve al general
        enviar(put("/api/vehiculos/cupos/apartamento/" + apto.getId()), admin, "[{\"tipo\":\"Carro\",\"maximo\":null}]")
                .andExpect(jsonPath("$[0].maximo").value(1));
        enviar(put("/api/vehiculos/cupos/apartamento/" + apto.getId()), propietario, "[{\"tipo\":\"Carro\",\"maximo\":5}]")
                .andExpect(status().isForbidden());
    }

    private int idDe(ResultActions r) throws Exception {
        return JsonPath.read(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String auth, String json) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(String url, String json) throws Exception {
        String respuesta = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
