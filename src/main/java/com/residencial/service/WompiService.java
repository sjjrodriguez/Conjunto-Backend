package com.residencial.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Cuota;
import com.residencial.model.TransaccionPago;
import com.residencial.repository.CuotaRepository;
import com.residencial.repository.TransaccionPagoRepository;
import com.residencial.security.AccesoArrendatario;
import com.residencial.security.AccesoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Pago en línea de una cuota con Wompi (P6 bloque 6). El servidor firma el checkout con el secreto
 * de integridad y solo da por pagada la cuota cuando el webhook llega con la firma de eventos válida.
 * La app nunca decide si se pagó: el regreso del navegador solo recarga la cartera.
 */
@Service
@Transactional
public class WompiService {

    private static final Logger log = LoggerFactory.getLogger(WompiService.class);

    public record Checkout(String url, String referencia, long montoCentavos) {}

    private final String llavePublica;
    private final String secretoIntegridad;
    private final String secretoEventos;
    private final String urlCheckout;
    private final String urlRetorno;
    private final CuotaRepository cuotas;
    private final TransaccionPagoRepository transacciones;
    private final CuotaService cuotaService;
    private final AccesoService acceso;
    private final AccesoArrendatario arrendatario;
    private final ZoneId zona;

    public WompiService(@Value("${app.wompi.llave-publica:}") String llavePublica,
                        @Value("${app.wompi.secreto-integridad:}") String secretoIntegridad,
                        @Value("${app.wompi.secreto-eventos:}") String secretoEventos,
                        @Value("${app.wompi.url-checkout:https://checkout.wompi.co/p/}") String urlCheckout,
                        @Value("${app.wompi.url-retorno:}") String urlRetorno,
                        CuotaRepository cuotas, TransaccionPagoRepository transacciones, CuotaService cuotaService,
                        AccesoService acceso, AccesoArrendatario arrendatario,
                        @Value("${app.zona-horaria:America/Bogota}") String zona) {
        this.llavePublica = llavePublica;
        this.secretoIntegridad = secretoIntegridad;
        this.secretoEventos = secretoEventos;
        this.urlCheckout = urlCheckout;
        this.urlRetorno = urlRetorno;
        this.cuotas = cuotas;
        this.transacciones = transacciones;
        this.cuotaService = cuotaService;
        this.acceso = acceso;
        this.arrendatario = arrendatario;
        this.zona = ZoneId.of(zona);
    }

    public boolean configurado() {
        return !llavePublica.isBlank() && !secretoIntegridad.isBlank();
    }

    /** Crea la transacción y devuelve la dirección firmada del checkout de Wompi. */
    public Checkout crearCheckout(Long cuotaId) {
        if (!configurado()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El pago en línea no está disponible todavía");
        Cuota c = cuotas.findById(cuotaId).orElseThrow(() -> new RecursoNoEncontradoException("Cuota no encontrada: " + cuotaId));
        Long apto = c.getApartamento().getId();
        acceso.verificarApartamento(apto);
        arrendatario.exigirPoderPagar(apto);
        if (c.pagada()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta cuota ya está pagada");
        long centavos = c.getValor().movePointRight(2).longValueExact();
        if (centavos <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuota no tiene un valor para pagar");

        TransaccionPago t = new TransaccionPago();
        t.setCuotaId(c.getId());
        t.setReferencia("MC-" + c.getId() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        t.setMontoCentavos(centavos);
        t.setCreadaEn(LocalDateTime.now(zona));
        transacciones.save(t);

        // Firma de integridad: referencia + monto + moneda + secreto
        String firma = sha256(t.getReferencia() + centavos + t.getMoneda() + secretoIntegridad);
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(urlCheckout)
                .queryParam("public-key", llavePublica)
                .queryParam("currency", t.getMoneda())
                .queryParam("amount-in-cents", centavos)
                .queryParam("reference", t.getReferencia())
                .queryParam("signature:integrity", firma);
        if (!urlRetorno.isBlank()) url.queryParam("redirect-url", urlRetorno);
        return new Checkout(url.build().encode().toUriString(), t.getReferencia(), centavos);
    }

    /**
     * Evento de Wompi. false si la firma no es válida (el controlador responde 401); true si se
     * procesó o se ignoró de forma segura. Repetir el mismo evento no cambia nada.
     */
    public boolean procesarEvento(JsonNode evento, String checksumCabecera) {
        if (secretoEventos.isBlank() || evento == null || !firmaValida(evento, checksumCabecera)) return false;
        JsonNode datos = evento.path("data").path("transaction");
        String referencia = datos.path("reference").asText("");
        String estadoProveedor = datos.path("status").asText("");
        TransaccionPago t = transacciones.findByReferencia(referencia).orElse(null);
        if (t == null) {
            log.warn("Evento de Wompi con una referencia desconocida: {}", referencia);
            return true;
        }
        // Ya aprobada: nada más puede cambiarla (Wompi reintenta y puede repetir)
        if (TransaccionPago.APROBADA.equals(t.getEstado())) return true;
        if (datos.path("amount_in_cents").asLong(-1) != t.getMontoCentavos()) {
            log.warn("El monto del evento no coincide con la transacción {}", referencia);
            return true;
        }
        t.setIdProveedor(datos.path("id").asText(null));
        t.setFirmaWebhook(evento.path("signature").path("checksum").asText(checksumCabecera));
        t.setActualizadaEn(LocalDateTime.now(zona));
        switch (estadoProveedor) {
            case "APPROVED" -> {
                t.setEstado(TransaccionPago.APROBADA);
                cuotaService.registrarPagoEnLinea(t.getCuotaId());
            }
            case "DECLINED" -> t.setEstado(TransaccionPago.RECHAZADA);
            case "VOIDED" -> t.setEstado(TransaccionPago.ANULADA);
            case "ERROR" -> t.setEstado(TransaccionPago.ERROR);
            default -> { /* PENDING: sigue creada */ }
        }
        transacciones.save(t);
        return true;
    }

    /** sha256(valores de signature.properties + timestamp + secreto de eventos) debe ser el checksum. */
    private boolean firmaValida(JsonNode evento, String checksumCabecera) {
        JsonNode firma = evento.path("signature");
        String esperado = firma.path("checksum").asText(checksumCabecera == null ? "" : checksumCabecera);
        if (esperado.isBlank() || !evento.hasNonNull("timestamp")) return false;
        StringBuilder cadena = new StringBuilder();
        JsonNode propiedades = firma.path("properties");
        if (!propiedades.isArray() || propiedades.isEmpty()) return false;
        for (JsonNode p : propiedades) {
            JsonNode valor = evento.path("data");
            for (String parte : p.asText().split("\\.")) valor = valor.path(parte);
            if (valor.isMissingNode() || valor.isNull()) return false;
            cadena.append(valor.asText());
        }
        cadena.append(evento.path("timestamp").asText()).append(secretoEventos);
        return MessageDigest.isEqual(sha256(cadena.toString()).getBytes(StandardCharsets.UTF_8),
                esperado.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
