package com.residencial.controller;

import com.residencial.dto.CambioPropiaContrasenaRequest;
import com.residencial.dto.LoginResponse;
import com.residencial.dto.MensajeError;
import com.residencial.dto.PlataformaDTO;
import com.residencial.model.OperadorPlataforma;
import com.residencial.repository.OperadorPlataformaRepository;
import com.residencial.security.AccesoService;
import com.residencial.security.LimitadorLogin;
import com.residencial.security.SesionVersion;
import com.residencial.security.TokenService;
import com.residencial.service.PlataformaPanelService;
import com.residencial.service.PlataformaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * P7 bloque 3: la app de plataforma. Solo entra un operador de la plataforma (SecurityConfig);
 * el login es público y tiene el mismo límite de intentos que los demás.
 */
@RestController
@RequestMapping("/api/plataforma")
public class PlataformaController {

    private final PlataformaService plataforma;
    private final PlataformaPanelService panel;
    private final OperadorPlataformaRepository operadores;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final LimitadorLogin limitador;
    private final AccesoService acceso;
    private final SesionVersion sesiones;
    // Como en AuthServiceImpl: si el usuario no existe se compara igual, para no revelarlo por el tiempo
    private final String hashFicticio;

    public PlataformaController(PlataformaService plataforma, OperadorPlataformaRepository operadores,
                                PasswordEncoder encoder, TokenService tokens, LimitadorLogin limitador,
                                AccesoService acceso, SesionVersion sesiones, PlataformaPanelService panel) {
        this.plataforma = plataforma;
        this.panel = panel;
        this.operadores = operadores;
        this.encoder = encoder;
        this.tokens = tokens;
        this.limitador = limitador;
        this.acceso = acceso;
        this.sesiones = sesiones;
        this.hashFicticio = encoder.encode("contrasena-ficticia");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody PlataformaDTO.Login datos, HttpServletRequest http) {
        String usuario = datos.usuario() == null ? "" : datos.usuario().trim().toLowerCase(Locale.ROOT);
        String clave = http.getRemoteAddr() + "|plataforma|" + usuario;
        limitador.verificar(clave);
        String contrasena = datos.contrasena() == null ? "" : datos.contrasena();
        OperadorPlataforma operador = operadores.findByUsuarioIgnoreCase(usuario).filter(OperadorPlataforma::isActivo).orElse(null);
        boolean valida = encoder.matches(contrasena, operador == null ? hashFicticio : operador.getContrasena());
        if (operador == null || !valida || contrasena.isEmpty()) {
            limitador.registrarFallo(clave);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MensajeError("Credenciales incorrectas"));
        }
        limitador.limpiar(clave);
        plataforma.registrarInicio(operador);
        return ResponseEntity.ok(tokens.paraPlataforma(operador));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        sesiones.cerrar("plataforma:" + operador().getId());
        return ResponseEntity.noContent().build();
    }

    /** Igual que /api/auth/contrasena: 400 si la actual no coincide y un token nuevo si cambia. */
    @PutMapping("/contrasena")
    public ResponseEntity<?> cambiarContrasena(@Valid @RequestBody CambioPropiaContrasenaRequest datos) {
        OperadorPlataforma operador = operador();
        String clave = "contrasena|plataforma:" + operador.getId();
        limitador.verificar(clave);
        if (datos.getContrasenaActual().equals(datos.getContrasenaNueva())) {
            return ResponseEntity.badRequest().body(new MensajeError("La nueva contraseña debe ser distinta de la actual"));
        }
        if (!encoder.matches(datos.getContrasenaActual(), operador.getContrasena())) {
            limitador.registrarFallo(clave);
            return ResponseEntity.badRequest().body(new MensajeError("La contraseña actual no es correcta"));
        }
        limitador.limpiar(clave);
        operador.setContrasena(encoder.encode(datos.getContrasenaNueva()));
        operadores.save(operador);
        sesiones.revocar("plataforma:" + operador.getId());
        LoginResponse nueva = tokens.paraPlataforma(operadores.findById(operador.getId()).orElseThrow());
        return ResponseEntity.ok(nueva);
    }

    @GetMapping("/departamentos")
    public List<String> departamentos() {
        return PlataformaService.DEPARTAMENTOS;
    }

    @GetMapping("/conjuntos")
    public List<PlataformaDTO.ConjuntoResumen> conjuntos() {
        operador();
        return plataforma.conjuntos();
    }

    @GetMapping("/conjuntos/{id}")
    public PlataformaDTO.ConjuntoResumen conjunto(@PathVariable long id) {
        operador();
        return plataforma.conjunto(id);
    }

    @PostMapping("/conjuntos")
    public ResponseEntity<PlataformaDTO.Acceso> crear(@Valid @RequestBody PlataformaDTO.NuevoConjunto datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plataforma.crear(operador(), datos));
    }

    @PutMapping("/conjuntos/{id}")
    public PlataformaDTO.ConjuntoResumen editar(@PathVariable long id, @Valid @RequestBody PlataformaDTO.DatosConjunto datos) {
        return plataforma.editar(operador(), id, datos);
    }

    @PutMapping("/conjuntos/{id}/estado")
    public PlataformaDTO.ConjuntoResumen cambiarEstado(@PathVariable long id, @Valid @RequestBody PlataformaDTO.CambioEstado cambio) {
        return plataforma.cambiarEstado(operador(), id, cambio);
    }

    /** Las cuentas de la administración del conjunto (sin contraseñas), para saber quién es el principal. */
    @GetMapping("/conjuntos/{id}/administracion")
    public List<PlataformaDTO.CuentaAdministracion> cuentas(@PathVariable long id) {
        operador();
        return plataforma.cuentas(id);
    }

    @PostMapping("/conjuntos/{id}/principal/restablecer")
    public PlataformaDTO.Acceso restablecer(@PathVariable long id) {
        return plataforma.restablecerPrincipal(operador(), id);
    }

    @GetMapping("/auditoria")
    public List<PlataformaDTO.Evento> auditoria(@RequestParam(required = false) Long conjunto) {
        operador();
        return plataforma.eventos(conjunto);
    }

    /** P8 bloque 6: las cifras de todos los conjuntos, lo pendiente y lo último de la auditoría. */
    @GetMapping("/panel")
    public PlataformaDTO.Panel panel() {
        operador();
        return panel.panel();
    }

    /** P8 bloque 6: un conjunto con sus cifras, sus cuentas de administración y su auditoría. */
    @GetMapping("/conjuntos/{id}/detalle")
    public PlataformaDTO.DetalleConjunto detalle(@PathVariable long id) {
        operador();
        return panel.detalle(id);
    }

    /** P8 bloque 6: la auditoría con filtros y páginas (la de arriba sigue para la app anterior). */
    @GetMapping("/auditoria/buscar")
    public PlataformaDTO.PaginaEventos buscarAuditoria(@RequestParam(required = false) Long conjunto,
                                                      @RequestParam(required = false) String accion,
                                                      @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate desde,
                                                      @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate hasta,
                                                      @RequestParam(required = false) String q,
                                                      @RequestParam(required = false) Integer pagina) {
        operador();
        return panel.eventos(conjunto, accion, desde, hasta, q, pagina);
    }

    /** El operador de la sesión. SecurityConfig ya exige el rol; aquí se comprueba que siga activo. */
    private OperadorPlataforma operador() {
        String sujeto = acceso.tokenActual().map(t -> t.getSubject())
                .filter(s -> s != null && s.startsWith("plataforma:"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la plataforma puede hacer esto"));
        long id = Long.parseLong(sujeto.substring("plataforma:".length()));
        return operadores.findById(id).filter(OperadorPlataforma::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión"));
    }
}
