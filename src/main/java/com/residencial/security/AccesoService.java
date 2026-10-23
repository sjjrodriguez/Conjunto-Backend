package com.residencial.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Comprueba que el usuario autenticado puede actuar sobre un apartamento:
 * el admin sobre cualquiera, el propietario solo sobre el suyo.
 * Las reglas por ruta están en SecurityConfig; esto cubre lo que depende del dato.
 */
@Component
public class AccesoService {

    private final boolean enforce;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    public AccesoService(@Value("${app.security.enforce:false}") boolean enforce) {
        this.enforce = enforce;
    }

    public void verificarApartamento(Long apartamentoId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || trustResolver.isAnonymous(auth)) {
            // Modo transición: la app antigua aún no envía token
            if (enforce) throw new AccessDeniedException("Debes iniciar sesión");
            return;
        }
        if (esAdmin(auth)) return;
        if (auth instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getClaim(TokenService.CLAIM_APARTAMENTO) instanceof Number propio
                && apartamentoId != null
                && propio.longValue() == apartamentoId) {
            return;
        }
        throw new AccessDeniedException("No tienes permiso sobre este apartamento");
    }

    /** true si la petición actual viene de un administrador con token válido. */
    public boolean esAdminActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && !trustResolver.isAnonymous(auth) && esAdmin(auth);
    }

    /** El token de la sesión actual; vacío si la petición no trae uno válido. */
    public Optional<Jwt> tokenActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt ? Optional.of(jwt.getToken()) : Optional.empty();
    }

    private static boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + TokenService.ROL_ADMIN).equals(a.getAuthority()));
    }
}
