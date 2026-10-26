package com.residencial.controller;

import com.residencial.dto.CambioPropiaContrasenaRequest;
import com.residencial.dto.LoginDTO;
import com.residencial.dto.MensajeError;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.model.Residente;
import com.residencial.dto.RegistroDTO;
import com.residencial.service.RegistroService;
import com.residencial.security.AccesoService;
import com.residencial.security.LimitadorLogin;
import com.residencial.security.TokenService;
import com.residencial.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final LimitadorLogin limitador;
    private final AccesoService acceso;
    private final RegistroService registro;

    @PostMapping("/admin/login")
    public ResponseEntity<?> loginAdmin(@RequestBody LoginDTO.AdminLoginRequest request, HttpServletRequest http) {
        String clave = http.getRemoteAddr() + "|admin|" + normalizar(request.getUsuario());
        limitador.verificar(clave);
        Administrador admin = authService.loginAdmin(request.getUsuario(), request.getContrasena());
        if (admin != null) {
            limitador.limpiar(clave);
            return ResponseEntity.ok(tokenService.paraAdmin(admin));
        }
        limitador.registrarFallo(clave);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MensajeError("Credenciales incorrectas"));
    }

    @PostMapping("/apartamento/login")
    public ResponseEntity<?> loginApartamento(@RequestBody LoginDTO.ApartamentoLoginRequest request,
                                              HttpServletRequest http) {
        String clave = http.getRemoteAddr() + "|apto|"
                + normalizar(request.getTorre()) + "-" + normalizar(request.getApto());
        limitador.verificar(clave);
        Apartamento apartamento = authService.loginApartamento(
                request.getTorre(), request.getApto(), request.getContrasena());
        if (apartamento != null) {
            limitador.limpiar(clave);
            return ResponseEntity.ok(tokenService.paraApartamento(apartamento));
        }
        limitador.registrarFallo(clave);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MensajeError("Torre, apto o contraseña incorrectos"));
    }

    /** Cada residente con su documento y su contraseña (P4 bloque 6). */
    @PostMapping("/residente/login")
    public ResponseEntity<?> loginResidente(@RequestBody LoginDTO.ResidenteLoginRequest request,
                                            HttpServletRequest http) {
        boolean porDocumento = request.getDocumento() != null && !request.getDocumento().isBlank();
        String clave = http.getRemoteAddr() + "|residente|" + (porDocumento ? normalizar(request.getDocumento())
                : normalizar(request.getTorre()) + "-" + normalizar(request.getApto()) + "-" + normalizar(request.getRol()));
        limitador.verificar(clave);
        Residente residente = porDocumento
                ? authService.loginResidente(request.getDocumento(), request.getContrasena())
                : registro.login(request.getTorre(), request.getApto(), request.getRol(), request.getContrasena());
        if (residente != null) {
            limitador.limpiar(clave);
            return ResponseEntity.ok(tokenService.paraResidente(residente));
        }
        limitador.registrarFallo(clave);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MensajeError(porDocumento ? "Documento o contraseña incorrectos" : "Torre, apto o contraseña incorrectos"));
    }

    /** Registro paso 1: con qué rol quedaría quien se registra en ese apartamento. */
    @PostMapping("/registro/verificar")
    public RegistroDTO.Verificado verificarRegistro(@RequestBody RegistroDTO.Verificar solicitud, HttpServletRequest http) {
        String clave = http.getRemoteAddr() + "|registro";
        limitador.verificar(clave);
        limitador.registrarFallo(clave); // cuenta cada consulta: evita recorrer todos los apartamentos
        return registro.verificar(solicitud);
    }

    /** Registro completo: queda pendiente de aprobación (y del propietario, si es arrendatario). */
    @PostMapping("/registro")
    public ResponseEntity<RegistroDTO.Recibido> registrar(@RequestBody RegistroDTO.Solicitud solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registro.registrar(solicitud));
    }

    /**
     * Cuenta → Perfil: el usuario cambia su propia contraseña confirmando la actual.
     * Si la actual no coincide responde 400 (no 401: la app trata 401 como sesión vencida).
     * Mismo límite de intentos que el login, para que no sirva para adivinarla.
     */
    @PutMapping("/contrasena")
    public ResponseEntity<?> cambiarContrasena(@Valid @RequestBody CambioPropiaContrasenaRequest request) {
        // Aun en modo transición hace falta saber de quién es la cuenta
        Jwt sesion = acceso.tokenActual()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión"));
        String clave = "contrasena|" + sesion.getSubject();
        limitador.verificar(clave);
        if (request.getContrasenaActual().equals(request.getContrasenaNueva())) {
            return ResponseEntity.badRequest().body(new MensajeError("La nueva contraseña debe ser distinta de la actual"));
        }
        if (!authService.cambiarContrasena(sesion.getSubject(), request.getContrasenaActual(), request.getContrasenaNueva())) {
            limitador.registrarFallo(clave);
            return ResponseEntity.badRequest().body(new MensajeError("La contraseña actual no es correcta"));
        }
        limitador.limpiar(clave);
        return ResponseEntity.noContent().build();
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase();
    }
}
