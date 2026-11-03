package com.residencial.service;

import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.dto.LoginResponse;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Administrador;
import com.residencial.model.GuardiaPorteria;
import com.residencial.repository.AdministradorRepository;
import com.residencial.repository.GuardiaPorteriaRepository;
import com.residencial.security.AccesoService;
import com.residencial.security.LimitadorLogin;
import com.residencial.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * P7 bloque 4: guardias de la recepción y sus turnos. La administración los crea con un PIN; en el
 * celular de la recepción (cuenta de portería) cada guardia elige su nombre y abre el turno con su
 * PIN, y recibe un token con su id: las visitas que registra llevan su nombre.
 */
@Service
@Transactional
public class GuardiaService {

    public record Guardia(Long id, String nombre, String documento, boolean activo, LocalDateTime creadoEn) {}

    /** Lo que ve la pantalla de bloqueo: solo el nombre. */
    public record EnTurno(Long id, String nombre) {}

    /** [pin] null al editar: no cambia. */
    public record Datos(String nombre, String documento, String pin, Boolean activo) {}

    public record AbrirTurno(Long guardiaId, String pin) {}

    private final GuardiaPorteriaRepository guardias;
    private final AdministradorRepository admins;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final AccesoService acceso;
    private final LimitadorLogin limitador;

    public GuardiaService(GuardiaPorteriaRepository guardias, AdministradorRepository admins, PasswordEncoder encoder,
                          TokenService tokens, AccesoService acceso, LimitadorLogin limitador) {
        this.guardias = guardias;
        this.admins = admins;
        this.encoder = encoder;
        this.tokens = tokens;
        this.acceso = acceso;
        this.limitador = limitador;
    }

    @Transactional(readOnly = true)
    public List<Guardia> listar() {
        return guardias.findAllByOrderByActivoDescNombreAsc().stream().map(GuardiaService::aDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<EnTurno> paraElTurno() {
        return guardias.findByActivoTrueOrderByNombreAsc().stream().map(g -> new EnTurno(g.getId(), g.getNombre())).toList();
    }

    public Guardia crear(Datos d) {
        GuardiaPorteria g = new GuardiaPorteria();
        g.setNombre(nombre(d.nombre()));
        g.setDocumento(documento(d.documento()));
        if (guardias.existsByDocumento(g.getDocumento())) throw repetido();
        if (d.pin() == null) throw invalido("Escribe el PIN");
        g.setPin(encoder.encode(pin(d.pin())));
        return aDTO(guardias.save(g));
    }

    public Guardia editar(Long id, Datos d) {
        GuardiaPorteria g = buscar(id);
        g.setNombre(nombre(d.nombre()));
        g.setDocumento(documento(d.documento()));
        if (guardias.existsByDocumentoAndIdNot(g.getDocumento(), id)) throw repetido();
        if (d.pin() != null && !d.pin().isBlank()) g.setPin(encoder.encode(pin(d.pin())));
        if (d.activo() != null) g.setActivo(d.activo());
        return aDTO(guardias.save(g));
    }

    /**
     * Solo la cuenta de portería (el celular de la recepción) abre turnos. Mismo límite de intentos
     * que el login, por guardia: un PIN de 4 dígitos no se puede adivinar probando.
     */
    public LoginResponse abrirTurno(AbrirTurno pedido) {
        Jwt sesion = acceso.tokenActual().orElseThrow(() -> new AccessDeniedException("Debes iniciar sesión"));
        if (!TokenService.ROL_PORTERIA.equals(sesion.getClaimAsString(TokenService.CLAIM_ROL))) {
            throw new AccessDeniedException("Solo la cuenta de portería abre turnos");
        }
        if (pedido.guardiaId() == null) throw invalido("Elige quién recibe el turno");
        String clave = "pin|" + ConjuntoContexto.actual() + "|" + pedido.guardiaId();
        limitador.verificar(clave);
        GuardiaPorteria g = guardias.findById(pedido.guardiaId()).filter(GuardiaPorteria::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ese guardia no existe o está de baja"));
        if (pedido.pin() == null || !encoder.matches(pedido.pin(), g.getPin())) {
            limitador.registrarFallo(clave);
            throw invalido("PIN incorrecto");
        }
        limitador.limpiar(clave);
        Long id = Long.valueOf(sesion.getSubject().substring("admin:".length()));
        Administrador porteria = admins.findById(id).orElseThrow(() -> new AccessDeniedException("Debes iniciar sesión"));
        return tokens.paraTurno(porteria, g);
    }

    /**
     * El guardia del turno de la petición, si lo hay. Si lo dieron de baja durante el turno, la
     * recepción tiene que volver a abrirlo con otro guardia.
     */
    @Transactional(readOnly = true)
    public Optional<GuardiaPorteria> delTurno() {
        Object gid = acceso.tokenActual().map(t -> t.getClaim(TokenService.CLAIM_GUARDIA)).orElse(null);
        if (!(gid instanceof Number n)) return Optional.empty();
        GuardiaPorteria g = guardias.findById(n.longValue()).filter(GuardiaPorteria::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Tu turno ya no es válido: vuelve a abrirlo con tu PIN"));
        return Optional.of(g);
    }

    private GuardiaPorteria buscar(Long id) {
        return guardias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Guardia no encontrado: " + id));
    }

    private static String nombre(String s) {
        String n = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) throw invalido("Escribe el nombre");
        if (n.length() > 150) throw invalido("El nombre es muy largo (máximo 150 caracteres)");
        return n;
    }

    private static String documento(String s) {
        String d = s == null ? "" : s.trim();
        if (d.isEmpty()) throw invalido("Escribe el documento");
        if (d.length() > 30) throw invalido("El documento es muy largo (máximo 30 caracteres)");
        return d;
    }

    /** De 4 a 6 dígitos, sin repetir uno solo ni seguir una escalera (1234, 9876). */
    static String pin(String s) {
        String p = s.trim();
        if (!p.matches("\\d{4,6}")) throw invalido("El PIN debe tener de 4 a 6 dígitos");
        if (p.chars().distinct().count() == 1 || "0123456789".contains(p) || "9876543210".contains(p)) {
            throw invalido("Ese PIN es muy fácil de adivinar");
        }
        return p;
    }

    private static Guardia aDTO(GuardiaPorteria g) {
        return new Guardia(g.getId(), g.getNombre(), g.getDocumento(), g.isActivo(), g.getCreadoEn());
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private static ResponseStatusException repetido() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay un guardia con ese documento");
    }
}
