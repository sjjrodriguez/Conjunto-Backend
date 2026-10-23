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
 * Cuando se guarda una notificación para el administrador, la envía a sus celulares.
 * Espera a que la transacción termine bien (si la reserva falla, no hay aviso) y corre en
 * segundo plano.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvisosPush {

    /** Se publica al guardar una notificación (NotificacionServiceImpl). */
    public record NotificacionCreada(Notificacion notificacion) {}

    private final DispositivoRepository dispositivos;
    private final EnviadorPush enviador;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void alCrear(NotificacionCreada evento) {
        List<String> tokens = dispositivos.findByRol(TokenService.ROL_ADMIN).stream()
                .map(Dispositivo::getToken).toList();
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
        datos.put("titulo", "VEHICULO".equals(n.getTipo()) ? "Nuevo vehículo" : "Nueva reserva");
        datos.put("mensaje", n.getMensaje());
        if (n.getReferenciaId() != null) datos.put("referenciaId", String.valueOf(n.getReferenciaId()));
        if (n.getApartamentoId() != null) datos.put("apartamentoId", String.valueOf(n.getApartamentoId()));
        return datos;
    }
}
