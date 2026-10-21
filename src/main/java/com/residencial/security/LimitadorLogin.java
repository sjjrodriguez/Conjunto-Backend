package com.residencial.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los intentos fallidos de login por IP y usuario dentro de una ventana
 * de tiempo. Vive en memoria: con una sola instancia en Render es suficiente.
 */
@Component
public class LimitadorLogin {

    private static final int MAX_CLAVES = 10_000;

    private final int maxIntentos;
    private final long ventanaMs;
    private final Map<String, Deque<Long>> fallos = new ConcurrentHashMap<>();

    public LimitadorLogin(@Value("${app.login.max-intentos:5}") int maxIntentos,
                          @Value("${app.login.ventana-segundos:60}") long ventanaSegundos) {
        this.maxIntentos = maxIntentos;
        this.ventanaMs = ventanaSegundos * 1000;
    }

    /** Lanza 429 si la clave ya agotó sus intentos en la ventana actual. */
    public void verificar(String clave) {
        Deque<Long> intentos = fallos.get(clave);
        if (intentos == null) return;
        synchronized (intentos) {
            purgar(intentos, System.currentTimeMillis());
            if (intentos.size() >= maxIntentos) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Demasiados intentos fallidos. Espera un minuto e inténtalo de nuevo");
            }
        }
    }

    public void registrarFallo(String clave) {
        long ahora = System.currentTimeMillis();
        if (fallos.size() > MAX_CLAVES) limpiarCaducadas(ahora);
        Deque<Long> intentos = fallos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (intentos) {
            purgar(intentos, ahora);
            intentos.addLast(ahora);
        }
    }

    public void limpiar(String clave) {
        fallos.remove(clave);
    }

    private void purgar(Deque<Long> intentos, long ahora) {
        while (!intentos.isEmpty() && ahora - intentos.peekFirst() > ventanaMs) {
            intentos.pollFirst();
        }
    }

    private void limpiarCaducadas(long ahora) {
        fallos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                purgar(e.getValue(), ahora);
                return e.getValue().isEmpty();
            }
        });
    }
}
