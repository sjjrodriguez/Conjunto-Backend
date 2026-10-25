package com.residencial.security;

import com.residencial.dto.LoginResponse;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Residente;
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
    // Entra por "Administración" pero solo maneja la cartera y la facturación (P4 bloque 7)
    public static final String ROL_CONTADOR = "CONTADOR";
    public static final String ROL_PROPIETARIO = "PROPIETARIO";
    public static final String ROL_ARRENDATARIO = "ARRENDATARIO";
    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_APARTAMENTO = "apartamentoId";
    public static final String CLAIM_RESIDENTE = "residenteId";

    private final JwtEncoder encoder;
    private final Duration duracion;

    public TokenService(JwtEncoder encoder, @Value("${app.jwt.expiracion-horas:8}") long horas) {
        this.encoder = encoder;
        this.duracion = Duration.ofHours(horas);
    }

    public LoginResponse paraAdmin(Administrador admin) {
        Instant expira = Instant.now().plus(duracion);
        String rol = ROL_CONTADOR.equals(admin.getRol()) ? ROL_CONTADOR : ROL_ADMIN;
        JwtClaimsSet claims = base("admin:" + admin.getId(), rol, expira).build();
        return LoginResponse.builder()
                .token(firmar(claims))
                .expiraEn(expira)
                .rol(rol)
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

    /**
     * Residente que entra con su documento: su rol es su tipo de habitante (PROPIETARIO o
     * ARRENDATARIO). Como el login del apartamento, id es el del apartamento.
     */
    public LoginResponse paraResidente(Residente residente) {
        Apartamento apto = residente.getApartamento();
        String rol = ROL_ARRENDATARIO.equals(residente.getTipoHabitante()) ? ROL_ARRENDATARIO : ROL_PROPIETARIO;
        Instant expira = Instant.now().plus(duracion);
        JwtClaimsSet claims = base("residente:" + residente.getId(), rol, expira)
                .claim(CLAIM_APARTAMENTO, apto.getId())
                .claim(CLAIM_RESIDENTE, residente.getId())
                .build();
        return LoginResponse.builder()
                .token(firmar(claims))
                .expiraEn(expira)
                .rol(rol)
                .id(apto.getId())
                .torre(apto.getTorre())
                .apto(apto.getApto())
                .residenteId(residente.getId())
                .nombre(residente.getNombreCompleto())
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
