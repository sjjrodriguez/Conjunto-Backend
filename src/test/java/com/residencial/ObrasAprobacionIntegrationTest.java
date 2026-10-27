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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P5 bloque 6: obras con certificado ARL por trabajador y aprobación de la administración. */
@SpringBootTest
@AutoConfigureMockMvc
class ObrasAprobacionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired ObraRepository obraRepo;
    @Autowired PersonalObraRepository personalRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    String admin;
    String propietario;
    final String hoy = LocalDate.now().toString();
    final String luego = LocalDate.now().plusDays(10).toString();

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "3", "301", encoder.encode("clave301")));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        propietario = "Bearer " + token("/api/auth/apartamento/login", "{\"torre\":\"3\",\"apto\":\"301\",\"contrasena\":\"clave301\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
        personalRepo.deleteAll();
        obraRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void laObraDelPropietarioEsperaAprobacionYYaAprobadaSoloCambiaSuEstado() throws Exception {
        int obra = idDe(enviar(post("/api/obras"), propietario, obraJson("Remodelación de cocina", apto.getId()))
                .andExpect(jsonPath("$.aprobacion").value("PENDIENTE"))
                .andExpect(jsonPath("$.torre").value("3")));
        assertThat(notificacionRepo.findAll()).anyMatch(n -> n.getTipo().equals("OBRA") && n.getDestinatarioApartamentoId() == null);

        int trabajador = idDe(enviar(post("/api/personal-obra"), propietario,
                "{\"nombreTrabajador\":\"Pedro\",\"cedula\":\"111\",\"arlEstado\":false,\"obraId\":" + obra + "}")
                .andExpect(jsonPath("$.aprobacion").value("PENDIENTE")));
        // Mientras está pendiente se edita toda
        enviar(put("/api/obras/" + obra), propietario, obraJson("Remodelación de cocina y baño", apto.getId()))
                .andExpect(jsonPath("$.descripcionAdecuacion").value("Remodelación de cocina y baño"));

        // Certificado ARL: solo foto o PDF
        mvc.perform(multipart("/api/personal-obra/" + trabajador + "/arl")
                        .file(new MockMultipartFile("archivo", "arl.txt", "text/plain", "hola".getBytes()))
                        .header("Authorization", propietario))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/personal-obra/" + trabajador + "/arl")
                        .file(new MockMultipartFile("archivo", "arl.pdf", "application/pdf", "%PDF-1.4 certificado".getBytes()))
                        .header("Authorization", propietario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneCertificado").value(true))
                .andExpect(jsonPath("$.arlEstado").value(true));
        mvc.perform(get("/api/personal-obra/" + trabajador + "/arl").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("application/pdf"));

        // La administración la encuentra por torre y apto, con su personal pendiente
        mvc.perform(get("/api/obras").param("alcance", "APARTAMENTOS").param("aprobacion", "PENDIENTE").param("q", "3 301")
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].personalPendiente").value(1));
        mvc.perform(get("/api/obras").param("alcance", "CONJUNTO").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(0));
        // Aprobar no es del propietario
        enviar(put("/api/obras/" + obra + "/aprobacion"), propietario, "{\"aprobada\":true}")
                .andExpect(status().isForbidden());

        enviar(put("/api/obras/" + obra + "/aprobacion"), admin, "{\"aprobada\":true}")
                .andExpect(jsonPath("$.aprobacion").value("APROBADA"))
                .andExpect(jsonPath("$.personalPendiente").value(0));
        assertThat(personalRepo.findById((long) trabajador).orElseThrow().getAprobacion()).isEqualTo("APROBADO");
        assertThat(notificacionRepo.findAll()).anyMatch(n -> n.getTipo().equals("OBRA_APROBADA")
                && apto.getId().equals(n.getDestinatarioApartamentoId()));

        // Aprobada: el propietario solo cambia el estado
        String finalizada = "{\"descripcionAdecuacion\":\"Otra cosa\",\"fechaInicio\":\"" + hoy + "\",\"fechaFinEstimada\":\"" + luego
                + "\",\"estadoObra\":\"FINALIZADA\",\"apartamentoId\":" + apto.getId() + "}";
        enviar(put("/api/obras/" + obra), propietario, finalizada)
                .andExpect(jsonPath("$.descripcionAdecuacion").value("Remodelación de cocina y baño"))
                .andExpect(jsonPath("$.estadoObra").value("FINALIZADA"));
        // Y ya no la puede borrar
        mvc.perform(delete("/api/obras/" + obra).header("Authorization", propietario))
                .andExpect(status().isBadRequest());

        // El trabajador nuevo de una obra aprobada queda pendiente y se revisa aparte
        int nuevo = idDe(enviar(post("/api/personal-obra"), propietario,
                "{\"nombreTrabajador\":\"Luis\",\"cedula\":\"222\",\"arlEstado\":true,\"obraId\":" + obra + "}")
                .andExpect(jsonPath("$.aprobacion").value("PENDIENTE")));
        assertThat(notificacionRepo.findAll()).anyMatch(n -> n.getTipo().equals("PERSONAL_OBRA"));
        enviar(put("/api/personal-obra/" + nuevo + "/aprobacion"), admin, "{\"aprobado\":false,\"motivo\":\"ARL vencida\"}")
                .andExpect(jsonPath("$.aprobacion").value("RECHAZADO"))
                .andExpect(jsonPath("$.motivoRechazo").value("ARL vencida"));
        assertThat(notificacionRepo.findAll()).anyMatch(n -> n.getTipo().equals("PERSONAL_OBRA_RECHAZADO")
                && n.getMensaje().contains("ARL vencida"));
    }

    @Test
    void laRechazadaCorregidaVuelveARevisionYLaDelConjuntoQuedaAprobada() throws Exception {
        int obra = idDe(enviar(post("/api/obras"), propietario, obraJson("Pintura", apto.getId())));
        enviar(put("/api/obras/" + obra + "/aprobacion"), admin, "{\"aprobada\":false,\"motivo\":\"Falta la ARL\"}")
                .andExpect(jsonPath("$.aprobacion").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("Falta la ARL"));
        enviar(put("/api/obras/" + obra), propietario, obraJson("Pintura de sala", apto.getId()))
                .andExpect(jsonPath("$.aprobacion").value("PENDIENTE"))
                .andExpect(jsonPath("$.motivoRechazo").doesNotExist());

        // Obra del conjunto: sin apartamento, la registra la administración ya aprobada
        enviar(post("/api/obras"), admin, obraJson("Impermeabilización de la terraza", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobacion").value("APROBADA"));
        mvc.perform(get("/api/obras").param("alcance", "CONJUNTO").param("q", "terraza").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1));
        // El propietario no puede registrar una sin apartamento
        enviar(post("/api/obras"), propietario, obraJson("Otra", null)).andExpect(status().isBadRequest());

        // Sin aprobar, el propietario sí la puede retirar
        mvc.perform(delete("/api/obras/" + obra).header("Authorization", propietario))
                .andExpect(status().isNoContent());
    }

    private String obraJson(String descripcion, Long apartamentoId) {
        return "{\"descripcionAdecuacion\":\"" + descripcion + "\",\"fechaInicio\":\"" + hoy + "\",\"fechaFinEstimada\":\"" + luego
                + "\",\"estadoObra\":\"EN_PROCESO\"" + (apartamentoId == null ? "" : ",\"apartamentoId\":" + apartamentoId) + "}";
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
