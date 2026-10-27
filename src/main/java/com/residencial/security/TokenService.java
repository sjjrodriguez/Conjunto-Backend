package com.residencial.security;

import com.residencial.dto.LoginResponse;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Residente;
import com.residencial.repository.ResidenteRepository;
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
    // P6: portería; solo registra visitas y consulta citófonos
    public static final String ROL_PORTERIA = "PORTERIA";
    public static final String ROL_PROPIETARIO = "PROPIETARIO";
    public static final String ROL_ARRENDATARIO = "ARRENDATARIO";
    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_APARTAMENTO = "apartamentoId";
    public static final String CLAIM_RESIDENTE = "residenteId";
    // P6: versión de sesión con la que se emitió el token (ver SesionVersion)
    public static final String CLAIM_VERSION = "tv";

    private final JwtEncoder encoder;
    private final Duration duracion;
    private final ResidenteRepository residentes;
    private final ConfiguracionService configuracion;

    public TokenService(JwtEncoder encoder, @Value("${app.jwt.expiracion-horas:8}") long horas,
                        ResidenteRepository residentes, ConfiguracionService configuracion) {
        this.encoder = encoder;
        this.duracion = Duration.ofHours(horas);
        this.residentes = residentes;
        this.configuracion = configuracion;
    }

    /** El rol del token de un administrador: cada rol de la cuenta es el suyo, sin ascender ninguno. */
    public static String rolDeAdmin(Administrador admin) {
        if (ROL_CONTADOR.equals(admin.getRol())) return ROL_CONTADOR;
        if (ROL_PORTERIA.equals(admin.getRol())) return ROL_PORTERIA;
        return ROL_ADMIN;
    }

    public LoginResponse paraAdmin(Administrador admin) {
        Instant expira = Instant.now().plus(duracion);
        String rol = rolDeAdmin(admin);
        // usuario: quién mandó una solicitud contable (P5)
        JwtClaimsSet claims = base("admin:" + admin.getId(), rol, expira, admin.getTokenVersion())
                .claim("usuario", admin.getUsuario()).build();
        return LoginResponse.builder()
                .token(firmar(claims))
                .expiraEn(expira)
                .rol(rol)
                .id(admin.getId())
                .usuario(admin.getUsuario())
                .nombre(admin.getNombreCompleto())
                .build();
    }

    public LoginResponse paraApartamento(Apartamento apto) {
        Instant expira = Instant.now().plus(duracion);
        JwtClaimsSet claims = base("apartamento:" + apto.getId(), ROL_PROPIETARIO, expira, apto.getTokenVersion())
                .claim(CLAIM_APARTAMENTO, apto.getId())
                .build();
        // El saludo: sin documento no se sabe quién entró, así que se usa el propietario titular
        Residente titular = residentes.findTitulares(apto.getId(), Residente.PROPIETARIO).stream()
                .filter(Residente::puedeEntrar).findFirst().orElse(null);
        LoginResponse.LoginResponseBuilder respuesta = LoginResponse.builder()
                .token(firmar(claims))
                .expiraEn(expira)
                .rol(ROL_PROPIETARIO)
                .id(apto.getId())
                .torre(apto.getTorre())
                .apto(apto.getApto())
                .residenteId(titular == null ? null : titular.getId())
                .nombre(titular == null ? null : titular.getNombreCompleto());
        return conAjustes(respuesta, apto);
    }

    /**
     * Residente que entra con su documento: su rol es su tipo de habitante (PROPIETARIO o
     * ARRENDATARIO). Como el login del apartamento, id es el del apartamento.
     */
    public LoginResponse paraResidente(Residente residente) {
        Apartamento apto = residente.getApartamento();
        String rol = ROL_ARRENDATARIO.equals(residente.getTipoHabitante()) ? ROL_ARRENDATARIO : ROL_PROPIETARIO;
        Instant expira = Instant.now().plus(duracion);
        JwtClaimsSet claims = base("residente:" + residente.getId(), rol, expira, residente.getTokenVersion())
                .claim(CLAIM_APARTAMENTO, apto.getId())
                .claim(CLAIM_RESIDENTE, residente.getId())
                .build();
        LoginResponse.LoginResponseBuilder respuesta = LoginResponse.builder()
                .token(firmar(claims))
                .expiraEn(expira)
                .rol(rol)
                .id(apto.getId())
                .torre(apto.getTorre())
                .apto(apto.getApto())
                .residenteId(residente.getId())
                .nombre(residente.getNombreCompleto());
        return conAjustes(respuesta, apto);
    }

    /** Ocupación del apartamento, lo que ve el arrendatario y los ajustes del conjunto que cambian la pantalla. */
    private LoginResponse conAjustes(LoginResponse.LoginResponseBuilder respuesta, Apartamento apto) {
        return respuesta
                .tipoOcupacion(apto.getTipoOcupacion())
                .arrendatarioVeCartera(apto.isArrendatarioVeCartera())
                .arrendatarioPuedePagar(apto.isArrendatarioPuedePagar())
                .arrendatarioVeObras(apto.isArrendatarioVeObras())
                .vehiculosAltaSoloAdmin(configuracion.vehiculosAltaSoloAdmin())
                .consentimientosPendientes(false)
                .build();
    }

    private JwtClaimsSet.Builder base(String sujeto, String rol, Instant expira, int version) {
        return JwtClaimsSet.builder()
                .issuer("miconjunto")
                .subject(sujeto)
                .issuedAt(Instant.now())
                .expiresAt(expira)
                .claim(CLAIM_ROL, rol)
                .claim(CLAIM_VERSION, version);
    }

    private String firmar(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
