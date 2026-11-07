package com.residencial;

import com.jayway.jsonpath.JsonPath;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Cuota;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P11 bloques 6 y 7: lo que el contador cambia va al administrador, que lo aprueba o lo devuelve. */
@SpringBootTest
@AutoConfigureMockMvc
class ContabilidadIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired CuotaRepository cuotaRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired SolicitudContableRepository solicitudRepo;
    @Autowired SolicitudContableHistorialRepository historialRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento a101;
    Apartamento a102;
    String admin;
    String contador;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        adminRepo.save(new Administrador(null, "conta", encoder.encode("conta12345"), Administrador.CONTADOR));
        a101 = aptoRepo.save(new Apartamento(null, "1", "101", encoder.encode("x")));
        a102 = aptoRepo.save(new Apartamento(null, "1", "102", encoder.encode("x")));
        admin = "Bearer " + token("{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        contador = "Bearer " + token("{\"usuario\":\"conta\",\"contrasena\":\"conta12345\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        historialRepo.deleteAll();
        solicitudRepo.deleteAll();
        cuotaRepo.deleteAll();
        pagoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void laCuotaNuevaSeDevuelveSeCorrigeSeApruebaYSeEdita() throws Exception {
        String cuerpo = "{\"tipo\":\"CUOTA_NUEVA\",\"nota\":\"Multa por ruido\",\"detalle\":{\"apartamentoId\":" + a101.getId()
                + ",\"periodo\":\"2026-11\",\"concepto\":\"Multa\",\"valor\":50000,\"fechaLimite\":\"2026-11-30\"}}";
        int id = JsonPath.read(enviar(post("/api/cartera/solicitudes"), contador, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE")).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.resumen").value(containsString("Multa")))
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(cuotaRepo.findAll()).isEmpty();

        // El administrador ve el detalle completo; devolverla exige el motivo
        mvc.perform(get("/api/cartera/solicitudes/" + id).header("Authorization", admin))
                .andExpect(jsonPath("$.puedeRevisar").value(true)).andExpect(jsonPath("$.puedeEditar").value(false))
                .andExpect(jsonPath("$.lineas[?(@.etiqueta=='Valor')].valor").value(hasItem("$50.000")));
        enviar(put("/api/cartera/solicitudes/" + id), admin, "{\"aprobada\":false}").andExpect(status().isBadRequest());
        enviar(put("/api/cartera/solicitudes/" + id), admin, "{\"aprobada\":false,\"motivo\":\"La multa es de 80.000\"}")
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));
        // La respuesta le llega al contador y no al administrador
        mvc.perform(get("/api/notificaciones").header("Authorization", contador))
                .andExpect(jsonPath("$[0].tipo").value("SOLICITUD_CONTABLE_RECHAZADA"))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("La multa es de 80.000")));
        mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andExpect(jsonPath("$[*].tipo", everyItem(not("SOLICITUD_CONTABLE_RECHAZADA"))));

        // Solo el contador la corrige; el administrador no
        String corregida = "{\"detalle\":{\"apartamentoId\":" + a101.getId()
                + ",\"periodo\":\"2026-11\",\"concepto\":\"Multa\",\"valor\":80000,\"fechaLimite\":\"2026-11-30\"}}";
        enviar(put("/api/cartera/solicitudes/" + id + "/propuesta"), admin, corregida).andExpect(status().isForbidden());
        enviar(put("/api/cartera/solicitudes/" + id), contador, "{\"aprobada\":true}").andExpect(status().isForbidden());
        enviar(put("/api/cartera/solicitudes/" + id + "/propuesta"), contador, corregida)
                .andExpect(jsonPath("$.estado").value("PENDIENTE")).andExpect(jsonPath("$.version").value(2));
        enviar(put("/api/cartera/solicitudes/" + id), admin, "{\"aprobada\":true}")
                .andExpect(jsonPath("$.estado").value("APROBADA"));
        Cuota multa = cuotaRepo.findAll().get(0);
        assertThat(multa.getConcepto()).isEqualTo("Multa");
        assertThat(multa.getValor()).isEqualByComparingTo("80000");
        assertThat(multa.getReferenciaPago()).startsWith("MC-");
        assertThat(multa.getSolicitudId()).isEqualTo((long) id);

        // Después de aprobada, el contador la edita y vuelve al administrador
        enviar(put("/api/cartera/solicitudes/" + id + "/propuesta"), contador, corregida.replace("80000", "75000"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE")).andExpect(jsonPath("$.edicion").value(true))
                .andExpect(jsonPath("$.version").value(3));
        mvc.perform(get("/api/cartera/solicitudes/" + id).header("Authorization", admin))
                .andExpect(jsonPath("$.lineasAprobadas[?(@.etiqueta=='Valor')].valor").value(hasItem("$80.000")))
                .andExpect(jsonPath("$.lineas[?(@.etiqueta=='Valor')].valor").value(hasItem("$75.000")));
        assertThat(cuotaRepo.findAll().get(0).getValor()).isEqualByComparingTo("80000");
        enviar(put("/api/cartera/solicitudes/" + id), admin, "{\"aprobada\":true}").andExpect(status().isOk());
        assertThat(cuotaRepo.findAll()).singleElement().satisfies(c -> assertThat(c.getValor()).isEqualByComparingTo("75000"));

        mvc.perform(get("/api/cartera/solicitudes/" + id).header("Authorization", contador))
                .andExpect(jsonPath("$.historial[*].accion").value(contains("ENVIADA", "RECHAZADA", "CORREGIDA", "APROBADA", "EDITADA", "APROBADA")))
                .andExpect(jsonPath("$.historial[1].motivo").value("La multa es de 80.000"));

        // La cartera del apartamento la muestra con su referencia
        mvc.perform(get("/api/cartera/apartamentos/" + a101.getId()).header("Authorization", contador))
                .andExpect(jsonPath("$.resumen.saldo").value(75000.0))
                .andExpect(jsonPath("$.cuotas[0].concepto").value("Multa"))
                .andExpect(jsonPath("$.cuotas[0].referenciaPago").value(startsWith("MC-")))
                .andExpect(jsonPath("$.solicitudes.length()").value(1));
    }

    @Test
    void laFacturacionPorCoeficienteSeReparteYSeEditaExcluyendoUnApartamento() throws Exception {
        // Sin coeficientes no se puede enviar por coeficiente
        String facturar = "{\"tipo\":\"FACTURACION\",\"detalle\":{\"periodo\":\"2026-12\",\"modo\":\"COEFICIENTE\","
                + "\"total\":1000000,\"fechaLimite\":\"2026-12-10\",\"interesMora\":1.5}}";
        enviar(post("/api/cartera/solicitudes"), contador, facturar).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("coeficiente")));
        enviar(put("/api/cartera/apartamentos/" + a101.getId() + "/coeficiente"), contador, "{\"coeficiente\":0.6}").andExpect(status().isOk());
        enviar(put("/api/cartera/apartamentos/" + a102.getId() + "/coeficiente"), contador, "{\"coeficiente\":0.4}").andExpect(status().isOk());

        enviar(post("/api/cartera/facturacion/vista-previa"), contador,
                "{\"periodo\":\"2026-12\",\"modo\":\"COEFICIENTE\",\"total\":1000000}")
                .andExpect(jsonPath("$.apartamentos").value(2)).andExpect(jsonPath("$.total").value(1000000))
                .andExpect(jsonPath("$.maximo").value(600000)).andExpect(jsonPath("$.minimo").value(400000));

        int id = JsonPath.read(enviar(post("/api/cartera/solicitudes"), contador, facturar).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        // Otra del mismo mes y concepto no
        enviar(post("/api/cartera/solicitudes"), contador, facturar).andExpect(status().isBadRequest());
        enviar(put("/api/cartera/solicitudes/" + id), admin, "{\"aprobada\":true}").andExpect(status().isOk());
        List<Cuota> cuotas = cuotaRepo.findAll();
        assertThat(cuotas).hasSize(2);
        assertThat(cuotas).filteredOn(c -> c.getApartamento().getId().equals(a101.getId()))
                .singleElement().satisfies(c -> assertThat(c.getValor()).isEqualByComparingTo("600000"));

        // Edición: ahora sin el 102 y otro total. La cuota del 102 (sin pagar) se quita
        enviar(put("/api/cartera/solicitudes/" + id + "/propuesta"), contador,
                "{\"detalle\":{\"periodo\":\"2026-12\",\"modo\":\"COEFICIENTE\",\"total\":900000,\"fechaLimite\":\"2026-12-15\","
                        + "\"excluidos\":[" + a102.getId() + "]}}")
                .andExpect(jsonPath("$.edicion").value(true));
        // El mes no se cambia en una edición
        enviar(put("/api/cartera/solicitudes/" + id + "/propuesta"), contador,
                "{\"detalle\":{\"periodo\":\"2027-01\",\"modo\":\"COEFICIENTE\",\"total\":900000}}")
                .andExpect(status().isBadRequest());
        enviar(put("/api/cartera/solicitudes/" + id), admin, "{\"aprobada\":true}").andExpect(status().isOk());
        assertThat(cuotaRepo.findAll()).singleElement().satisfies(c -> {
            assertThat(c.getApartamento().getId()).isEqualTo(a101.getId());
            assertThat(c.getValor()).isEqualByComparingTo("900000");
            assertThat(c.getFechaLimite()).hasToString("2026-12-15");
        });

        mvc.perform(get("/api/cartera/panel").header("Authorization", contador))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recaudo.length()").value(6))
                .andExpect(jsonPath("$.misPendientes").value(0));
        mvc.perform(get("/api/cartera/apartamentos?torre=1").header("Authorization", contador))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].coeficiente").value(0.6));
    }

    @Test
    void elPagoEnLaOficinaYAnularUnaCuota() throws Exception {
        Cuota c1 = cuotaRepo.save(new Cuota(a101, "2026-11", new BigDecimal("300000"), java.time.LocalDate.of(2026, 11, 10)));
        Cuota c2 = cuotaRepo.save(new Cuota(a102, "2026-11", new BigDecimal("300000"), java.time.LocalDate.of(2026, 11, 10)));
        int pago = JsonPath.read(enviar(post("/api/cartera/solicitudes"), contador, "{\"tipo\":\"PAGO_MANUAL\",\"detalle\":{\"cuotaId\":"
                + c1.getId() + ",\"medio\":\"transferencia\",\"fechaPago\":\"2026-11-05\",\"referencia\":\"Bancolombia 123\"}}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        // Mientras espera, la misma cuota no admite otro cambio
        enviar(post("/api/cartera/solicitudes"), contador, "{\"tipo\":\"CUOTA_ANULAR\",\"detalle\":{\"cuotaId\":" + c1.getId()
                + ",\"motivo\":\"Error\"}}").andExpect(status().isBadRequest());
        enviar(put("/api/cartera/solicitudes/" + pago), admin, "{\"aprobada\":true}").andExpect(status().isOk());
        Cuota pagada = cuotaRepo.findById(c1.getId()).orElseThrow();
        assertThat(pagada.pagada()).isTrue();
        assertThat(pagada.getMedioPago()).isEqualTo("TRANSFERENCIA");
        assertThat(pagada.getPagoReferencia()).isEqualTo("Bancolombia 123");

        // Anular sin motivo no; con motivo sí
        enviar(post("/api/cartera/solicitudes"), contador, "{\"tipo\":\"CUOTA_ANULAR\",\"detalle\":{\"cuotaId\":" + c2.getId() + "}}")
                .andExpect(status().isBadRequest());
        int anular = JsonPath.read(enviar(post("/api/cartera/solicitudes"), contador, "{\"tipo\":\"CUOTA_ANULAR\",\"detalle\":{\"cuotaId\":"
                + c2.getId() + ",\"motivo\":\"Se cobró dos veces\"}}").andReturn().getResponse().getContentAsString(), "$.id");
        enviar(put("/api/cartera/solicitudes/" + anular), admin, "{\"aprobada\":true}").andExpect(status().isOk());
        assertThat(cuotaRepo.findById(c2.getId())).isEmpty();
        // Una anulación aprobada no se edita
        enviar(put("/api/cartera/solicitudes/" + anular + "/propuesta"), contador, "{\"detalle\":{\"cuotaId\":" + c2.getId()
                + ",\"motivo\":\"x\"}}").andExpect(status().isBadRequest());

        mvc.perform(get("/api/cartera/solicitudes?estado=aprobada").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(2));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String auth, String cuerpo) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private String token(String json) throws Exception {
        String r = mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(r, "$.token");
    }
}
