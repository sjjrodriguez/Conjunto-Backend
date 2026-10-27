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

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P4 bloque 7: el contador genera la cuota del mes, el residente sube el comprobante y el
 * contador lo aprueba o lo rechaza; la cartera (pago) se recalcula con las cuotas.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CuotasIntegrationTest {

    // Primeros bytes de un PNG: el backend mira el contenido, no el nombre
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired CuotaRepository cuotaRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired CupoVehiculoRepository cupoRepo;
    @Autowired ReservaRepository reservaRepo;
    @Autowired ZonaComunRepository zonaRepo;
    @Autowired PersonalObraRepository personalRepo;
    @Autowired SolicitudContableRepository solicitudRepo;
    @Autowired ObraRepository obraRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    Apartamento vecino;
    String admin;
    String contador;
    String propietario;
    String otroPropietario;
    String mes = YearMonth.now(ZoneId.of("America/Bogota")).toString();

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        adminRepo.save(new Administrador(null, "conta", encoder.encode("conta123"), Administrador.CONTADOR));
        apto = aptoRepo.save(new Apartamento(null, "7", "701", encoder.encode("clave701")));
        vecino = aptoRepo.save(new Apartamento(null, "7", "702", encoder.encode("clave702")));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        contador = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"conta\",\"contrasena\":\"conta123\"}");
        propietario = "Bearer " + token("/api/auth/apartamento/login",
                "{\"torre\":\"7\",\"apto\":\"701\",\"contrasena\":\"clave701\"}");
        otroPropietario = "Bearer " + token("/api/auth/apartamento/login",
                "{\"torre\":\"7\",\"apto\":\"702\",\"contrasena\":\"clave702\"}");
    }

    // La base H2 se comparte con los demás tests: no dejar filas que bloqueen sus borrados
    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
        solicitudRepo.deleteAll();
        cuotaRepo.deleteAll();
        personalRepo.deleteAll();
        obraRepo.deleteAll();
        reservaRepo.deleteAll();
        zonaRepo.deleteAll();
        vehiculoRepo.deleteAll();
        cupoRepo.deleteAll();
        residenteRepo.deleteAll();
        pagoRepo.deleteAll();
        aptoRepo.deleteAll();
        adminRepo.deleteAll();
    }

    @Test
    void elContadorGeneraLaCuotaDelMesYLaCarteraSeRecalcula() throws Exception {
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"conta\",\"contrasena\":\"conta123\"}"))
                .andExpect(jsonPath("$.rol").value("CONTADOR"));

        // P5: la facturación del contador espera al administrador
        int solicitud = JsonPath.read(generar(contador, 175000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creadas").value(0))
                .andExpect(jsonPath("$.enAprobacion").value(true))
                .andReturn().getResponse().getContentAsString(), "$.solicitudId");
        assertThat(cuotaRepo.findAll()).isEmpty();
        generar(contador, 175000).andExpect(status().isBadRequest());
        mvc.perform(get("/api/cartera/solicitudes?estado=PENDIENTE").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("FACTURACION"))
                .andExpect(jsonPath("$[0].solicitadaPor").value("conta"));
        enviar(put("/api/cartera/solicitudes/" + solicitud), contador, "{\"aprobada\":true}").andExpect(status().isForbidden());
        enviar(put("/api/cartera/solicitudes/" + solicitud), admin, "{\"aprobada\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));
        assertThat(cuotaRepo.findAll()).hasSize(2);
        enviar(put("/api/cartera/solicitudes/" + solicitud), admin, "{\"aprobada\":true}").andExpect(status().isBadRequest());
        // Generar otra vez no duplica
        generar(admin, 175000).andExpect(jsonPath("$.creadas").value(0)).andExpect(jsonPath("$.existentes").value(2));

        mvc.perform(get("/api/cuotas?periodo=" + mes).header("Authorization", contador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].apto").value("701"))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
        mvc.perform(get("/api/cuotas/apartamento/" + apto.getId()).header("Authorization", propietario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].valor").value(175000))
                .andExpect(jsonPath("$[0].fechaLimite").value(YearMonth.parse(mes).atEndOfMonth().toString()));
        mvc.perform(get("/api/pagos/apartamento/" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$.saldoPendiente").value(175000.0))
                .andExpect(jsonPath("$.observaciones").value(containsString("Debe")));
        mvc.perform(get("/api/notificaciones").header("Authorization", propietario))
                .andExpect(jsonPath("$[0].tipo").value("CUOTA"))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("$175.000")));

        // El contador ve la cartera, pero no el resto de la administración; el residente no genera
        mvc.perform(get("/api/pagos").header("Authorization", contador)).andExpect(status().isOk());
        mvc.perform(get("/api/apartamentos").header("Authorization", contador)).andExpect(status().isOk());
        mvc.perform(get("/api/pagos/apartamento/" + vecino.getId()).header("Authorization", contador)).andExpect(status().isOk());
        mvc.perform(get("/api/residentes/pendientes").header("Authorization", contador)).andExpect(status().isForbidden());
        mvc.perform(get("/api/vehiculos").header("Authorization", contador)).andExpect(status().isForbidden());
        generar(propietario, 1000).andExpect(status().isForbidden());
        mvc.perform(get("/api/cuotas/apartamento/" + vecino.getId()).header("Authorization", propietario))
                .andExpect(status().isForbidden());
    }

    @Test
    void elResidenteSubeElComprobanteYElContadorLoConfirma() throws Exception {
        generar(admin, 200000);
        long id = cuota(apto);

        // Solo una foto o un PDF de verdad, y solo para su apartamento
        subir(id, propietario, new byte[]{'h', 'o', 'l', 'a'}).andExpect(status().isBadRequest());
        subir(id, otroPropietario, PNG).andExpect(status().isForbidden());
        subir(id, propietario, PNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.tieneComprobante").value(true))
                .andExpect(jsonPath("$.comprobanteTipo").value("image/png"));
        mvc.perform(get("/api/notificaciones").header("Authorization", contador))
                .andExpect(jsonPath("$[0].tipo").value("COMPROBANTE"))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("Torre 7 Apto 701")));

        // El enlace firmado descarga el archivo sin sesión; alterado, no
        String url = JsonPath.read(mvc.perform(get("/api/cuotas/" + id + "/comprobante").header("Authorization", contador))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.url");
        URI uri = URI.create(url);
        mvc.perform(get(uri.getPath() + "?" + uri.getRawQuery()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));
        mvc.perform(get(uri.getPath() + "?" + uri.getRawQuery().replaceAll("firma=[^&]+", "firma=falsa")))
                .andExpect(status().isNotFound());

        revisar(id, propietario, "{\"estado\":\"pagada\"}").andExpect(status().isForbidden());
        revisar(id, contador, "{\"estado\":\"pagada\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"));
        mvc.perform(get("/api/pagos/apartamento/" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$.saldoPendiente").value(0.0))
                .andExpect(jsonPath("$.estadoCuenta").value("Al Día"));
        mvc.perform(get("/api/notificaciones").header("Authorization", propietario))
                .andExpect(jsonPath("$[0].tipo").value("CUOTA_PAGADA"));
        subir(id, propietario, PNG).andExpect(status().isBadRequest());
    }

    @Test
    void elComprobanteRechazadoVuelveADeberYSePuedeSubirOtro() throws Exception {
        generar(admin, 200000);
        long id = cuota(apto);
        revisar(id, contador, "{\"estado\":\"rechazada\"}").andExpect(status().isBadRequest());
        subir(id, propietario, PNG);
        revisar(id, contador, "{\"estado\":\"rechazada\",\"motivo\":\"No se ve el valor\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("No se ve el valor"));
        mvc.perform(get("/api/notificaciones").header("Authorization", propietario))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("No se ve el valor")));
        mvc.perform(get("/api/cuotas?estado=rechazada").header("Authorization", contador))
                .andExpect(jsonPath("$.length()").value(1));
        subir(id, propietario, PNG).andExpect(jsonPath("$.estado").value("EN_REVISION"));
    }

    @Test
    void laCuotaVencidaPoneLaCarteraEnMora() throws Exception {
        String pasado = YearMonth.now(ZoneId.of("America/Bogota")).minusMonths(2).toString();
        enviar(post("/api/cuotas/generar"), admin, "{\"periodo\":\"" + pasado + "\",\"valor\":90000}")
                .andExpect(status().isOk());
        mvc.perform(get("/api/cuotas/apartamento/" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$[0].vencida").value(true))
                .andExpect(jsonPath("$[0].fechaLimite").value(YearMonth.parse(pasado).atDay(10).toString()));
        mvc.perform(get("/api/pagos/apartamento/" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$.estadoCuenta").value("En Mora"));
        enviar(post("/api/cuotas/generar"), admin, "{\"periodo\":\"2026-13\",\"valor\":90000}")
                .andExpect(status().isBadRequest());
        enviar(post("/api/cuotas/generar"), admin, "{\"periodo\":\"" + mes + "\",\"valor\":0}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void elAdministradorCreaLaCuentaDelContador() throws Exception {
        enviar(post("/api/administradores"), contador, "{\"usuario\":\"otro\",\"contrasena\":\"otro123\",\"rol\":\"contador\"}")
                .andExpect(status().isForbidden());
        int id = enviar(post("/api/administradores"), admin, "{\"usuario\":\"lucia\",\"contrasena\":\"lucia123\",\"rol\":\"contador\",\"nombreCompleto\":\"Lucía Rojas\",\"documento\":\"52111222\",\"telefono\":\"3001234567\",\"correo\":\"lucia@correo.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("CONTADOR"))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andReturn().getResponse().getContentAsString().transform(r -> JsonPath.read(r, "$.id"));
        enviar(post("/api/administradores"), admin, "{\"usuario\":\"lucia\",\"contrasena\":\"lucia123\",\"nombreCompleto\":\"Lucía Rojas\",\"documento\":\"52111222\",\"telefono\":\"3001234567\",\"correo\":\"lucia@correo.com\"}")
                .andExpect(status().isBadRequest());
        token("/api/auth/admin/login", "{\"usuario\":\"lucia\",\"contrasena\":\"lucia123\"}");

        long propio = adminRepo.findByUsuario("admin").orElseThrow().getId();
        mvc.perform(delete("/api/administradores/" + propio).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/administradores/" + id).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/administradores").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(2));
    }

    private long cuota(Apartamento a) {
        return cuotaRepo.findByApartamentoIdOrderByPeriodoDesc(a.getId()).get(0).getId();
    }

    private ResultActions generar(String auth, int valor) throws Exception {
        String limite = YearMonth.parse(mes).atEndOfMonth().toString();
        return enviar(post("/api/cuotas/generar"), auth,
                "{\"periodo\":\"" + mes + "\",\"valor\":" + valor + ",\"fechaLimite\":\"" + limite + "\"}");
    }

    private ResultActions subir(long id, String auth, byte[] datos) throws Exception {
        return mvc.perform(multipart("/api/cuotas/" + id + "/comprobante")
                .file(new MockMultipartFile("archivo", "pago.png", "image/png", datos))
                .header("Authorization", auth));
    }

    private ResultActions revisar(long id, String auth, String json) throws Exception {
        return enviar(put("/api/cuotas/" + id + "/estado"), auth, json);
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
