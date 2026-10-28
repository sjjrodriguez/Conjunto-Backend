package com.residencial.service;

import com.residencial.dto.LoginResponse;
import com.residencial.model.Administrador;
import com.residencial.model.CredencialDispositivo;
import com.residencial.model.Residente;
import com.residencial.repository.AdministradorRepository;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.CredencialDispositivoRepository;
import com.residencial.repository.ResidenteRepository;
import com.residencial.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Entrar con huella o rostro sin que el celular guarde la contraseña (P6 bloque 4). Al activarla,
 * el servidor entrega una credencial aleatoria que solo sirve en ese celular y en esa app; aquí
 * se guarda únicamente su SHA-256. Cambiar la contraseña o desactivar la huella la revoca.
 */
@Service
public class BiometriaService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final CredencialDispositivoRepository credenciales;
    private final AdministradorRepository admins;
    private final ResidenteRepository residentes;
    private final ApartamentoRepository apartamentos;
    private final TokenService tokens;
    private final ZoneId zona;

    public BiometriaService(CredencialDispositivoRepository credenciales, AdministradorRepository admins,
                            ResidenteRepository residentes, ApartamentoRepository apartamentos, TokenService tokens,
                            @Value("${app.zona-horaria:America/Bogota}") String zona) {
        this.credenciales = credenciales;
        this.admins = admins;
        this.residentes = residentes;
        this.apartamentos = apartamentos;
        this.tokens = tokens;
        this.zona = ZoneId.of(zona);
    }

    /** Crea (o reemplaza) la credencial de [sujeto] en ese celular; se entrega una sola vez. */
    @Transactional
    public String activar(String sujeto, String dispositivoId, String tipoApp) {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String credencial = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        CredencialDispositivo c = credenciales.findBySujetoAndDispositivoIdAndTipoApp(sujeto, dispositivoId, tipoApp)
                .orElseGet(CredencialDispositivo::new);
        c.setSujeto(sujeto);
        c.setDispositivoId(dispositivoId);
        c.setTipoApp(tipoApp);
        c.setHashCredencial(hash(credencial));
        c.setCreadaEn(LocalDateTime.now(zona));
        credenciales.save(c);
        return credencial;
    }

    /** Una sesión nueva si la credencial vale y la cuenta sigue pudiendo entrar; vacío si no. */
    @Transactional
    public Optional<LoginResponse> entrar(String dispositivoId, String credencial, String tipoApp) {
        if (dispositivoId == null || credencial == null || credencial.isBlank()) return Optional.empty();
        Optional<CredencialDispositivo> guardada = credenciales
                .findByHashCredencialAndDispositivoIdAndTipoApp(hash(credencial), dispositivoId, tipoApp);
        if (guardada.isEmpty()) return Optional.empty();
        CredencialDispositivo c = guardada.get();
        Optional<LoginResponse> sesion = sesionDe(c.getSujeto());
        if (sesion.isEmpty()) {
            credenciales.delete(c);
            return Optional.empty();
        }
        c.setUltimoUso(LocalDateTime.now(zona));
        return sesion;
    }

    @Transactional
    public void quitar(String sujeto, String dispositivoId, String tipoApp) {
        credenciales.findBySujetoAndDispositivoIdAndTipoApp(sujeto, dispositivoId, tipoApp).ifPresent(credenciales::delete);
    }

    /** Cambió la contraseña: ninguna huella anterior debe seguir entrando. */
    @Transactional
    public void revocarTodas(String sujeto) {
        credenciales.borrarDe(sujeto);
    }

    private Optional<LoginResponse> sesionDe(String sujeto) {
        int i = sujeto.indexOf(':');
        if (i < 0) return Optional.empty();
        Long id;
        try {
            id = Long.valueOf(sujeto.substring(i + 1));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (sujeto.startsWith("admin:")) {
            return admins.findById(id).filter(Administrador::isActivo).map(a -> {
                LoginResponse r = tokens.paraAdmin(a);
                if (Administrador.ADMIN.equals(a.getRol())) r.setPrincipal(admins.esPrincipal(a));
                return r;
            });
        }
        if (sujeto.startsWith("residente:")) {
            return residentes.findById(id).filter(Residente::puedeEntrar).map(tokens::paraResidente);
        }
        if (sujeto.startsWith("apartamento:")) return apartamentos.findById(id).map(tokens::paraApartamento);
        return Optional.empty();
    }

    private static String hash(String credencial) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(credencial.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
