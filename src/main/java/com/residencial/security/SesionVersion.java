package com.residencial.security;

import com.residencial.repository.AdministradorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ResidenteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Versión de sesión de cada cuenta (P6). El token lleva la versión con la que se emitió; al
 * cerrar sesión la de la cuenta sube y los tokens anteriores dejan de valer. La versión vigente
 * se guarda 30 segundos en memoria para no consultar la base en cada petición.
 *
 * El sujeto del token dice de quién es: "admin:ID", "residente:ID" o "apartamento:ID". La clave
 * del apartamento la comparten todos los que viven ahí, así que cerrar esa sesión no invalida a
 * los demás: solo suben las versiones de administradores y residentes con cuenta propia.
 */
@Component
@RequiredArgsConstructor
public class SesionVersion {

    private static final long VIGENCIA_MS = 30_000;
    /** Cuenta que no existe o dada de baja: ningún token suyo vale. */
    public static final int INVALIDA = Integer.MAX_VALUE;

    private record Entrada(int version, long vence) {}

    private final AdministradorRepository admins;
    private final ResidenteRepository residentes;
    private final ApartamentoRepository apartamentos;
    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<String, Entrada> cache = new ConcurrentHashMap<>();

    /** La versión que deben tener los tokens de [sujeto]; [INVALIDA] si la cuenta ya no puede entrar. */
    public int vigente(String sujeto) {
        if (sujeto == null) return INVALIDA;
        long ahora = System.currentTimeMillis();
        Entrada e = cache.get(sujeto);
        if (e != null && e.vence() > ahora) return e.version();
        int v = consultar(sujeto);
        cache.put(sujeto, new Entrada(v, ahora + VIGENCIA_MS));
        return v;
    }

    private int consultar(String sujeto) {
        Long id = id(sujeto);
        if (id == null) return 0; // sujeto de otro tipo: no se controla
        // Consulta directa de una columna: se hace en cada petición nueva y no debe cargar entidades
        String sql;
        if (sujeto.startsWith("admin:")) sql = "select token_version from administrador where id = ? and activo = true";
        else if (sujeto.startsWith("residente:")) sql = "select token_version from residente where id = ?";
        else sql = "select token_version from apartamento where id = ?";
        return jdbc.query(sql, rs -> rs.next() ? rs.getInt(1) : INVALIDA, id);
    }

    /** Cierra la sesión de [sujeto]: sus tokens anteriores dejan de valer. */
    @Transactional
    public void cerrar(String sujeto) {
        Long id = id(sujeto);
        if (id == null) return;
        if (sujeto.startsWith("admin:")) {
            admins.findById(id).ifPresent(a -> { a.setTokenVersion(a.getTokenVersion() + 1); admins.save(a); });
        } else if (sujeto.startsWith("residente:")) {
            residentes.findById(id).ifPresent(r -> { r.setTokenVersion(r.getTokenVersion() + 1); residentes.save(r); });
        }
        cache.remove(sujeto);
    }

    /** Invalida los tokens de una cuenta aunque no cierre sesión (baja o cambio de contraseña). */
    @Transactional
    public void revocar(String sujeto) {
        Long id = id(sujeto);
        if (id == null) return;
        if (sujeto.startsWith("apartamento:")) {
            apartamentos.findById(id).ifPresent(a -> { a.setTokenVersion(a.getTokenVersion() + 1); apartamentos.save(a); });
            cache.remove(sujeto);
        } else {
            cerrar(sujeto);
        }
    }

    private static Long id(String sujeto) {
        if (sujeto == null) return null;
        int i = sujeto.indexOf(':');
        if (i < 0) return null;
        try {
            return Long.valueOf(sujeto.substring(i + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
