package com.residencial.controller;

import com.residencial.dto.LoginDTO;
import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/admin/login")
    public ResponseEntity<?> loginAdmin(@RequestBody LoginDTO.AdminLoginRequest request) {
        Administrador admin = authService.loginAdmin(request.getUsuario(), request.getContrasena());
        if (admin != null) {
            return ResponseEntity.ok(admin);
        }
        return ResponseEntity.status(401).body("Credenciales incorrectas");
    }

    @PostMapping("/apartamento/login")
    public ResponseEntity<?> loginApartamento(@RequestBody LoginDTO.ApartamentoLoginRequest request) {
        Apartamento apartamento = authService.loginApartamento(
                request.getTorre(), request.getApto(), request.getContrasena());
        if (apartamento != null) {
            return ResponseEntity.ok(apartamento);
        }
        return ResponseEntity.status(401).body("Torre, apto o contraseña incorrectos");
    }
}
