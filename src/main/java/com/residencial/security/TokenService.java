package com.residencial.security;

import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.conjunto.FiltroConjunto;
import com.residencial.repository.ConjuntoRepository;
import com.residencial.dto.LoginResponse;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.OperadorPlataforma;
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
    // P7 bloque 3: maneja los conjuntos de la plataforma; no es de ninguno ni ve sus datos
    public static final String ROL_PLATAFORMA = "PLATAFORMA";
    // Más corta que la de las cuentas de un conjunto: puede crear y suspender conjuntos
    private static final Duration DURACION_PLATAFORMA = Duration.ofHours(2);
    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_APARTAMENTO = "apartamentoId";
    public static final String CLAIM_RESIDENTE = "residenteId";
    // P6: versión de sesión con la que se emitió el token (ver SesionVersion)
    public static final String CLAIM_VERSION = "tv";

    private final JwtEncoder encoder;
    private final Duration duracion;
    private final ResidenteRepository residentes;
    private final ConfiguracionService configuracion;
    private final ConjuntoRepository conjuntos;

    public TokenService(JwtEncoder encoder, @Value("${app.jwt.expiracion-horas:8}") long horas,
                        ResidenteRepository residentes, ConfiguracionService configuracion,
                        ConjuntoRepository conjuntos) {
        this.encoder = encoder;
        this.duracion = Duration.ofHours(horas);
        this.residentes = residentes;
        this.configuracion = configuracion;
        this.conjuntos = conjuntos;
    }

    /** P7: el conjunto de la cuenta, para que la app lo muestre; el de la petición si la entidad aún no lo tiene. */
    private LoginResponse conConjunto(LoginResponse respuesta, Long conjunto) {
        long id = conjunto == null ? ConjuntoContexto.actual() : conjunto;
        respuesta.setConjuntoId(id);
        conjuntos.findById(id).ifPresent(c -> respuesta.setConjuntoNombre(c.getNombre()));
        return respuesta;
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
        JwtClaimsSet claims = base("admin:" + admin.getId(), rol, expira, admin.getTokenVersion(), admin.getConjuntoId())
                .claim("usuario", admin.getUsuario()).build();
        return conConjunto(LoginResponse.builder()
                .token(firmar(claims))
                .expiraEn(expira)
                .rol(rol)
                .id(admin.getId())
                .usuario(admin.getUsuario())
                .nombre(admin.getNombreCompleto())
                .build(), admin.getConjuntoId());
    }

    /** P7 bloque 3: el token de la plataforma no lleva conjunto (FiltroConjunto no lo revisa). */
    public LoginResponse paraPlataforma(OperadorPlataforma operador) {
        Instant expira = Instant.now().plus(DURACION_PLATAFORMA);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("miconjunto")
                .subject("plataforma:" + operador.getId())
                .issuedAt(Instant.now())
                .expiresAt(expira)
                .claim(CLAIM_ROL, ROL_PLATAFORMA)
                .claim(CLAIM_VERSION, operador.getTokenVersion())
                .claim("usuario", operador.getUsuario())
                .build();
        return LoginResponse.builder()
                .token(firmar(claims))
                .expiraEn(expira)
                .rol(ROL_PLATAFORMA)
                .id(operador.getId())
                .usuario(operador.getUsuario())
                .nombre(operador.getNombreCompleto())
                .build();
    }

    public LoginResponse paraApartamento(Apartamento apto) {
        Instant expira = Instant.now().plus(duracion);
        JwtClaimsSet claims = base("apartamento:" + apto.getId(), ROL_PROPIETARIO, expira, apto.getTokenVersion(), apto.getConjuntoId())
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
        JwtClaimsSet claims = base("residente:" + residente.getId(), rol, expira, residente.getTokenVersion(), residente.getConjuntoId())
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
        return conConjunto(respuesta
                .tipoOcupacion(apto.getTipoOcupacion())
                .arrendatarioVeCartera(apto.isArrendatarioVeCartera())
                .arrendatarioPuedePagar(apto.isArrendatarioPuedePagar())
                .arrendatarioVeObras(apto.isArrendatarioVeObras())
                .vehiculosAltaSoloAdmin(configuracion.vehiculosAltaSoloAdmin())
                .consentimientosPendientes(false)
                .build(), apto.getConjuntoId());
    }

    private JwtClaimsSet.Builder base(String sujeto, String rol, Instant expira, int version, Long conjunto) {
        if (conjunto == null) conjunto = ConjuntoContexto.actual();
        return JwtClaimsSet.builder()
                .issuer("miconjunto")
                .subject(sujeto)
                .issuedAt(Instant.now())
                .expiresAt(expira)
                .claim(CLAIM_ROL, rol)
                .claim(CLAIM_VERSION, version)
                // P7: el conjunto de la cuenta; FiltroConjunto lo usa en cada petición
                .claim(FiltroConjunto.CLAIM, conjunto);
    }

    private String firmar(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
