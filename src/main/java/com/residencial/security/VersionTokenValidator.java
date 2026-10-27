package com.residencial.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Rechaza los tokens de una sesión cerrada o de una cuenta dada de baja. */
public class VersionTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error SESION_CERRADA =
            new OAuth2Error("invalid_token", "La sesión ya no es válida", null);

    private final SesionVersion sesiones;

    public VersionTokenValidator(SesionVersion sesiones) {
        this.sesiones = sesiones;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Number emitida = jwt.getClaim(TokenService.CLAIM_VERSION);
        int version = emitida == null ? 0 : emitida.intValue();
        return version >= sesiones.vigente(jwt.getSubject())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(SESION_CERRADA);
    }
}
