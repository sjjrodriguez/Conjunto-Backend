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

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P4 bloque 4: tipos de vehículo, cupos por tipo, aprobación del administrador y las
 * notificaciones que recibe el residente (vehículo y reserva revisados).
 */
@SpringBootTest
@AutoConfigureMockMvc
class VehiculosAprobacionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired CupoVehiculoRepository cupoRepo;
    @Autowired ZonaComunRepository zonaRepo;
    @Autowired ReservaRepository reservaRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PersonalObraRepository personalRepo;
    @Autowired ObraRepository obraRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    Apartamento vecino;
    String admin;
    String propietario;
    String otroPropietario;

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "9", "901", encoder.encode("clave901")));
        vecino = aptoRepo.save(new Apartamento(null, "9", "902", encoder.encode("clave902")));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        propietario = "Bearer " + token("/api/auth/apartamento/login",
                "{\"torre\":\"9\",\"apto\":\"901\",\"contrasena\":\"clave901\"}");
        otroPropietario = "Bearer " + token("/api/auth/apartamento/login",
                "{\"torre\":\"9\",\"apto\":\"902\",\"contrasena\":\"clave902\"}");
    }

    // La base H2 se comparte con los demás tests: no dejar filas que bloqueen sus borrados
    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
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
    void elVehiculoDelResidenteEsperaAprobacionYAlAprobarloSeLeAvisa() throws Exception {
        int id = registrar(propietario, carro("ABC123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString().transform(r -> JsonPath.read(r, "$.id"));

        // El administrador lo ve en su lista de pendientes, con su apartamento
        mvc.perform(get("/api/vehiculos").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].placa").value("ABC123"))
                .andExpect(jsonPath("$[0].torre").value("9"))
                .andExpect(jsonPath("$[0].apto").value("901"));

        mvc.perform(put("/api/vehiculos/" + id + "/estado").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"aprobado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));
        mvc.perform(get("/api/vehiculos").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(0));

        // En su lista aparece aprobado, con su apartamento
        mvc.perform(get("/api/vehiculos/apartamento/" + apto.getId()).header("Authorization", propietario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("APROBADO"))
                .andExpect(jsonPath("$[0].torre").value("9"));

        // El residente recibe su notificación; el vecino no la ve
        mvc.perform(get("/api/notificaciones").header("Authorization", propietario))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("VEHICULO_APROBADO"))
                .andExpect(jsonPath("$[0].referenciaId").value(id))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("ABC123")));
        mvc.perform(get("/api/notificaciones").header("Authorization", otroPropietario))
                .andExpect(jsonPath("$.length()").value(0));
        // Y en las del administrador solo está el aviso del vehículo nuevo
        mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("VEHICULO"));
    }

    @Test
    void alRechazarloSeGuardaElMotivoYDejaDeOcuparCupo() throws Exception {
        int id = registrar(propietario, carro("ABC123")).andReturn().getResponse().getContentAsString()
                .transform(r -> JsonPath.read(r, "$.id"));
        mvc.perform(put("/api/vehiculos/" + id + "/estado").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"RECHAZADO\",\"motivo\":\"La placa no coincide con la tarjeta\"}"))
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.motivoRechazo").value("La placa no coincide con la tarjeta"));
        mvc.perform(get("/api/notificaciones").header("Authorization", propietario))
                .andExpect(jsonPath("$[0].tipo").value("VEHICULO_RECHAZADO"))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("La placa no coincide")));

        // El rechazado no cuenta: puede registrar otro carro
        registrar(propietario, carro("DEF456")).andExpect(status().isOk());
    }

    @Test
    void respetaElCupoPorTipoYLaPlacaSoloSeExigeACarrosYMotos() throws Exception {
        registrar(propietario, carro("ABC123")).andExpect(status().isOk());
        registrar(propietario, carro("XYZ999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("máximo de vehículos de tipo carro (1)")));

        // Bicicletas: sin placa y hasta 2 por defecto
        registrar(propietario, sinPlaca("Bicicleta")).andExpect(status().isOk())
                .andExpect(jsonPath("$.placa").doesNotExist());
        registrar(propietario, sinPlaca("bicicleta")).andExpect(status().isOk());
        registrar(propietario, sinPlaca("Bicicleta")).andExpect(status().isBadRequest());
        registrar(propietario, sinPlaca("Patineta electrica")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Patineta eléctrica"));

        registrar(propietario, sinPlaca("Moto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La placa es obligatoria para moto"));

        // El administrador registra directo y sin límite
        registrar(admin, carro("ADM111")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    @Test
    void elAdministradorCambiaLosCupos() throws Exception {
        mvc.perform(get("/api/vehiculos/cupos").header("Authorization", propietario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].tipo").value("Carro"))
                .andExpect(jsonPath("$[0].maximo").value(1))
                .andExpect(jsonPath("$[0].requierePlaca").value(true))
                .andExpect(jsonPath("$[2].tipo").value("Bicicleta"))
                .andExpect(jsonPath("$[2].requierePlaca").value(false));

        String cupos = "[{\"tipo\":\"Bicicleta\",\"maximo\":0},{\"tipo\":\"Carro\",\"maximo\":2}]";
        mvc.perform(put("/api/vehiculos/cupos").header("Authorization", propietario)
                        .contentType(MediaType.APPLICATION_JSON).content(cupos))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/vehiculos/cupos").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(cupos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maximo").value(2))
                .andExpect(jsonPath("$[2].maximo").value(0));

        registrar(propietario, sinPlaca("Bicicleta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El conjunto no permite registrar vehículos de tipo bicicleta"));
        registrar(propietario, carro("ABC123")).andExpect(status().isOk());
        registrar(propietario, carro("DEF456")).andExpect(status().isOk());

        mvc.perform(put("/api/vehiculos/cupos").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"tipo\":\"Moto\",\"maximo\":-1}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloElAdministradorApruebaYVeLosPendientes() throws Exception {
        int id = registrar(propietario, carro("ABC123")).andReturn().getResponse().getContentAsString()
                .transform(r -> JsonPath.read(r, "$.id"));
        mvc.perform(get("/api/vehiculos").header("Authorization", propietario))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/vehiculos/" + id + "/estado").header("Authorization", propietario)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"APROBADO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/vehiculos/" + id + "/estado").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PENDIENTE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elResidenteNoTocaLasNotificacionesDeOtros() throws Exception {
        registrar(propietario, carro("ABC123"));
        int delAdmin = mvc.perform(get("/api/notificaciones").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString().transform(r -> JsonPath.read(r, "$[0].id"));
        mvc.perform(put("/api/notificaciones/" + delAdmin + "/leida").header("Authorization", propietario))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/notificaciones/" + delAdmin).header("Authorization", propietario))
                .andExpect(status().isNotFound());
    }

    @Test
    void alAprobarUnaReservaSeLeAvisaAlResidente() throws Exception {
        ZonaComun salon = zonaRepo.save(new ZonaComun(null, "Salón", "Bueno", null, true));
        LocalDate dia = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(4);
        int reserva = registrarEn(post("/api/reservas"), propietario, "{\"fecha\":\"" + dia + "\",\"hora\":\"15:00\","
                + "\"zonaComunId\":" + salon.getId() + ",\"apartamentoId\":" + apto.getId() + "}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                .transform(r -> JsonPath.read(r, "$.id"));
        registrarEn(put("/api/reservas/" + reserva + "/estado"), admin, "{\"estado\":\"APROBADA\"}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/notificaciones").header("Authorization", propietario))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("RESERVA_APROBADA"))
                .andExpect(jsonPath("$[0].referenciaId").value(reserva))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("Salón")));
    }

    private String carro(String placa) {
        return "{\"placa\":\"" + placa + "\",\"marca\":\"Mazda\",\"tipo\":\"Carro\",\"apartamentoId\":" + apto.getId() + "}";
    }

    private String sinPlaca(String tipo) {
        return "{\"marca\":\"GW\",\"tipo\":\"" + tipo + "\",\"apartamentoId\":" + apto.getId() + "}";
    }

    private ResultActions registrar(String auth, String json) throws Exception {
        return registrarEn(post("/api/vehiculos"), auth, json);
    }

    private ResultActions registrarEn(MockHttpServletRequestBuilder req, String auth, String json) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(String url, String json) throws Exception {
        String respuesta = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
