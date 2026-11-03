package com.residencial.conjunto;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.LongPredicate;

/**
 * P7: fija el conjunto de la petición. Con sesión manda el del token (la cabecera se ignora: nadie
 * puede pasarse a otro conjunto con su token); sin sesión (login, registro) el de la cabecera
 * X-Conjunto. Sin ninguno de los dos, el conjunto 1, como la app anterior. Va después de validar el token.
 *
 * Bloque 3: un conjunto suspendido no entra. Se rechazan sus sesiones y sus logins (/api/auth); lo
 * público (elegir conjunto, textos legales, el webhook de pagos) sigue respondiendo. La plataforma
 * no es de ningún conjunto y no se revisa.
 */
public class FiltroConjunto extends OncePerRequestFilter {

    public static final String CABECERA = "X-Conjunto";
    public static final String CLAIM = "cid";
    private static final String CLAIM_ROL = "rol";
    private static final String ROL_PLATAFORMA = "PLATAFORMA";
    private static final String SUSPENDIDO =
            "{\"mensaje\":\"Este conjunto está suspendido. Comunícate con la administración de la plataforma\"}";

    private final LongPredicate activo;

    public FiltroConjunto(LongPredicate activo) {
        this.activo = activo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadena)
            throws ServletException, IOException {
        Long id;
        try {
            id = conjunto(req);
        } catch (NumberFormatException e) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Conjunto inválido");
            return;
        }
        if (revisar(req) && !activo.test(id)) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getOutputStream().write(SUSPENDIDO.getBytes(StandardCharsets.UTF_8));
            return;
        }
        Long antes = ConjuntoContexto.fijado();
        ConjuntoContexto.fijar(id);
        try {
            cadena.doFilter(req, res);
        } finally {
            ConjuntoContexto.fijar(antes);
        }
    }

    /** Con sesión, todo menos la plataforma; sin sesión, solo login y registro. */
    private static boolean revisar(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return !ROL_PLATAFORMA.equals(jwt.getToken().getClaimAsString(CLAIM_ROL));
        }
        return req.getRequestURI().startsWith("/api/auth/");
    }

    private static Long conjunto(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            Object cid = jwt.getToken().getClaim(CLAIM);
            return cid instanceof Number n ? n.longValue() : ConjuntoContexto.PREDETERMINADO;
        }
        String cabecera = req.getHeader(CABECERA);
        if (cabecera == null || cabecera.isBlank()) return ConjuntoContexto.PREDETERMINADO;
        long id = Long.parseLong(cabecera.trim());
        if (id <= 0) throw new NumberFormatException();
        return id;
    }
}
