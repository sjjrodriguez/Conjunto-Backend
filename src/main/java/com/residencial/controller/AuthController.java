package com.residencial.controller;

import com.residencial.dto.LoginDTO;
import com.residencial.dto.MensajeError;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.security.LimitadorLogin;
import com.residencial.security.TokenService;
import com.residencial.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final LimitadorLogin limitador;

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

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase();
    }
}
