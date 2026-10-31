package com.residencial.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Administrador;
import com.residencial.model.Catalogo;
import com.residencial.model.CupoEquipo;
import com.residencial.repository.AdministradorRepository;
import com.residencial.repository.CupoEquipoRepository;
import com.residencial.security.AccesoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Equipo de la administración. Desde P5 solo lo maneja el administrador principal: crea asistentes
 * (administradores sin acceso al equipo) y contadores (solo cartera y facturación), con sus datos
 * personales y hasta el cupo que él fije para cada rol.
 */
@RestController
@RequestMapping("/api/administradores")
@RequiredArgsConstructor
public class AdministradorController {

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<String> ROLES = List.of(Administrador.ADMIN, Administrador.CONTADOR, Administrador.PORTERIA);

    private final AdministradorRepository repo;
    private final CupoEquipoRepository cupos;
    private final PasswordEncoder encoder;
    private final AccesoService acceso;

    public record CuentaDTO(Long id, String usuario, String rol,
                            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String contrasena,
                            String nombreCompleto, String documento, String telefono, String correo,
                            @JsonProperty(access = JsonProperty.Access.READ_ONLY) Boolean principal) {}

    /** maximo null = sin límite; usados = cuentas de ese rol (sin contar al principal). */
    public record CupoDTO(String rol, Integer maximo, Long usados) {}

    private CuentaDTO toDTO(Administrador a) {
        return new CuentaDTO(a.getId(), a.getUsuario(), a.getRol(), null, a.getNombreCompleto(), a.getDocumento(),
                a.getTelefono(), a.getCorreo(), a.isPrincipal());
    }

    @GetMapping
    public List<CuentaDTO> listar() {
        exigirPrincipal();
        return repo.findAll().stream()
                .sorted(Comparator.comparing(Administrador::isPrincipal).reversed()
                        .thenComparing(Administrador::getRol).thenComparing(Administrador::getUsuario))
                .map(this::toDTO).toList();
    }

    @PostMapping
    @Transactional
    public CuentaDTO crear(@RequestBody CuentaDTO dto) {
        Administrador principal = exigirPrincipal();
        String usuario = dto.usuario() == null ? "" : dto.usuario().trim();
        if (usuario.length() < 3) throw invalido("El usuario debe tener al menos 3 caracteres");
        if (dto.contrasena() == null || dto.contrasena().length() < 6) {
            throw invalido("La contraseña debe tener al menos 6 caracteres");
        }
        if (repo.findByUsuario(usuario).isPresent()) throw invalido("Ya existe una cuenta con ese usuario");
        String rol = Catalogo.ROL_ADMINISTRACION.normalizar(dto.rol() == null ? Administrador.CONTADOR : dto.rol());
        verificarCupo(rol);
        Administrador a = new Administrador(null, usuario, encoder.encode(dto.contrasena()), rol);
        datosPersonales(a, dto, true);
        // Quien crea la primera cuenta queda como principal (bases sin marcar)
        if (principal != null && !principal.isPrincipal()) {
            principal.setPrincipal(true);
            repo.save(principal);
        }
        return toDTO(repo.save(a));
    }

    /** Cambia el rol, la contraseña y/o los datos personales (todo opcional). */
    @PutMapping("/{id}")
    @Transactional
    public CuentaDTO actualizar(@PathVariable Long id, @RequestBody CuentaDTO dto) {
        exigirPrincipal();
        Administrador a = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada: " + id));
        if (dto.rol() != null) {
            String rol = Catalogo.ROL_ADMINISTRACION.normalizar(dto.rol());
            if (!rol.equals(a.getRol())) {
                if (a.isPrincipal() || esPropia(id)) throw invalido("No puedes quitarte el rol de administrador");
                if (Administrador.ADMIN.equals(a.getRol()) && administradores() <= 1) {
                    throw invalido("Debe quedar al menos un administrador");
                }
                verificarCupo(rol);
            }
            a.setRol(rol);
        }
        if (dto.contrasena() != null && !dto.contrasena().isEmpty()) {
            if (dto.contrasena().length() < 6) throw invalido("La contraseña debe tener al menos 6 caracteres");
            a.setContrasena(encoder.encode(dto.contrasena()));
        }
        datosPersonales(a, dto, false);
        return toDTO(repo.save(a));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        exigirPrincipal();
        Administrador a = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada: " + id));
        if (esPropia(id)) throw invalido("No puedes eliminar tu propia cuenta");
        if (a.isPrincipal()) throw invalido("La cuenta del administrador principal no se puede eliminar");
        if (Administrador.ADMIN.equals(a.getRol()) && administradores() <= 1) {
            throw invalido("Debe quedar al menos un administrador");
        }
        repo.delete(a);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cupos")
    public List<CupoDTO> cupos() {
        exigirPrincipal();
        return ROLES.stream()
                .map(rol -> new CupoDTO(rol, cupos.findById(rol).map(CupoEquipo::getMaximo).orElse(null),
                        repo.countByRolAndPrincipalFalse(rol)))
                .toList();
    }

    /** [{rol, maximo}]: maximo null quita el límite. No se puede bajar de las cuentas que ya hay. */
    @PutMapping("/cupos")
    @Transactional
    public List<CupoDTO> fijarCupos(@RequestBody List<CupoDTO> nuevos) {
        exigirPrincipal();
        for (CupoDTO c : nuevos) {
            String rol = Catalogo.ROL_ADMINISTRACION.normalizar(c.rol());
            if (c.maximo() == null) {
                if (cupos.existsById(rol)) cupos.deleteById(rol);
                continue;
            }
            if (c.maximo() < 0) throw invalido("El cupo no puede ser negativo");
            long usados = repo.countByRolAndPrincipalFalse(rol);
            if (c.maximo() < usados) {
                throw invalido("Ya hay " + usados + " cuentas de " + etiqueta(rol) + ": elimina alguna antes de bajar el cupo");
            }
            cupos.save(new CupoEquipo(rol, c.maximo()));
        }
        return cupos();
    }

    // Al crear: todos obligatorios; al editar, solo los que lleguen
    private void datosPersonales(Administrador a, CuentaDTO dto, boolean obligatorios) {
        String nombre = limpio(dto.nombreCompleto());
        String documento = limpio(dto.documento());
        String telefono = limpio(dto.telefono());
        String correo = limpio(dto.correo());
        if (obligatorios) {
            if (nombre == null) throw invalido("El nombre completo es obligatorio");
            if (documento == null) throw invalido("El documento es obligatorio");
            if (telefono == null) throw invalido("El teléfono es obligatorio");
            if (correo == null) throw invalido("El correo es obligatorio");
        }
        if (correo != null && !CORREO.matcher(correo).matches()) throw invalido("El correo no es válido");
        if (nombre != null) a.setNombreCompleto(nombre);
        if (documento != null) a.setDocumento(documento);
        if (telefono != null) a.setTelefono(telefono);
        if (correo != null) a.setCorreo(correo.toLowerCase());
    }

    private void verificarCupo(String rol) {
        Optional<CupoEquipo> cupo = cupos.findById(rol);
        if (cupo.isPresent() && repo.countByRolAndPrincipalFalse(rol) >= cupo.get().getMaximo()) {
            throw invalido("Ya usaste el cupo de " + cupo.get().getMaximo() + " cuentas de " + etiqueta(rol));
        }
    }

    /** La cuenta de la sesión si es el principal; sin sesión (modo transición) pasa con null. */
    private Administrador exigirPrincipal() {
        if (acceso.tokenActual().isEmpty()) return null;
        Administrador a = acceso.tokenActual()
                .map(t -> t.getSubject())
                .filter(s -> s != null && s.startsWith("admin:"))
                .map(s -> Long.valueOf(s.substring("admin:".length())))
                .flatMap(repo::findById)
                .orElseThrow(() -> new AccessDeniedException("Solo el administrador principal maneja el equipo"));
        if (!repo.esPrincipal(a)) throw new AccessDeniedException("Solo el administrador principal maneja el equipo");
        return a;
    }

    private boolean esPropia(Long id) {
        return acceso.tokenActual().map(t -> ("admin:" + id).equals(t.getSubject())).orElse(false);
    }

    private long administradores() {
        return repo.findAll().stream().filter(x -> Administrador.ADMIN.equals(x.getRol())).count();
    }

    private static String etiqueta(String rol) {
        return Administrador.CONTADOR.equals(rol) ? "contador" : Administrador.PORTERIA.equals(rol) ? "portería" : "asistente";
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
