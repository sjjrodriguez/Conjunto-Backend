package com.residencial.controller;

import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.model.Conjunto;
import com.residencial.repository.ConjuntoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

/**
 * P10: el teléfono de la portería del conjunto. Cualquiera con sesión lo lee (la alerta de visita
 * del residente ofrece llamar); solo la administración lo cambia (SecurityConfig).
 */
@RestController
@RequestMapping("/api/telefono-porteria")
public class TelefonoPorteriaController {

    public record Telefono(String telefono) {}

    /** Números, espacios, guiones, paréntesis y un + inicial; de 7 a 20 caracteres. */
    private static final Pattern VALIDO = Pattern.compile("^\\+?[0-9 ()-]{7,20}$");

    private final ConjuntoRepository conjuntos;

    public TelefonoPorteriaController(ConjuntoRepository conjuntos) {
        this.conjuntos = conjuntos;
    }

    @GetMapping
    public Telefono leer() {
        return new Telefono(conjunto().getTelefonoPorteria());
    }

    @PutMapping
    @Transactional
    public Telefono cambiar(@RequestBody Telefono nuevo) {
        String t = nuevo.telefono() == null ? "" : nuevo.telefono().trim();
        if (!t.isEmpty() && !VALIDO.matcher(t).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un teléfono válido (solo números)");
        }
        Conjunto c = conjunto();
        c.setTelefonoPorteria(t.isEmpty() ? null : t);
        conjuntos.save(c);
        return new Telefono(c.getTelefonoPorteria());
    }

    private Conjunto conjunto() {
        return conjuntos.findById(ConjuntoContexto.actual())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El conjunto no existe"));
    }
}
