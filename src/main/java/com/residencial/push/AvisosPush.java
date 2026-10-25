package com.residencial.push;

import com.residencial.model.Dispositivo;
import com.residencial.model.Notificacion;
import com.residencial.repository.DispositivoRepository;
import com.residencial.security.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cuando se guarda una notificación, la envía a los celulares de quien la recibe: los del
 * administrador o los de los residentes del apartamento. Espera a que la transacción termine
 * bien (si la reserva falla, no hay aviso) y corre en segundo plano.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvisosPush {

    /** Se publica al guardar una notificación (NotificacionServiceImpl). */
    public record NotificacionCreada(Notificacion notificacion) {}

    // Título del aviso en el celular según el tipo de notificación
    private static final Map<String, String> TITULOS = Map.ofEntries(
            Map.entry("RESERVA", "Nueva reserva"),
            Map.entry("VEHICULO", "Nuevo vehículo por aprobar"),
            Map.entry("VEHICULO_APROBADO", "Vehículo aprobado"),
            Map.entry("VEHICULO_RECHAZADO", "Vehículo rechazado"),
            Map.entry("RESERVA_APROBADA", "Reserva aprobada"),
            Map.entry("RESERVA_RECHAZADA", "Reserva rechazada"),
            Map.entry("RESIDENTE", "Nuevo arrendatario por aprobar"),
            Map.entry("RESIDENTE_APROBADO", "Arrendatario aprobado"),
            Map.entry("RESIDENTE_RECHAZADO", "Arrendatario rechazado"),
            Map.entry("CUOTA", "Nueva cuota de administración"),
            Map.entry("COMPROBANTE", "Comprobante por revisar"),
            Map.entry("CUOTA_PAGADA", "Pago confirmado"),
            Map.entry("CUOTA_RECHAZADA", "Comprobante rechazado"));

    private final DispositivoRepository dispositivos;
    private final EnviadorPush enviador;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void alCrear(NotificacionCreada evento) {
        Long destinatario = evento.notificacion().getDestinatarioApartamentoId();
        List<Dispositivo> celulares = destinatario == null
                // Las de la administración llegan al administrador y al contador
                ? dispositivos.findByRolIn(List.of(TokenService.ROL_ADMIN, TokenService.ROL_CONTADOR))
                : dispositivos.findByApartamentoId(destinatario);
        List<String> tokens = celulares.stream().map(Dispositivo::getToken).toList();
        if (tokens.isEmpty()) return;
        Set<String> invalidos = enviador.enviar(tokens, datos(evento.notificacion()));
        if (!invalidos.isEmpty()) {
            dispositivos.borrarTokens(invalidos);
            log.info("Se borraron {} tokens de celulares que ya no reciben avisos", invalidos.size());
        }
    }

    /** Lo que recibe la app: texto del aviso y a qué pantalla llevar al tocarlo. */
    static Map<String, String> datos(Notificacion n) {
        Map<String, String> datos = new HashMap<>();
        datos.put("notificacionId", String.valueOf(n.getId()));
        datos.put("tipo", n.getTipo());
        datos.put("titulo", TITULOS.getOrDefault(n.getTipo(), "MiConjunto"));
        datos.put("mensaje", n.getMensaje());
        if (n.getReferenciaId() != null) datos.put("referenciaId", String.valueOf(n.getReferenciaId()));
        if (n.getApartamentoId() != null) datos.put("apartamentoId", String.valueOf(n.getApartamentoId()));
        return datos;
    }
}
