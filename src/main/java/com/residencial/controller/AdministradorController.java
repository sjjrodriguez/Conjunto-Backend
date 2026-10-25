package com.residencial.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Administrador;
import com.residencial.model.Catalogo;
import com.residencial.repository.AdministradorRepository;
import com.residencial.security.AccesoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/**
 * Cuentas de la administración (solo el administrador): otros administradores y el contador,
 * que entra por "Administración" y solo ve la cartera y la facturación (P4 bloque 7).
 */
@RestController
@RequestMapping("/api/administradores")
@RequiredArgsConstructor
public class AdministradorController {

    private final AdministradorRepository repo;
    private final PasswordEncoder encoder;
    private final AccesoService acceso;

    public record CuentaDTO(Long id, String usuario, String rol,
                            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String contrasena) {}

    @GetMapping
    public List<CuentaDTO> listar() {
        return repo.findAll().stream()
                .sorted(Comparator.comparing(Administrador::getRol).thenComparing(Administrador::getUsuario))
                .map(a -> new CuentaDTO(a.getId(), a.getUsuario(), a.getRol(), null)).toList();
    }

    @PostMapping
    @Transactional
    public CuentaDTO crear(@RequestBody CuentaDTO dto) {
        String usuario = dto.usuario() == null ? "" : dto.usuario().trim();
        if (usuario.length() < 3) throw invalido("El usuario debe tener al menos 3 caracteres");
        if (dto.contrasena() == null || dto.contrasena().length() < 6) {
            throw invalido("La contraseña debe tener al menos 6 caracteres");
        }
        if (repo.findByUsuario(usuario).isPresent()) throw invalido("Ya existe una cuenta con ese usuario");
        String rol = Catalogo.ROL_ADMINISTRACION.normalizar(dto.rol() == null ? Administrador.CONTADOR : dto.rol());
        Administrador a = repo.save(new Administrador(null, usuario, encoder.encode(dto.contrasena()), rol));
        return new CuentaDTO(a.getId(), a.getUsuario(), a.getRol(), null);
    }

    /** Cambia el rol y/o la contraseña (los dos opcionales). */
    @PutMapping("/{id}")
    @Transactional
    public CuentaDTO actualizar(@PathVariable Long id, @RequestBody CuentaDTO dto) {
        Administrador a = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada: " + id));
        if (dto.rol() != null) {
            String rol = Catalogo.ROL_ADMINISTRACION.normalizar(dto.rol());
            if (!rol.equals(a.getRol()) && Administrador.ADMIN.equals(a.getRol())) {
                if (esPropia(id)) throw invalido("No puedes quitarte el rol de administrador");
                if (administradores() <= 1) throw invalido("Debe quedar al menos un administrador");
            }
            a.setRol(rol);
        }
        if (dto.contrasena() != null && !dto.contrasena().isEmpty()) {
            if (dto.contrasena().length() < 6) throw invalido("La contraseña debe tener al menos 6 caracteres");
            a.setContrasena(encoder.encode(dto.contrasena()));
        }
        Administrador guardado = repo.save(a);
        return new CuentaDTO(guardado.getId(), guardado.getUsuario(), guardado.getRol(), null);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        Administrador a = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada: " + id));
        if (esPropia(id)) throw invalido("No puedes eliminar tu propia cuenta");
        if (Administrador.ADMIN.equals(a.getRol()) && administradores() <= 1) {
            throw invalido("Debe quedar al menos un administrador");
        }
        repo.delete(a);
        return ResponseEntity.noContent().build();
    }

    private boolean esPropia(Long id) {
        return acceso.tokenActual().map(t -> ("admin:" + id).equals(t.getSubject())).orElse(false);
    }

    private long administradores() {
        return repo.findAll().stream().filter(x -> Administrador.ADMIN.equals(x.getRol())).count();
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
