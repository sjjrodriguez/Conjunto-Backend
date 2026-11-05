package com.residencial.security;

import com.residencial.model.Conjunto;
import com.residencial.repository.ConjuntoRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;

/**
 * P9 bloque 2: el código del conjunto. Cada conjunto tiene un secreto; con él y la fecha de
 * Colombia sale un código de 6 dígitos que cambia a medianoche (como los códigos de un banco,
 * pero diario). La portería y la administración se lo dan en persona a los residentes.
 *
 * Quien pone el código correcto en la app de residentes recibe un pase para ese conjunto, que
 * su celular guarda; el login y el registro de residentes lo exigen (cabecera X-Pase-Conjunto)
 * cuando app.conjunto.exigir-codigo=true. Mientras está en false (las apps anteriores no lo
 * mandan) se acepta sin pase. Cambiar el secreto del conjunto invalida todos sus pases.
 */
@Component
public class CodigoConjunto {

    public static final String CABECERA = "X-Pase-Conjunto";
    /** Pasada la medianoche, el código de ayer sirve unos minutos más (quien lo acaba de recibir). */
    static final int MINUTOS_GRACIA = 15;

    private final ConjuntoRepository conjuntos;
    private final byte[] llave;
    private final boolean exigir;
    private final ZoneId zona;
    private final SecureRandom azar = new SecureRandom();

    public CodigoConjunto(ConjuntoRepository conjuntos, @Value("${app.jwt.secret}") String secreto,
                          @Value("${app.conjunto.exigir-codigo:false}") boolean exigir,
                          @Value("${app.zona-horaria:America/Bogota}") String zona) {
        this.conjuntos = conjuntos;
        this.llave = ("pase-conjunto|" + secreto).getBytes(StandardCharsets.UTF_8);
        this.exigir = exigir;
        this.zona = ZoneId.of(zona);
    }

    /** El código de ese día. */
    public String codigo(Conjunto c, LocalDate dia) {
        byte[] h = hmac(c.getCodigoSecreto().getBytes(StandardCharsets.UTF_8), c.getId() + "|" + dia);
        int n = ByteBuffer.wrap(h, 0, 4).getInt() & 0x7FFF_FFFF;
        return String.format("%06d", n % 1_000_000);
    }

    public String codigoDeHoy(Conjunto c) {
        return codigo(c, hoy());
    }

    /** Cuándo cambia el de hoy: a la medianoche de Colombia. */
    public LocalDateTime cambiaEn() {
        return hoy().plusDays(1).atStartOfDay();
    }

    /** El de hoy o, en los primeros minutos del día, el de ayer. */
    public boolean valido(Conjunto c, String intento) {
        if (intento == null) return false;
        String limpio = intento.replaceAll("\\s", "");
        if (!limpio.matches("\\d{6}")) return false;
        LocalDateTime ahora = LocalDateTime.now(zona);
        if (iguales(limpio, codigo(c, ahora.toLocalDate()))) return true;
        return ahora.toLocalTime().isBefore(java.time.LocalTime.of(0, MINUTOS_GRACIA))
                && iguales(limpio, codigo(c, ahora.toLocalDate().minusDays(1)));
    }

    /** Un pase nuevo para ese conjunto: un número al azar y su firma. */
    public String emitirPase(Conjunto c) {
        byte[] nonce = new byte[12];
        azar.nextBytes(nonce);
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return n + "." + firma(c, n);
    }

    public boolean paseValido(Conjunto c, String pase) {
        if (pase == null) return false;
        int punto = pase.indexOf('.');
        if (punto <= 0) return false;
        return iguales(pase.substring(punto + 1), firma(c, pase.substring(0, punto)));
    }

    /**
     * Login y registro de residentes: sin un pase válido para el conjunto de la petición responde
     * 428 (la app abre la pantalla del código). No hace nada si aún no se exige.
     */
    public void exigirPase(HttpServletRequest http, Long conjuntoId) {
        if (!exigir) return;
        Conjunto c = conjuntoId == null ? null : conjuntos.findById(conjuntoId).orElse(null);
        if (c == null || !paseValido(c, http.getHeader(CABECERA))) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Pide en la portería el código del conjunto e ingrésalo en la app");
        }
    }

    public boolean exige() {
        return exigir;
    }

    private String firma(Conjunto c, String nonce) {
        byte[] h = hmac(llave, c.getId() + "|" + c.getCodigoSecreto() + "|" + nonce);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
    }

    private LocalDate hoy() {
        return LocalDate.now(zona);
    }

    private static byte[] hmac(byte[] llave, String mensaje) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(llave, "HmacSHA256"));
            return mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el código del conjunto", e);
        }
    }

    /** Comparación en tiempo constante: el tiempo de respuesta no dice cuántos dígitos acertó. */
    private static boolean iguales(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** 64 caracteres hexadecimales al azar, para un conjunto nuevo. */
    public static String nuevoSecreto() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
