package com.residencial.conjunto;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * P7 bloque 3: si un conjunto está activo. FiltroConjunto lo pregunta en cada petición, así que se
 * guarda 30 segundos en memoria (como SesionVersion); la plataforma lo olvida al suspender o
 * reactivar. Un conjunto que no está en la tabla cuenta como activo: el 1 de una base sin V20.
 */
@Component
public class ConjuntosActivos {

    private static final long VIGENCIA_MS = 30_000;

    private record Entrada(boolean activo, long vence) {}

    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<Long, Entrada> cache = new ConcurrentHashMap<>();

    public ConjuntosActivos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean activo(long id) {
        long ahora = System.currentTimeMillis();
        Entrada e = cache.get(id);
        if (e != null && e.vence() > ahora) return e.activo();
        boolean activo = jdbc.query("select activo from conjunto where id = ?", rs -> !rs.next() || rs.getBoolean(1), id);
        cache.put(id, new Entrada(activo, ahora + VIGENCIA_MS));
        return activo;
    }

    public void olvidar(long id) {
        cache.remove(id);
    }
}
