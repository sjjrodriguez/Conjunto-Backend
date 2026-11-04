package com.residencial.service;

import com.residencial.correo.Correo;
import com.residencial.correo.Plantillas;
import com.residencial.dto.LoginResponse;
import com.residencial.model.Administrador;
import com.residencial.model.CodigoVerificacion;
import com.residencial.model.DispositivoCuenta;
import com.residencial.model.Residente;
import com.residencial.repository.AdministradorRepository;
import com.residencial.repository.CodigoVerificacionRepository;
import com.residencial.repository.DispositivoCuentaRepository;
import com.residencial.repository.ResidenteRepository;
import com.residencial.security.LimitadorLogin;
import com.residencial.security.SesionesDispositivo;
import com.residencial.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * P7 bloque 5: el login en un celular. Con la contraseña ya comprobada, si la cuenta nunca
 * verificó ese celular se envía un código al correo y el login no devuelve token hasta que se
 * escribe; si ya lo verificó, abre la sesión de ese celular (SesionesDispositivo).
 */
@Service
public class VerificacionDispositivo {

    private static final Logger log = LoggerFactory.getLogger(VerificacionDispositivo.class);
    private static final SecureRandom ALEATORIO = new SecureRandom();
    static final int MINUTOS = 10;
    static final int MAX_INTENTOS = 5;
    static final int MAX_ENVIOS = 3;
    private static final int SEGUNDOS_ENTRE_ENVIOS = 60;
    private static final String NO_VALIDO = "El código ya no es válido: vuelve a iniciar sesión";

    /** El celular que hace la petición (cabeceras de la app); null si es la app anterior. */
    public record Celular(String id, String nombre, String tipoApp) {}

    /** Un celular de la cuenta en "Mis dispositivos". */
    public record Dispositivo(Long id, String nombre, String app, LocalDateTime verificadoEn,
                              LocalDateTime sesionDesde, boolean sesionAbierta, boolean actual) {}

    public record Reenvio(String correoOculto) {}

    private final SesionesDispositivo sesiones;
    private final CodigoVerificacionRepository codigos;
    private final DispositivoCuentaRepository dispositivos;
    private final AdministradorRepository admins;
    private final ResidenteRepository residentes;
    private final BiometriaService cuentas;
    private final PasswordEncoder encoder;
    private final Correo correo;
    private final LimitadorLogin limitador;
    private final ZoneId zona;
    private final boolean exigirDispositivo;
    private final boolean registrarCodigo;

    public VerificacionDispositivo(SesionesDispositivo sesiones, CodigoVerificacionRepository codigos,
                                   DispositivoCuentaRepository dispositivos, AdministradorRepository admins,
                                   ResidenteRepository residentes, BiometriaService cuentas, PasswordEncoder encoder,
                                   Correo correo, LimitadorLogin limitador,
                                   @Value("${app.zona-horaria:America/Bogota}") String zona,
                                   @Value("${app.sesiones.exigir-dispositivo:false}") boolean exigirDispositivo,
                                   @Value("${app.sesiones.registrar-codigo:false}") boolean registrarCodigo) {
        this.sesiones = sesiones;
        this.codigos = codigos;
        this.dispositivos = dispositivos;
        this.admins = admins;
        this.residentes = residentes;
        this.cuentas = cuentas;
        this.encoder = encoder;
        this.correo = correo;
        this.limitador = limitador;
        this.zona = ZoneId.of(zona);
        this.exigirDispositivo = exigirDispositivo;
        this.registrarCodigo = registrarCodigo;
    }

    public static Celular celular(HttpServletRequest http) {
        String id = http.getHeader(SesionesDispositivo.CABECERA);
        if (id == null || id.isBlank() || id.length() > 64) return null;
        return new Celular(id.trim(), http.getHeader(SesionesDispositivo.CABECERA_NOMBRE), http.getHeader("X-App-Tipo"));
    }

    /**
     * Termina un login con la contraseña ya comprobada: pide el código si el celular es nuevo para
     * la cuenta o abre su sesión y emite el token con [emitir].
     */
    public LoginResponse entrar(String sujeto, String correoCuenta, String nombre, HttpServletRequest http,
                                Supplier<LoginResponse> emitir) {
        Celular c = celular(http);
        if (c == null) {
            if (exigirDispositivo) {
                throw new ResponseStatusException(HttpStatus.UPGRADE_REQUIRED, "Actualiza la app para iniciar sesión");
            }
            return emitir.get();
        }
        if (SesionesDispositivo.aplica(sujeto) && !sesiones.verificado(sujeto, c.id())) {
            if (correoCuenta != null && !correoCuenta.isBlank()) return pedirCodigo(sujeto, correoCuenta, nombre, c);
            log.warn("La cuenta {} no tiene correo: entra en un celular nuevo sin código", sujeto);
        }
        return abrir(sujeto, c, false, emitir);
    }

    /** El código que llegó al correo: abre la sesión y deja el celular verificado. */
    public LoginResponse verificar(String id, String codigo, HttpServletRequest http) {
        CodigoVerificacion v = vigente(id, http);
        String clave = "codigo|" + v.getSujeto();
        limitador.verificar(clave);
        if (v.getIntentos() >= MAX_INTENTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demasiados intentos: pide un código nuevo");
        }
        if (codigo == null || !encoder.matches(codigo.trim(), v.getCodigo())) {
            v.setIntentos(v.getIntentos() + 1);
            codigos.save(v);
            limitador.registrarFallo(clave);
            int quedan = MAX_INTENTOS - v.getIntentos();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, quedan > 0
                    ? "Código incorrecto. Te quedan " + quedan + (quedan == 1 ? " intento" : " intentos")
                    : "Demasiados intentos: pide un código nuevo");
        }
        limitador.limpiar(clave);
        v.setUsado(true);
        codigos.save(v);
        return abrir(v.getSujeto(), new Celular(v.getDispositivo(), v.getNombre(), v.getTipoApp()), true,
                () -> cuentas.sesionDe(v.getSujeto()).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Esta cuenta ya no puede entrar")));
    }

    /** Un código nuevo para el mismo login (uno por minuto, hasta 3 envíos). */
    public Reenvio reenviar(String id, HttpServletRequest http) {
        CodigoVerificacion v = vigente(id, http);
        LocalDateTime ahora = LocalDateTime.now(zona);
        if (v.getEnvios() >= MAX_ENVIOS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Ya se enviaron varios códigos: vuelve a iniciar sesión más tarde");
        }
        if (v.getEnviadoEn().plusSeconds(SEGUNDOS_ENTRE_ENVIOS).isAfter(ahora)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Espera un minuto para pedir otro código");
        }
        Optional<String[]> cuenta = correoYNombre(v.getSujeto());
        if (cuenta.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_VALIDO);
        String nuevo = generar();
        v.setCodigo(encoder.encode(nuevo));
        v.setIntentos(0);
        v.setEnvios(v.getEnvios() + 1);
        v.setEnviadoEn(ahora);
        v.setVence(ahora.plusMinutes(MINUTOS));
        codigos.save(v);
        enviar(cuenta.get()[0], cuenta.get()[1], nuevo, v.getNombre(), v.getSujeto());
        return new Reenvio(ocultar(cuenta.get()[0]));
    }

    /**
     * Cambió la contraseña: [revocar] cierra todas las sesiones de la cuenta, pero quien la cambió
     * sigue en su celular con un token nuevo de [emitir].
     */
    public <T> T renovar(Jwt sesion, Runnable revocar, Supplier<T> emitir) {
        Optional<DispositivoCuenta> actual = Optional.ofNullable(sesion.getClaimAsString(TokenService.CLAIM_SESION))
                .flatMap(dispositivos::findBySesion);
        revocar.run();
        return actual.map(d -> {
            SesionesDispositivo.Apertura a = sesiones.abrir(d.getSujeto(), d.getDispositivo(), d.getNombre(), d.getTipoApp(), false);
            return SesionesDispositivo.conSesion(a.sesion(), emitir);
        }).orElseGet(emitir);
    }

    /** Los celulares de la cuenta del token, el de la sesión actual marcado. */
    public List<Dispositivo> listar(Jwt sesion) {
        String actual = sesion.getClaimAsString(TokenService.CLAIM_SESION);
        return dispositivos.findBySujetoOrderBySesionDesdeDesc(sesion.getSubject()).stream()
                .map(d -> new Dispositivo(d.getId(), d.getNombre(), d.getTipoApp(), d.getVerificadoEn(), d.getSesionDesde(),
                        d.getSesion() != null, d.getSesion() != null && d.getSesion().equals(actual)))
                .toList();
    }

    /** Cierra la sesión de ese celular y lo olvida: si vuelve a entrar, pide código. */
    public void quitar(Jwt sesion, Long id) {
        DispositivoCuenta d = dispositivos.findById(id)
                .filter(x -> x.getSujeto().equals(sesion.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese celular no está en tu cuenta"));
        sesiones.quitar(d);
    }

    private LoginResponse abrir(String sujeto, Celular c, boolean verificado, Supplier<LoginResponse> emitir) {
        SesionesDispositivo.Apertura a = sesiones.abrir(sujeto, c.id(), c.nombre(), c.tipoApp(), verificado);
        try {
            LoginResponse r = SesionesDispositivo.conSesion(a.sesion(), emitir);
            r.setSesionCerradaEn(a.cerradaEn());
            return r;
        } catch (RuntimeException e) {
            sesiones.cerrar(a.sesion());
            throw e;
        }
    }

    private LoginResponse pedirCodigo(String sujeto, String correoCuenta, String nombre, Celular c) {
        LocalDateTime ahora = LocalDateTime.now(zona);
        String codigo = generar();
        CodigoVerificacion v = new CodigoVerificacion();
        v.setId(UUID.randomUUID().toString());
        v.setSujeto(sujeto);
        v.setDispositivo(c.id());
        v.setNombre(c.nombre() == null ? null : c.nombre().length() > 80 ? c.nombre().substring(0, 80) : c.nombre());
        v.setTipoApp(c.tipoApp() == null ? null : c.tipoApp().length() > 20 ? c.tipoApp().substring(0, 20) : c.tipoApp());
        v.setCodigo(encoder.encode(codigo));
        v.setEnviadoEn(ahora);
        v.setVence(ahora.plusMinutes(MINUTOS));
        codigos.save(v);
        enviar(correoCuenta, nombre, codigo, v.getNombre(), sujeto);
        return LoginResponse.builder().verificacionId(v.getId()).correoOculto(ocultar(correoCuenta)).build();
    }

    /** El código de ese login si sigue sirviendo y lo pide el mismo celular. */
    private CodigoVerificacion vigente(String id, HttpServletRequest http) {
        Celular c = celular(http);
        CodigoVerificacion v = id == null ? null : codigos.findById(id).orElse(null);
        if (v == null || c == null || !c.id().equals(v.getDispositivo()) || v.isUsado()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_VALIDO);
        }
        if (v.getVence().isBefore(LocalDateTime.now(zona))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código venció: pide uno nuevo");
        }
        return v;
    }

    private void enviar(String para, String nombre, String codigo, String celular, String sujeto) {
        if (registrarCodigo) log.info("Código para {} ({}): {}", sujeto, para, codigo);
        correo.enviar(para, nombre, Plantillas.asuntoCodigo(codigo), Plantillas.codigo(nombre, codigo, celular, MINUTOS));
    }

    /** Correo y nombre de la cuenta ("admin:ID" o "residente:ID"). */
    private Optional<String[]> correoYNombre(String sujeto) {
        Long id = Long.valueOf(sujeto.substring(sujeto.indexOf(':') + 1));
        if (sujeto.startsWith("admin:")) {
            return admins.findById(id).filter(Administrador::isActivo).filter(a -> a.getCorreo() != null)
                    .map(a -> new String[]{a.getCorreo(), a.getNombreCompleto()});
        }
        return residentes.findById(id).filter(Residente::puedeEntrar).filter(r -> r.getCorreo() != null)
                .map(r -> new String[]{r.getCorreo(), r.getNombreCompleto()});
    }

    private static String generar() {
        return String.format("%06d", ALEATORIO.nextInt(1_000_000));
    }

    /** "msergio@gmail.com" → "ms***@gmail.com". */
    static String ocultar(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 0) return "***";
        return correo.substring(0, Math.min(2, arroba)) + "***" + correo.substring(arroba);
    }
}
