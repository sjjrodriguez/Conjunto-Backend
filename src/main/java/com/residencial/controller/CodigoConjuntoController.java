package com.residencial.controller;

import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.dto.MensajeError;
import com.residencial.model.Conjunto;
import com.residencial.repository.ConjuntoRepository;
import com.residencial.security.CodigoConjunto;
import com.residencial.security.LimitadorLogin;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * P9 bloque 2: el código diario del conjunto. La app de residentes lo manda para recibir el pase
 * del conjunto (público, con límite de intentos); la administración y la portería ven el de hoy
 * para dárselo en persona a quien lo pida. La plataforma lo ve en el detalle de cada conjunto.
 */
@RestController
public class CodigoConjuntoController {

    public record Intento(String codigo) {}
    public record Pase(Long conjuntoId, String pase) {}
    public record CodigoDelDia(String codigo, LocalDateTime cambiaEn) {}

    private final ConjuntoRepository conjuntos;
    private final CodigoConjunto codigos;
    private final LimitadorLogin limitador;

    public CodigoConjuntoController(ConjuntoRepository conjuntos, CodigoConjunto codigos, LimitadorLogin limitador) {
        this.conjuntos = conjuntos;
        this.codigos = codigos;
        this.limitador = limitador;
    }

    @PostMapping("/api/conjuntos/{id}/codigo")
    public ResponseEntity<?> verificar(@PathVariable long id, @RequestBody Intento intento, HttpServletRequest http) {
        // Por celular (IP) y conjunto: 6 dígitos no se pueden adivinar con unos pocos intentos
        String clave = http.getRemoteAddr() + "|codigo-conjunto|" + id;
        limitador.verificar(clave);
        Conjunto c = conjuntos.findById(id).filter(Conjunto::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El conjunto no existe"));
        if (!codigos.valido(c, intento.codigo())) {
            limitador.registrarFallo(clave);
            return ResponseEntity.badRequest().body(new MensajeError("El código no es correcto. Pídelo de nuevo en la portería"));
        }
        limitador.limpiar(clave);
        return ResponseEntity.ok(new Pase(c.getId(), codigos.emitirPase(c)));
    }

    /** Administración y portería (SecurityConfig): el código de hoy de su conjunto. */
    @GetMapping("/api/codigo-conjunto")
    public CodigoDelDia hoy() {
        Conjunto c = conjuntos.findById(ConjuntoContexto.actual())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El conjunto no existe"));
        return new CodigoDelDia(codigos.codigoDeHoy(c), codigos.cambiaEn());
    }
}
