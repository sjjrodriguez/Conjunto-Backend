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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P5 bloque 3: la administración publica en el feed y a cada apartamento le llega el aviso. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicacionesIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired PublicacionRepository publicacionRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PasswordEncoder encoder;

    String admin;
    String residente;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        aptoRepo.save(new Apartamento(null, "3", "301", encoder.encode("clave301")));
        aptoRepo.save(new Apartamento(null, "3", "302", encoder.encode("clave302")));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        residente = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"3\",\"apto\":\"301\",\"contrasena\":\"clave301\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
        publicacionRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void laAdministracionPublicaTextoImagenYArchivoYLosResidentesLoVen() throws Exception {
        mvc.perform(multipart("/api/publicaciones")
                        .file(new MockMultipartFile("archivos", "piscina.png", "image/png", PNG))
                        .file(new MockMultipartFile("archivos", "reglamento.pdf", "application/pdf", PDF))
                        .param("texto", "Mantenimiento de la piscina el sábado")
                        .header("Authorization", admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adjuntos.length()").value(2))
                .andExpect(jsonPath("$.adjuntos[0].tipo").value("IMAGEN"))
                .andExpect(jsonPath("$.adjuntos[1].tipo").value("ARCHIVO"))
                .andExpect(jsonPath("$.adjuntos[1].nombre").value("reglamento.pdf"));

        // A cada apartamento le llega el aviso
        assertThat(notificacionRepo.findAll()).hasSize(2).allMatch(n -> "PUBLICACION".equals(n.getTipo()));

        String feed = mvc.perform(get("/api/publicaciones").header("Authorization", residente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicaciones[0].texto").value("Mantenimiento de la piscina el sábado"))
                .andExpect(jsonPath("$.publicaciones[0].autor").value("Administración"))
                .andExpect(jsonPath("$.hayMas").value(false))
                .andReturn().getResponse().getContentAsString();
        URI imagen = URI.create(JsonPath.read(feed, "$.publicaciones[0].adjuntos[0].url"));
        mvc.perform(get(imagen.getPath() + "?" + imagen.getRawQuery())).andExpect(status().isOk()).andExpect(content().bytes(PNG));

        // El residente no publica
        mvc.perform(multipart("/api/publicaciones").param("texto", "Hola").header("Authorization", residente))
                .andExpect(status().isForbidden());
    }

    @Test
    void validaElContenidoYPermiteEditarYEliminar() throws Exception {
        mvc.perform(multipart("/api/publicaciones").header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/publicaciones")
                        .file(new MockMultipartFile("archivos", "virus.exe", "application/pdf", new byte[]{'M', 'Z', 0, 0}))
                        .header("Authorization", admin))
                .andExpect(status().isBadRequest());
        assertThat(publicacionRepo.count()).isZero();

        int id = JsonPath.read(mvc.perform(multipart("/api/publicaciones").param("texto", "Asamblea el lunes").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/publicaciones/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Asamblea el martes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Asamblea el martes"))
                .andExpect(jsonPath("$.editadaEn").exists());
        mvc.perform(delete("/api/publicaciones/" + id).header("Authorization", residente)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/publicaciones/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/publicaciones").header("Authorization", residente))
                .andExpect(jsonPath("$.publicaciones.length()").value(0));
    }

    private String token(String url, String json) throws Exception {
        String respuesta = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
