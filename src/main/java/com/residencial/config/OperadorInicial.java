package com.residencial.config;

import com.residencial.model.OperadorPlataforma;
import com.residencial.repository.OperadorPlataformaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * P7 bloque 3: crea el primer operador de la plataforma con PLATAFORMA_USUARIO y
 * PLATAFORMA_CONTRASENA si todavía no existe. Si ya existe no lo toca: cambiar la variable no
 * cambia la contraseña (se cambia desde la app).
 */
@Component
public class OperadorInicial implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OperadorInicial.class);

    private final OperadorPlataformaRepository operadores;
    private final PasswordEncoder encoder;
    private final String usuario;
    private final String contrasena;

    public OperadorInicial(OperadorPlataformaRepository operadores, PasswordEncoder encoder,
                           @Value("${app.plataforma.usuario:}") String usuario,
                           @Value("${app.plataforma.contrasena:}") String contrasena) {
        this.operadores = operadores;
        this.encoder = encoder;
        this.usuario = usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
        this.contrasena = contrasena == null ? "" : contrasena;
    }

    @Override
    public void run(String... args) {
        if (usuario.isEmpty() || contrasena.isEmpty()) return;
        if (operadores.findByUsuarioIgnoreCase(usuario).isPresent()) return;
        if (contrasena.length() < 10) {
            log.warn("PLATAFORMA_CONTRASENA debe tener al menos 10 caracteres; no se creó el operador");
            return;
        }
        operadores.save(new OperadorPlataforma(usuario, encoder.encode(contrasena), "Plataforma"));
        log.info("Operador de la plataforma creado: {}", usuario);
    }
}
