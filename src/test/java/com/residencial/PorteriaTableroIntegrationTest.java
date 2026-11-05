package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Residente;
import com.residencial.model.Vehiculo;
import com.residencial.model.VisitaPorteria;
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

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P8 bloque 5: resumen, quién sigue dentro, historial con búsqueda, directorio y minuta de la portería. */
@SpringBootTest
@AutoConfigureMockMvc
class PorteriaTableroIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired VisitaPorteriaRepository visitaRepo;
    @Autowired MinutaPorteriaRepository minutaRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired PasswordEncoder encoder;

    String admin;
    String porteria;
    String residente;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        adminRepo.save(new Administrador(null, "portero1", encoder.encode("portero123"), Administrador.PORTERIA));
        Apartamento a5 = aptoRepo.save(new Apartamento(null, "5", "502", encoder.encode("clave502")));
        a5.setCitofono1("502");
        aptoRepo.save(a5);
        aptoRepo.save(new Apartamento(null, "10", "1001", encoder.encode("x")));
        aptoRepo.save(new Apartamento(null, "9", "901", encoder.encode("x")));
        residenteRepo.save(new Residente(null, "Laura Muñoz", "3001112233", "111", "PROPIETARIO", a5));
        Residente pendiente = new Residente(null, "Oculto Pendiente", "3009999999", "222", "ARRENDATARIO", a5);
        pendiente.setEstado(Residente.PENDIENTE);
        residenteRepo.save(pendiente);
        vehiculoRepo.save(new Vehiculo(null, "KLM456", "Kia", "Gris", "Carro", a5, Vehiculo.APROBADO, null));
        vehiculoRepo.save(new Vehiculo(null, "NOP789", "Kia", "Gris", "Carro", a5, Vehiculo.PENDIENTE, null));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        porteria = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"portero1\",\"contrasena\":\"portero123\"}");
        residente = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"5\",\"apto\":\"502\",\"contrasena\":\"clave502\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        visitaRepo.deleteAll();
        minutaRepo.deleteAll();
        vehiculoRepo.deleteAll();
        residenteRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void resumenYQuienSigueDentro() throws Exception {
        int a = registrar("José Pérez", "CC 123");
        int b = registrar("Marta Ruiz", null);
        registrar("Sin Respuesta", null);
        responder(a, "AUTORIZAR");
        responder(b, "AUTORIZAR");
        enviar(post("/api/porteria/visitas/" + b + "/salida"), porteria, "").andExpect(status().isOk());

        mvc.perform(get("/api/porteria/resumen").header("Authorization", porteria)).andExpect(status().isOk())
                .andExpect(jsonPath("$.visitasHoy").value(3)).andExpect(jsonPath("$.esperandoRespuesta").value(1))
                .andExpect(jsonPath("$.dentro").value(1)).andExpect(jsonPath("$.minutaHoy").value(0));
        mvc.perform(get("/api/porteria/visitas/dentro").header("Authorization", porteria))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].visitante").value("José Pérez"));

        // Una visita a pie de hace dos días ya no cuenta como dentro
        VisitaPorteria vieja = visitaRepo.findById((long) a).orElseThrow();
        vieja.setCreadaEn(LocalDateTime.now().minusDays(2));
        visitaRepo.save(vieja);
        mvc.perform(get("/api/porteria/visitas/dentro").header("Authorization", porteria)).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/porteria/resumen").header("Authorization", residente)).andExpect(status().isForbidden());
        mvc.perform(get("/api/porteria/resumen").header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void historialConBusquedaSinTildesYPaginas() throws Exception {
        registrar("José Pérez", "CC 123");
        registrar("Marta Ruiz", null);
        for (int i = 0; i < 31; i++) registrar("Domicilio " + i, null);

        mvc.perform(get("/api/porteria/visitas/historial").param("q", "jose perez").header("Authorization", porteria))
                .andExpect(jsonPath("$.visitas.length()").value(1)).andExpect(jsonPath("$.hayMas").value(false));
        mvc.perform(get("/api/porteria/visitas/historial").param("q", "PÉREZ").header("Authorization", porteria))
                .andExpect(jsonPath("$.visitas.length()").value(1));
        mvc.perform(get("/api/porteria/visitas/historial").param("q", "cc 123").header("Authorization", porteria))
                .andExpect(jsonPath("$.visitas[0].visitante").value("José Pérez"));
        mvc.perform(get("/api/porteria/visitas/historial").param("q", "5-502").header("Authorization", porteria))
                .andExpect(jsonPath("$.visitas.length()").value(30)).andExpect(jsonPath("$.hayMas").value(true));
        mvc.perform(get("/api/porteria/visitas/historial").param("pagina", "1").header("Authorization", porteria))
                .andExpect(jsonPath("$.visitas.length()").value(3)).andExpect(jsonPath("$.hayMas").value(false));
        // Un comodín de SQL no lo trae todo
        mvc.perform(get("/api/porteria/visitas/historial").param("q", "zz%").header("Authorization", porteria))
                .andExpect(jsonPath("$.visitas.length()").value(0));
        mvc.perform(get("/api/porteria/visitas/historial").header("Authorization", residente)).andExpect(status().isForbidden());
    }

    @Test
    void directorioConResidentesAprobadosYPlacas() throws Exception {
        mvc.perform(get("/api/porteria/directorio").header("Authorization", porteria)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // Torres en orden numérico: 5, 9, 10
                .andExpect(jsonPath("$[0].torre").value("5")).andExpect(jsonPath("$[1].torre").value("9"))
                .andExpect(jsonPath("$[2].torre").value("10"))
                .andExpect(jsonPath("$[0].citofono1").value("502"))
                .andExpect(jsonPath("$[0].residentes.length()").value(1))
                .andExpect(jsonPath("$[0].residentes[0].nombre").value("Laura Muñoz"))
                .andExpect(jsonPath("$[0].residentes[0].telefono").value("3001112233"))
                .andExpect(jsonPath("$[0].residentes[0].identificacion").doesNotExist())
                .andExpect(jsonPath("$[0].placas.length()").value(1)).andExpect(jsonPath("$[0].placas[0]").value("KLM456"));
        mvc.perform(get("/api/porteria/directorio").param("q", "munoz").header("Authorization", porteria))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].apto").value("502"));
        mvc.perform(get("/api/porteria/directorio").param("q", "klm 456").header("Authorization", porteria))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/porteria/directorio").param("q", "10-1001").header("Authorization", porteria))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].residentes.length()").value(0));
        mvc.perform(get("/api/porteria/directorio").header("Authorization", residente)).andExpect(status().isForbidden());
    }

    @Test
    void minutaDelTurno() throws Exception {
        enviar(post("/api/porteria/minuta"), porteria, "{\"categoria\":\"ronda\",\"texto\":\"Ronda por el parqueadero sin novedad\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.categoria").value("RONDA"));
        enviar(post("/api/porteria/minuta"), porteria, "{\"texto\":\"Llegó un paquete para el 502\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.categoria").value("NOVEDAD"));
        enviar(post("/api/porteria/minuta"), porteria, "{\"texto\":\"  \"}").andExpect(status().isBadRequest());
        enviar(post("/api/porteria/minuta"), porteria, "{\"categoria\":\"CHISME\",\"texto\":\"x\"}").andExpect(status().isBadRequest());
        enviar(post("/api/porteria/minuta"), residente, "{\"texto\":\"x\"}").andExpect(status().isForbidden());

        mvc.perform(get("/api/porteria/minuta").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].texto").value("Llegó un paquete para el 502"))
                .andExpect(jsonPath("$[*].categoria", hasItem("RONDA")));
        mvc.perform(get("/api/porteria/resumen").header("Authorization", porteria)).andExpect(jsonPath("$.minutaHoy").value(2));
    }

    private int registrar(String visitante, String documento) throws Exception {
        String doc = documento == null ? "" : ",\"documento\":\"" + documento + "\"";
        return JsonPath.read(enviar(post("/api/porteria/visitas"), porteria,
                "{\"torre\":\"5\",\"apto\":\"502\",\"visitante\":\"" + visitante + "\"" + doc + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void responder(int id, String decision) throws Exception {
        enviar(patch("/api/porteria/visitas/" + id + "/respuesta"), residente, "{\"decision\":\"" + decision + "\"}")
                .andExpect(status().isOk());
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String auth, String json) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(String ruta, String json) throws Exception {
        String cuerpo = mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }
}
