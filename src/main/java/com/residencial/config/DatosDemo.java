package com.residencial.config;

import com.residencial.model.*;
import com.residencial.repository.*;
import com.residencial.service.CuotaService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos de ejemplo para el perfil "demo" (H2 en memoria). Solo existe con
 * --spring.profiles.active=demo; en producción esta clase no se carga.
 *
 * Credenciales de prueba (no son de ningún usuario real):
 *   Administrador:  usuario "admin", contraseña "admin-demo"
 *   Apartamentos:   torre 1 aptos 101, 102, 201 / torre 2 aptos 101, 102, 201,
 *                   contraseña "demo1234" en todos
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DatosDemo implements CommandLineRunner {

    private static void marcarPagada(Cuota c) {
        c.setEstado(Cuota.PAGADA);
        c.setPagadaEn(c.getFechaLimite().atTime(9, 0).minusDays(2));
    }

    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);
    private static final String CLAVE_APARTAMENTOS = "demo1234";

    private final AdministradorRepository adminRepo;
    private final ApartamentoRepository aptoRepo;
    private final ResidenteRepository residenteRepo;
    private final VehiculoRepository vehiculoRepo;
    private final PagoRepository pagoRepo;
    private final ObraRepository obraRepo;
    private final PersonalObraRepository personalRepo;
    private final ZonaComunRepository zonaRepo;
    private final ReservaRepository reservaRepo;
    private final NotificacionRepository notificacionRepo;
    private final PasswordEncoder encoder;
    private final CuotaRepository cuotaRepo;
    private final CuotaService cuotaService;

    @Override
    public void run(String... args) {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        adminRepo.save(new Administrador(null, "admin", encoder.encode("admin-demo")));
        adminRepo.save(new Administrador(null, "contador", encoder.encode("contador-demo"), Administrador.CONTADOR));

        String clave = encoder.encode(CLAVE_APARTAMENTOS);
        List<Apartamento> aptos = new ArrayList<>();
        for (String torre : List.of("1", "2")) {
            for (String apto : List.of("101", "102", "201")) {
                aptos.add(aptoRepo.save(new Apartamento(null, torre, apto, clave)));
            }
        }
        Apartamento t1a101 = aptos.get(0), t1a102 = aptos.get(1), t1a201 = aptos.get(2),
                t2a101 = aptos.get(3), t2a102 = aptos.get(4);

        // Laura y Camila entran también con su documento y la clave demo; Mateo espera aprobación
        residenteRepo.saveAll(List.of(
                new Residente(null, "Laura Martínez Gómez", "3001234567", "1010101010", "PROPIETARIO", t1a101, clave, Residente.APROBADO),
                new Residente(null, "Andrés Martínez Gómez", "3007654321", "1020202020", "PROPIETARIO", t1a101),
                new Residente(null, "Camila Rojas Pérez", "3109876543", "1030303030", "ARRENDATARIO", t1a102, clave, Residente.APROBADO),
                new Residente(null, "Mateo Ruiz Salas", "3115556677", "1060606060", "ARRENDATARIO", t1a101, clave, Residente.PENDIENTE),
                new Residente(null, "Jorge Castro Díaz", "3204567890", "1040404040", "PROPIETARIO", t1a201),
                new Residente(null, "Paula Herrera León", "3152223344", "1050505050", "PROPIETARIO", t2a101)));

        vehiculoRepo.saveAll(List.of(
                new Vehiculo(null, "ABC123", "Mazda", "Rojo", "Carro", t1a101),
                new Vehiculo(null, "XYZ98F", "Yamaha", "Negro", "Moto", t1a101),
                new Vehiculo(null, "JKL456", "Renault", "Gris", "Carro", t1a102)));
        Vehiculo chevrolet = vehiculoRepo.save(new Vehiculo(null, "MNO789", "Chevrolet", "Blanco", "Carro", t2a101));
        // Uno esperando aprobación del administrador y una bicicleta (sin placa)
        vehiculoRepo.saveAll(List.of(
                new Vehiculo(null, "QRS321", "Kia", "Azul", "Carro", t1a201, Vehiculo.PENDIENTE, null),
                new Vehiculo(null, null, "GW", "Negra", "Bicicleta", t1a101)));

        // Cuotas del mes pasado y de este: al día, en mora y pendiente sin vencer.
        // La cartera (tabla pago) sale de ellas
        YearMonth mes = YearMonth.from(hoy);
        BigDecimal valor = new BigDecimal("175000");
        for (Apartamento a : aptos) {
            Cuota anterior = new Cuota(a, mes.minusMonths(1).toString(), valor, mes.minusMonths(1).atDay(10));
            Cuota actual = new Cuota(a, mes.toString(), valor, mes.atEndOfMonth());
            if (a != t1a102) marcarPagada(anterior);
            if (a == t1a101 || a == t2a101) marcarPagada(actual);
            cuotaRepo.saveAll(List.of(anterior, actual));
            cuotaService.recalcularCartera(a.getId());
        }

        Obra cocina = obraRepo.save(new Obra(null, "Remodelación de cocina: cambio de mesón y enchape",
                hoy.minusDays(5), hoy.plusDays(10), "EN_PROCESO", t1a101));
        Obra pintura = obraRepo.save(new Obra(null, "Pintura de alcobas y sala",
                hoy.minusDays(20), hoy.minusDays(12), "FINALIZADA", t1a102));
        Obra bano = obraRepo.save(new Obra(null, "Cambio de tubería del baño principal",
                hoy.minusDays(1), hoy.plusDays(4), "EN_PROCESO", t2a102));
        personalRepo.saveAll(List.of(
                new PersonalObra(null, "Carlos Pardo", "79111222", true, cocina),
                new PersonalObra(null, "Luis Gómez", "80333444", false, cocina),
                new PersonalObra(null, "Miguel Ángel Ruiz", "1012555666", true, pintura),
                new PersonalObra(null, "Óscar Beltrán", "79777888", true, bano)));

        // Aforo y horario en que se pueden reservar
        ZonaComun bbq = zonaRepo.save(new ZonaComun(null, "Zona BBQ", "Bueno", "Parrilla y 4 mesas", true,
                20, LocalTime.of(9, 0), LocalTime.of(22, 0)));
        ZonaComun salon = zonaRepo.save(new ZonaComun(null, "Salón comunal", "Bueno", "Mesas, sillas y sonido", true,
                40, LocalTime.of(8, 0), LocalTime.of(23, 0)));
        zonaRepo.save(new ZonaComun(null, "Gimnasio", "Regular", "Una caminadora en reparación", true,
                8, LocalTime.of(5, 0), LocalTime.of(21, 0)));
        zonaRepo.save(new ZonaComun(null, "Piscina", "En mantenimiento", "Cerrada hasta nuevo aviso", false));

        Reserva bbqPendiente = reservaRepo.save(
                new Reserva(null, hoy.plusDays(2), LocalTime.of(14, 0), "PENDIENTE", bbq, t1a101,
                        LocalTime.of(17, 0), 12));
        reservaRepo.saveAll(List.of(
                new Reserva(null, hoy.plusDays(3), LocalTime.of(18, 0), "APROBADA", salon, t1a102,
                        LocalTime.of(22, 0), 30),
                new Reserva(null, hoy.plusDays(5), LocalTime.of(10, 0), "PENDIENTE", salon, t2a101,
                        LocalTime.of(13, 0), 15),
                new Reserva(null, hoy.plusDays(2), LocalTime.of(9, 0), "APROBADA", bbq, t2a102,
                        LocalTime.of(12, 0), 8),
                new Reserva(null, hoy.minusDays(4), LocalTime.of(16, 0), "RECHAZADA", bbq, t1a201)));

        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Bogota"));
        notificacionRepo.saveAll(List.of(
                new Notificacion(null, "RESERVA", "Nueva reserva — Zona BBQ el "
                        + hoy.plusDays(2).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + " a las 14:00 · Torre 1 Apto 101", ahora.minusHours(3), false,
                        bbqPendiente.getId(), t1a101.getId()),
                new Notificacion(null, "VEHICULO", "Nuevo vehículo registrado — MNO789 (Carro Chevrolet Blanco)"
                        + " · Torre 2 Apto 101", ahora.minusDays(1), true,
                        chevrolet.getId(), t2a101.getId())));

        log.info("Demo: contador / contador-demo entra por Administración (solo cartera y facturación)");
        log.warn("PERFIL DEMO activo: base H2 en memoria con datos de ejemplo. No usar en producción.");
    }
}
