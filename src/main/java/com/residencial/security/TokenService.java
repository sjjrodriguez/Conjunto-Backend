package com.residencial.security;

import com.residencial.dto.LoginResponse;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

@Service
public class TokenService {

    public static final String ROL_ADMIN = "ADMIN";
    public static final String ROL_PROPIETARIO = "PROPIETARIO";
    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_APARTAMENTO = "apartamentoId";

    private final JwtEncoder encoder;
    private final Duration duracion;

    public TokenService(JwtEncoder encoder, @Value("${app.jwt.expiracion-horas:8}") long horas) {
        this.encoder = encoder;
        this.duracion = Duration.ofHours(horas);
    }

    public LoginResponse paraAdmin(Administrador admin) {
        Instant expira = Instant.now().plus(duracion);
        JwtClaimsSet claims = base("admin:" + admin.getId(), ROL_ADMIN, expira).build();
        return LoginResponse.builder()
                .token(firmar(claims))
                .expiraEn(expira)
                .rol(ROL_ADMIN)
                .id(admin.getId())
                .usuario(admin.getUsuario())
                .build();
    }

    public LoginResponse paraApartamento(Apartamento apto) {
        Instant expira = Instant.now().plus(duracion);
        JwtClaimsSet claims = base("apartamento:" + apto.getId(), ROL_PROPIETARIO, expira)
                .claim(CLAIM_APARTAMENTO, apto.getId())
                .build();
        return LoginResponse.builder()
                .token(firmar(claims))
                .expiraEn(expira)
                .rol(ROL_PROPIETARIO)
                .id(apto.getId())
                .torre(apto.getTorre())
                .apto(apto.getApto())
                .build();
    }

    private JwtClaimsSet.Builder base(String sujeto, String rol, Instant expira) {
        return JwtClaimsSet.builder()
                .issuer("miconjunto")
                .subject(sujeto)
                .issuedAt(Instant.now())
                .expiresAt(expira)
                .claim(CLAIM_ROL, rol);
    }

    private String firmar(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
