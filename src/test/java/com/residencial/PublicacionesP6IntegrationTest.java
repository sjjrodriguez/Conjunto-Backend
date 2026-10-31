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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P6 bloque 3: categoría, audiencia, torre, fijadas y marcas de lectura del feed. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicacionesP6IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired PublicacionRepository publicacionRepo;
    @Autowired PublicacionLecturaRepository lecturaRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PasswordEncoder encoder;

    String admin;
    String contador;
    String propietarioT3;
    String arrendatarioT3;
    String propietarioT4;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        adminRepo.save(new Administrador(null, "conta", encoder.encode("conta123"), Administrador.CONTADOR));
        Apartamento a3 = aptoRepo.save(new Apartamento(null, "3", "301", encoder.encode("clave301")));
        Apartamento a4 = aptoRepo.save(new Apartamento(null, "4", "401", encoder.encode("clave401")));
        residenteRepo.save(new Residente(null, "Ana Dueña", "3001", "1", "PROPIETARIO", a3, encoder.encode("dueno123"), Residente.APROBADO));
        residenteRepo.save(new Residente(null, "Beto Arrienda", "3002", "2", "ARRENDATARIO", a3, encoder.encode("arrienda1"), Residente.APROBADO));
        residenteRepo.save(new Residente(null, "Carla Dueña", "3003", "3", "PROPIETARIO", a4, encoder.encode("dueno456"), Residente.APROBADO));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        contador = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"conta\",\"contrasena\":\"conta123\"}");
        propietarioT3 = "Bearer " + token("/api/auth/residente/login",
                "{\"torre\":\"3\",\"apto\":\"301\",\"rol\":\"PROPIETARIO\",\"contrasena\":\"dueno123\"}");
        arrendatarioT3 = "Bearer " + token("/api/auth/residente/login",
                "{\"torre\":\"3\",\"apto\":\"301\",\"rol\":\"ARRENDATARIO\",\"contrasena\":\"arrienda1\"}");
        propietarioT4 = "Bearer " + token("/api/auth/residente/login",
                "{\"torre\":\"4\",\"apto\":\"401\",\"rol\":\"PROPIETARIO\",\"contrasena\":\"dueno456\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
        lecturaRepo.deleteAll();
        publicacionRepo.deleteAll();
        residenteRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void cadaResidenteVeSoloLoDirigidoASuRolYSuTorre() throws Exception {
        publicar("Para todos", "GENERAL", "TODOS", null, false);
        publicar("Solo propietarios", "FINANZAS", "PROPIETARIOS", null, false);
        publicar("Solo arrendatarios", "EVENTO", "ARRENDATARIOS", null, false);
        publicar("Torre 4", "MANTENIMIENTO", "TODOS", "4", false);

        textos(propietarioT3, "Para todos", "Solo propietarios");
        textos(arrendatarioT3, "Para todos", "Solo arrendatarios");
        textos(propietarioT4, "Para todos", "Solo propietarios", "Torre 4");
        // La administración y el contador ven todo
        mvc.perform(get("/api/publicaciones").header("Authorization", admin))
                .andExpect(jsonPath("$.publicaciones.length()").value(4));
        mvc.perform(get("/api/publicaciones").header("Authorization", contador))
                .andExpect(jsonPath("$.publicaciones.length()").value(4));
    }

    @Test
    void lasFijadasVanPrimeroYSoloLaAdministracionLasFija() throws Exception {
        publicar("Vieja", "GENERAL", "TODOS", null, false);
        int id = publicar("Importante", "URGENTE", "TODOS", null, false);
        publicar("Nueva", "GENERAL", "TODOS", null, false);

        mvc.perform(patch("/api/publicaciones/" + id + "/fijar").header("Authorization", propietarioT3)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fijado\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/publicaciones/" + id + "/fijar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fijado\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fijado").value(true));

        mvc.perform(get("/api/publicaciones").header("Authorization", propietarioT3))
                .andExpect(jsonPath("$.publicaciones[0].texto").value("Importante"))
                .andExpect(jsonPath("$.publicaciones[0].categoria").value("URGENTE"))
                .andExpect(jsonPath("$.publicaciones[1].texto").value("Nueva"))
                .andExpect(jsonPath("$.publicaciones[2].texto").value("Vieja"));
    }

    @Test
    void abrirUnaPublicacionLaMarcaLeidaSoloParaQuienLaAbre() throws Exception {
        int id = publicar("Aviso", "GENERAL", "TODOS", null, false);
        mvc.perform(get("/api/publicaciones").header("Authorization", propietarioT3))
                .andExpect(jsonPath("$.publicaciones[0].leida").value(false));

        mvc.perform(post("/api/publicaciones/" + id + "/lectura").header("Authorization", propietarioT3))
                .andExpect(status().isNoContent());
        // Repetirlo no falla ni duplica
        mvc.perform(post("/api/publicaciones/" + id + "/lectura").header("Authorization", propietarioT3))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/publicaciones/99999/lectura").header("Authorization", propietarioT3))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/publicaciones").header("Authorization", propietarioT3))
                .andExpect(jsonPath("$.publicaciones[0].leida").value(true));
        mvc.perform(get("/api/publicaciones").header("Authorization", arrendatarioT3))
                .andExpect(jsonPath("$.publicaciones[0].leida").value(false));
    }

    @Test
    void validaLaCategoriaYLaAudienciaYPermiteEditarEnParcial() throws Exception {
        mvc.perform(multipart("/api/publicaciones").param("texto", "x").param("categoria", "RARA")
                        .header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/publicaciones").param("texto", "x").param("audiencia", "NADIE")
                        .header("Authorization", admin))
                .andExpect(status().isBadRequest());

        int id = publicar("Texto original", "GENERAL", "TODOS", null, false);
        mvc.perform(put("/api/publicaciones/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoria\":\"evento\",\"titulo\":\"Fiesta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Texto original"))
                .andExpect(jsonPath("$.categoria").value("EVENTO"))
                .andExpect(jsonPath("$.titulo").value("Fiesta"));
        // Con lecturas registradas igual se puede eliminar
        mvc.perform(post("/api/publicaciones/" + id + "/lectura").header("Authorization", propietarioT3));
        mvc.perform(delete("/api/publicaciones/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
    }

    private int publicar(String texto, String categoria, String audiencia, String torre, boolean fijado) throws Exception {
        var req = multipart("/api/publicaciones").param("texto", texto).param("categoria", categoria)
                .param("audiencia", audiencia).param("fijado", String.valueOf(fijado)).header("Authorization", admin);
        if (torre != null) req.param("torre", torre);
        String cuerpo = mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.id");
    }

    private void textos(String auth, String... esperados) throws Exception {
        String cuerpo = mvc.perform(get("/api/publicaciones").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<String> vistos = JsonPath.read(cuerpo, "$.publicaciones[*].texto");
        org.assertj.core.api.Assertions.assertThat(vistos).containsExactlyInAnyOrder(esperados);
    }

    private String token(String ruta, String json) throws Exception {
        String cuerpo = mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }
}
