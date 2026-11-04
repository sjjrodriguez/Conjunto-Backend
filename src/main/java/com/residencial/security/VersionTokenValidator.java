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
    private final SesionesDispositivo dispositivos;

    public VersionTokenValidator(SesionVersion sesiones, SesionesDispositivo dispositivos) {
        this.sesiones = sesiones;
        this.dispositivos = dispositivos;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Number emitida = jwt.getClaim(TokenService.CLAIM_VERSION);
        int version = emitida == null ? 0 : emitida.intValue();
        if (version < sesiones.vigente(jwt.getSubject())) return OAuth2TokenValidatorResult.failure(SESION_CERRADA);
        // P7 bloque 5: la sesión de ese celular (los tokens sin sid son de la app anterior y siguen valiendo)
        String sesion = jwt.getClaimAsString(TokenService.CLAIM_SESION);
        return sesion == null || dispositivos.activa(sesion)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(SESION_CERRADA);
    }
}
