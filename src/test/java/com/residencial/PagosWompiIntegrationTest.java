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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P6 bloque 6: checkout firmado y webhook de Wompi con firma simulada (sin llaves reales). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.wompi.llave-publica=pub_test_123",
        "app.wompi.secreto-integridad=integridad_test",
        "app.wompi.secreto-eventos=eventos_test"})
class PagosWompiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired CuotaRepository cuotaRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired TransaccionPagoRepository transaccionRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    Cuota cuota;
    String residente;
    String otro;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "9", "901", encoder.encode("clave901")));
        aptoRepo.save(new Apartamento(null, "9", "902", encoder.encode("clave902")));
        cuota = cuotaRepo.save(new Cuota(apto, "2026-11", new BigDecimal("350000.00"), LocalDate.now().plusDays(10)));
        residente = "Bearer " + token("{\"torre\":\"9\",\"apto\":\"901\",\"contrasena\":\"clave901\"}");
        otro = "Bearer " + token("{\"torre\":\"9\",\"apto\":\"902\",\"contrasena\":\"clave902\"}");
    }

    @AfterEach
    void limpiar() {
        transaccionRepo.deleteAll();
        notificacionRepo.deleteAll();
        cuotaRepo.deleteAll();
        pagoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void elCheckoutVieneFirmadoConElSecretoDeIntegridad() throws Exception {
        String cuerpo = checkout(residente, cuota.getId()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String referencia = JsonPath.read(cuerpo, "$.referencia");
        URI url = URI.create(JsonPath.read(cuerpo, "$.url"));
        assertThat(url.getHost()).isEqualTo("checkout.wompi.co");
        assertThat(url.getRawQuery()).contains("public-key=pub_test_123", "amount-in-cents=35000000", "currency=COP",
                "reference=" + referencia, "signature:integrity=" + sha256(referencia + "35000000COPintegridad_test"));
        assertThat(transaccionRepo.findByReferencia(referencia)).isPresent();
    }

    @Test
    void soloElApartamentoDeLaCuotaPuedePagarla() throws Exception {
        checkout(otro, cuota.getId()).andExpect(status().isForbidden());
        mvc.perform(post("/api/pagos/checkout").contentType(MediaType.APPLICATION_JSON).content("{\"cuotaId\":" + cuota.getId() + "}"))
                .andExpect(status().isUnauthorized());
        checkout(residente, 99999L).andExpect(status().isNotFound());
    }

    @Test
    void elWebhookAprobadoPagaLaCuotaUnaSolaVez() throws Exception {
        String referencia = JsonPath.read(checkout(residente, cuota.getId()).andReturn().getResponse().getContentAsString(), "$.referencia");

        webhook(evento(referencia, "APPROVED", 35000000L, "tx-1", 1700000000L, true)).andExpect(status().isOk());
        Cuota pagada = cuotaRepo.findById(cuota.getId()).orElseThrow();
        assertThat(pagada.getEstado()).isEqualTo(Cuota.PAGADA);
        assertThat(pagada.getPagadaEn()).isNotNull();
        assertThat(pagoRepo.findByApartamentoId(apto.getId()).orElseThrow().getSaldoPendiente()).isZero();
        assertThat(notificacionRepo.findAll()).hasSize(1).allMatch(n -> "CUOTA_PAGADA".equals(n.getTipo()));
        assertThat(transaccionRepo.findByReferencia(referencia).orElseThrow().getEstado()).isEqualTo("APROBADA");

        // Wompi reintenta: el mismo evento no duplica avisos ni cambia nada
        webhook(evento(referencia, "APPROVED", 35000000L, "tx-1", 1700000000L, true)).andExpect(status().isOk());
        webhook(evento(referencia, "DECLINED", 35000000L, "tx-1", 1700000001L, true)).andExpect(status().isOk());
        assertThat(notificacionRepo.count()).isEqualTo(1);
        assertThat(transaccionRepo.findByReferencia(referencia).orElseThrow().getEstado()).isEqualTo("APROBADA");
        // Y una cuota pagada ya no se cobra otra vez
        checkout(residente, cuota.getId()).andExpect(status().isBadRequest());
    }

    @Test
    void unEventoSinFirmaValidaOConOtroMontoNoPagaNada() throws Exception {
        String referencia = JsonPath.read(checkout(residente, cuota.getId()).andReturn().getResponse().getContentAsString(), "$.referencia");

        webhook(evento(referencia, "APPROVED", 35000000L, "tx-2", 1700000000L, false)).andExpect(status().isUnauthorized());
        webhook("{\"data\":{}}").andExpect(status().isUnauthorized());
        assertThat(cuotaRepo.findById(cuota.getId()).orElseThrow().getEstado()).isEqualTo(Cuota.PENDIENTE);

        // Firma válida pero el monto no es el de la transacción: se ignora
        webhook(evento(referencia, "APPROVED", 100L, "tx-3", 1700000000L, true)).andExpect(status().isOk());
        assertThat(cuotaRepo.findById(cuota.getId()).orElseThrow().getEstado()).isEqualTo(Cuota.PENDIENTE);

        // Rechazada por el banco: la cuota sigue pendiente y se puede intentar de nuevo
        webhook(evento(referencia, "DECLINED", 35000000L, "tx-4", 1700000002L, true)).andExpect(status().isOk());
        assertThat(cuotaRepo.findById(cuota.getId()).orElseThrow().getEstado()).isEqualTo(Cuota.PENDIENTE);
        assertThat(transaccionRepo.findByReferencia(referencia).orElseThrow().getEstado()).isEqualTo("RECHAZADA");
        checkout(residente, cuota.getId()).andExpect(status().isOk());
    }

    private ResultActions checkout(String auth, Long cuotaId) throws Exception {
        return mvc.perform(post("/api/pagos/checkout").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cuotaId\":" + cuotaId + "}"));
    }

    private ResultActions webhook(String json) throws Exception {
        return mvc.perform(post("/api/pagos/webhook/wompi").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Evento de Wompi como lo manda el sandbox; la firma = sha256(id+status+monto+timestamp+secreto de eventos). */
    private static String evento(String referencia, String estado, long centavos, String id, long timestamp, boolean firmaBuena) {
        String checksum = firmaBuena ? sha256(id + estado + centavos + timestamp + "eventos_test") : "0".repeat(64);
        return "{\"event\":\"transaction.updated\",\"data\":{\"transaction\":{\"id\":\"" + id + "\",\"status\":\"" + estado
                + "\",\"amount_in_cents\":" + centavos + ",\"reference\":\"" + referencia + "\"}},"
                + "\"signature\":{\"properties\":[\"transaction.id\",\"transaction.status\",\"transaction.amount_in_cents\"],"
                + "\"checksum\":\"" + checksum + "\"},\"timestamp\":" + timestamp + "}";
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String token(String json) throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/apartamento/login").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }
}
