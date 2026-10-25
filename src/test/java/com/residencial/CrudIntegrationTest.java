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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** P4 bloque 8: editar y eliminar lo que antes solo se podía crear. */
@SpringBootTest
@AutoConfigureMockMvc
class CrudIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdministradorRepository adminRepo;
    @Autowired ApartamentoRepository aptoRepo;
    @Autowired VehiculoRepository vehiculoRepo;
    @Autowired CupoVehiculoRepository cupoRepo;
    @Autowired ObraRepository obraRepo;
    @Autowired PersonalObraRepository personalRepo;
    @Autowired ZonaComunRepository zonaRepo;
    @Autowired ReservaRepository reservaRepo;
    @Autowired CuotaRepository cuotaRepo;
    @Autowired PagoRepository pagoRepo;
    @Autowired ResidenteRepository residenteRepo;
    @Autowired NotificacionRepository notificacionRepo;
    @Autowired DispositivoRepository dispositivoRepo;
    @Autowired PasswordEncoder encoder;

    Apartamento apto;
    Apartamento vecino;
    String admin;
    String propietario;
    String otroPropietario;
    LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

    @BeforeEach
    void datos() throws Exception {
        limpiar();
        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin123")));
        apto = aptoRepo.save(new Apartamento(null, "6", "601", encoder.encode("clave601")));
        vecino = aptoRepo.save(new Apartamento(null, "6", "602", encoder.encode("clave602")));
        admin = "Bearer " + token("/api/auth/admin/login", "{\"usuario\":\"admin\",\"contrasena\":\"admin123\"}");
        propietario = "Bearer " + token("/api/auth/apartamento/login",
                "{\"torre\":\"6\",\"apto\":\"601\",\"contrasena\":\"clave601\"}");
        otroPropietario = "Bearer " + token("/api/auth/apartamento/login",
                "{\"torre\":\"6\",\"apto\":\"602\",\"contrasena\":\"clave602\"}");
    }

    @AfterEach
    void limpiar() {
        notificacionRepo.deleteAll();
        dispositivoRepo.deleteAll();
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
    void elResidenteEditaSuVehiculoYVuelveAEsperarAprobacion() throws Exception {
        Vehiculo v = vehiculoRepo.save(new Vehiculo(null, "ABC123", "Mazda", "Rojo", "Carro", apto, Vehiculo.APROBADO, null));
        enviar(put("/api/vehiculos/" + v.getId()), propietario, "{\"tipo\":\"carro\",\"placa\":\"xyz987\",\"marca\":\"Kia\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placa").value("XYZ987"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
        enviar(put("/api/vehiculos/" + v.getId()), propietario, "{\"tipo\":\"moto\"}").andExpect(status().isBadRequest());
        enviar(put("/api/vehiculos/" + v.getId()), otroPropietario, "{\"tipo\":\"carro\",\"placa\":\"AAA111\"}")
                .andExpect(status().isForbidden());
        // El administrador corrige sin cambiar el estado
        enviar(put("/api/vehiculos/" + v.getId()), admin, "{\"tipo\":\"carro\",\"placa\":\"XYZ987\",\"color\":\"Azul\"}")
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.color").value("Azul"));
    }

    @Test
    void elResidenteEditaYQuitaElPersonalDeSuObra() throws Exception {
        Obra obra = obraRepo.save(new Obra(null, "Cocina", hoy, hoy.plusDays(5), "EN_PROCESO", apto));
        PersonalObra p = personalRepo.save(new PersonalObra(null, "Pedro", "111", true, obra));
        enviar(put("/api/personal-obra/" + p.getId()), propietario,
                "{\"nombreTrabajador\":\"Pedro Gómez\",\"cedula\":\"111\",\"arlEstado\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreTrabajador").value("Pedro Gómez"))
                .andExpect(jsonPath("$.torrePiso").value("6"));
        mvc.perform(delete("/api/personal-obra/" + p.getId()).header("Authorization", otroPropietario))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/personal-obra/" + p.getId()).header("Authorization", propietario))
                .andExpect(status().isNoContent());
    }

    @Test
    void elResidenteCancelaSuReservaSiNoHaEmpezado() throws Exception {
        ZonaComun zona = zonaRepo.save(new ZonaComun(null, "BBQ", "Bueno", null, true));
        Reserva futura = reservaRepo.save(new Reserva(null, hoy.plusDays(3), LocalTime.of(10, 0), "APROBADA", zona, apto, null, null));
        Reserva pasada = reservaRepo.save(new Reserva(null, hoy.minusDays(3), LocalTime.of(10, 0), "APROBADA", zona, apto, null, null));
        mvc.perform(delete("/api/reservas/" + futura.getId()).header("Authorization", otroPropietario))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/reservas/" + pasada.getId()).header("Authorization", propietario))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/reservas/" + futura.getId()).header("Authorization", propietario))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/reservas/" + pasada.getId()).header("Authorization", admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void lasCuotasSinPagarSeCorrigenOEliminan() throws Exception {
        String mes = YearMonth.from(hoy).toString();
        Cuota c = cuotaRepo.save(new Cuota(apto, mes, new BigDecimal("100000"), YearMonth.from(hoy).atEndOfMonth()));
        enviar(put("/api/cuotas/" + c.getId()), admin, "{\"valor\":120000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(120000));
        mvc.perform(get("/api/pagos/apartamento/" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$.saldoPendiente").value(120000.0));
        enviar(put("/api/cuotas/" + c.getId()), propietario, "{\"valor\":1}").andExpect(status().isForbidden());

        mvc.perform(delete("/api/cuotas/" + c.getId()).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/pagos/apartamento/" + apto.getId()).header("Authorization", propietario))
                .andExpect(jsonPath("$.saldoPendiente").value(0.0));

        Cuota pagada = new Cuota(apto, mes, new BigDecimal("100000"), hoy);
        pagada.setEstado(Cuota.PAGADA);
        long id = cuotaRepo.save(pagada).getId();
        mvc.perform(delete("/api/cuotas/" + id).header("Authorization", admin)).andExpect(status().isBadRequest());
    }

    @Test
    void elApartamentoSoloSeEliminaVacio() throws Exception {
        residenteRepo.save(new Residente(null, "Ana", null, "1", "PROPIETARIO", vecino));
        mvc.perform(delete("/api/apartamentos/" + vecino.getId()).header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("residentes")));
        residenteRepo.deleteAll();
        cuotaRepo.save(new Cuota(vecino, YearMonth.from(hoy).toString(), new BigDecimal("1000"), hoy));
        mvc.perform(delete("/api/apartamentos/" + vecino.getId()).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/apartamentos/" + vecino.getId()).header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void elAdministradorCambiaElRolYLaContrasenaDeUnaCuenta() throws Exception {
        Administrador conta = adminRepo.save(new Administrador(null, "conta", encoder.encode("conta123"), Administrador.CONTADOR));
        enviar(put("/api/administradores/" + conta.getId()), admin, "{\"usuario\":\"conta\",\"rol\":\"admin\",\"contrasena\":\"nueva123\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"));
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"conta\",\"contrasena\":\"nueva123\"}"))
                .andExpect(jsonPath("$.rol").value("ADMIN"));
        long propio = adminRepo.findByUsuario("admin").orElseThrow().getId();
        enviar(put("/api/administradores/" + propio), admin, "{\"usuario\":\"admin\",\"rol\":\"contador\"}")
                .andExpect(status().isBadRequest());
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
