package com.residencial.controller;

import com.residencial.dto.MensajeError;
import com.residencial.security.AccesoService;
import com.residencial.security.LimitadorLogin;
import com.residencial.service.BiometriaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/** Entrar con huella o rostro con una credencial opaca por celular (P6 bloque 4). */
@RestController
@RequestMapping("/api/auth/biometria")
public class BiometriaController {

    public record Activar(String dispositivoId) {}
    public record Activada(String credencial) {}
    public record Entrar(String dispositivoId, String credencial) {}

    private final BiometriaService servicio;
    private final AccesoService acceso;
    private final LimitadorLogin limitador;

    public BiometriaController(BiometriaService servicio, AccesoService acceso, LimitadorLogin limitador) {
        this.servicio = servicio;
        this.acceso = acceso;
        this.limitador = limitador;
    }

    /** Con la sesión abierta: entrega la credencial de este celular (se muestra una sola vez). */
    @PostMapping("/activar")
    public Activada activar(@RequestBody Activar cuerpo, @RequestHeader(value = "X-App-Tipo", required = false) String app) {
        Jwt sesion = sesion();
        return new Activada(servicio.activar(sesion.getSubject(), dispositivo(cuerpo.dispositivoId()), app(app)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> entrar(@RequestBody Entrar cuerpo, HttpServletRequest http,
                                    @RequestHeader(value = "X-App-Tipo", required = false) String app) {
        String clave = http.getRemoteAddr() + "|bio|" + (cuerpo.dispositivoId() == null ? "" : cuerpo.dispositivoId());
        limitador.verificar(clave);
        var sesion = servicio.entrar(cuerpo.dispositivoId(), cuerpo.credencial(), app(app));
        if (sesion.isPresent()) {
            limitador.limpiar(clave);
            return ResponseEntity.ok(sesion.get());
        }
        limitador.registrarFallo(clave);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MensajeError("Vuelve a iniciar sesión con tu contraseña"));
    }

    @DeleteMapping
    public ResponseEntity<Void> quitar(@RequestParam String dispositivoId,
                                       @RequestHeader(value = "X-App-Tipo", required = false) String app) {
        servicio.quitar(sesion().getSubject(), dispositivo(dispositivoId), app(app));
        return ResponseEntity.noContent().build();
    }

    private Jwt sesion() {
        return acceso.tokenActual()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión"));
    }

    private static String dispositivo(String id) {
        if (id == null || id.isBlank() || id.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el identificador del celular");
        }
        return id.trim();
    }

    static String app(String valor) {
        return valor == null || valor.isBlank() ? "RESIDENTE" : valor.trim().toUpperCase(Locale.ROOT);
    }
}
