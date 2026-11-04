package com.residencial.security;

import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.model.Conjunto;
import com.residencial.model.DispositivoCuenta;
import com.residencial.repository.ConjuntoRepository;
import com.residencial.repository.DispositivoCuentaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * P7 bloque 5: una sesión por celular. Cada login de una cuenta de la administración o de un
 * residente abre la sesión de ese celular (fila de dispositivo_cuenta) con un id aleatorio que va
 * en el token (claim sid); cerrarla invalida solo los tokens de ese celular. Si la cuenta pasa del
 * límite del conjunto, se cierra la sesión abierta hace más tiempo.
 *
 * Que una sesión siga abierta se guarda 30 segundos en memoria (como SesionVersion): con una
 * sola instancia en Render, cerrarla aquí la olvida de inmediato.
 */
@Component
public class SesionesDispositivo {

    /** El id del celular (el mismo de la huella) y su marca y modelo; las manda la app en cada petición. */
    public static final String CABECERA = "X-Dispositivo";
    public static final String CABECERA_NOMBRE = "X-Dispositivo-Nombre";
    public static final int MAX_POR_DEFECTO = 3;

    private static final long VIGENCIA_MS = 30_000;
    private static final SecureRandom ALEATORIO = new SecureRandom();
    // La sesión que se está abriendo, para que TokenService la ponga en el token que emite
    private static final ThreadLocal<String> EN_CURSO = new ThreadLocal<>();

    /** Lo que pasó al abrir la sesión: su id y el celular que se cerró por el límite (o null). */
    public record Apertura(String sesion, String cerradaEn) {}

    private final DispositivoCuentaRepository repo;
    private final ConjuntoRepository conjuntos;
    private final JdbcTemplate jdbc;
    private final ZoneId zona;
    private final ConcurrentHashMap<String, Long> abiertas = new ConcurrentHashMap<>();

    public SesionesDispositivo(DispositivoCuentaRepository repo, ConjuntoRepository conjuntos, JdbcTemplate jdbc,
                               @Value("${app.zona-horaria:America/Bogota}") String zona) {
        this.repo = repo;
        this.conjuntos = conjuntos;
        this.jdbc = jdbc;
        this.zona = ZoneId.of(zona);
    }

    /**
     * Solo las cuentas personales: la clave del apartamento la comparte toda la familia y la
     * plataforma no tiene correo (su token dura 2 horas).
     */
    public static boolean aplica(String sujeto) {
        return sujeto != null && (sujeto.startsWith("admin:") || sujeto.startsWith("residente:"));
    }

    /** La sesión que se abre en esta petición; null si no se está abriendo ninguna. */
    public static String enCurso() {
        return EN_CURSO.get();
    }

    /** Emite el token de [emitir] con la sesión [sesion] (null: sin sesión de celular). */
    public static <T> T conSesion(String sesion, Supplier<T> emitir) {
        String antes = EN_CURSO.get();
        EN_CURSO.set(sesion);
        try {
            return emitir.get();
        } finally {
            if (antes == null) EN_CURSO.remove(); else EN_CURSO.set(antes);
        }
    }

    /** Si los tokens con esta sesión siguen valiendo. */
    public boolean activa(String sesion) {
        long ahora = System.currentTimeMillis();
        Long vence = abiertas.get(sesion);
        if (vence != null && vence > ahora) return true;
        // SQL directo: se consulta en cada petición nueva y no debe depender del conjunto
        Integer filas = jdbc.queryForObject("select count(*) from dispositivo_cuenta where sesion = ?", Integer.class, sesion);
        boolean activa = filas != null && filas > 0;
        if (activa) abiertas.put(sesion, ahora + VIGENCIA_MS); else abiertas.remove(sesion);
        return activa;
    }

    /** Si la cuenta ya confirmó el código en ese celular. */
    @Transactional(readOnly = true)
    public boolean verificado(String sujeto, String dispositivo) {
        return repo.findBySujetoAndDispositivo(sujeto, dispositivo).map(d -> d.getVerificadoEn() != null).orElse(false);
    }

    /**
     * Abre (o renueva) la sesión de [sujeto] en ese celular; [verificado] lo marca como verificado.
     * Si con ella la cuenta pasa del límite del conjunto, cierra las más antiguas.
     */
    @Transactional
    public Apertura abrir(String sujeto, String dispositivo, String nombre, String tipoApp, boolean verificado) {
        if (!aplica(sujeto) || dispositivo == null) return new Apertura(null, null);
        LocalDateTime ahora = LocalDateTime.now(zona);
        DispositivoCuenta d = repo.findBySujetoAndDispositivo(sujeto, dispositivo).orElseGet(() -> {
            DispositivoCuenta nuevo = new DispositivoCuenta();
            nuevo.setSujeto(sujeto);
            nuevo.setDispositivo(dispositivo);
            nuevo.setCreadoEn(ahora);
            return nuevo;
        });
        if (verificado && d.getVerificadoEn() == null) d.setVerificadoEn(ahora);
        if (nombre != null && !nombre.isBlank()) d.setNombre(recortar(nombre, 80));
        if (tipoApp != null) d.setTipoApp(recortar(tipoApp, 20));
        if (d.getSesion() != null) abiertas.remove(d.getSesion());
        d.setSesion(nuevaSesion());
        d.setSesionDesde(ahora);
        repo.save(d);

        int maximo = conjuntos.findById(ConjuntoContexto.actual()).map(Conjunto::getMaxDispositivos).orElse(MAX_POR_DEFECTO);
        List<DispositivoCuenta> otras = repo.findBySujetoAndSesionIsNotNullOrderBySesionDesdeAsc(sujeto).stream()
                .filter(o -> !o.getId().equals(d.getId())).toList();
        String cerradaEn = null;
        for (int i = 0; i < otras.size() - (maximo - 1); i++) {
            DispositivoCuenta vieja = otras.get(i);
            cerrarFila(vieja);
            if (cerradaEn == null) cerradaEn = vieja.getNombre() == null ? "otro celular" : vieja.getNombre();
        }
        return new Apertura(d.getSesion(), cerradaEn);
    }

    /** Cierra la sesión de un celular (cerrar sesión en la app); el celular sigue verificado. */
    @Transactional
    public void cerrar(String sesion) {
        if (sesion == null) return;
        repo.findBySesion(sesion).ifPresent(this::cerrarFila);
        abiertas.remove(sesion);
    }

    /** Quita el celular de la cuenta: cierra su sesión y la próxima vez pedirá código. */
    @Transactional
    public void quitar(DispositivoCuenta d) {
        if (d.getSesion() != null) abiertas.remove(d.getSesion());
        repo.delete(d);
    }

    /**
     * Cierra todas las sesiones de [sujeto] (cambio de contraseña, baja, restablecer). SQL directo:
     * se llama también desde la plataforma, que no es de ningún conjunto.
     */
    public void cerrarTodas(String sujeto) {
        jdbc.update("update dispositivo_cuenta set sesion = null, sesion_desde = null where sujeto = ?", sujeto);
        abiertas.clear();
    }

    private void cerrarFila(DispositivoCuenta d) {
        if (d.getSesion() != null) abiertas.remove(d.getSesion());
        d.setSesion(null);
        d.setSesionDesde(null);
        repo.save(d);
    }

    private static String nuevaSesion() {
        byte[] bytes = new byte[24];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String recortar(String texto, int largo) {
        String t = texto.trim();
        return t.length() > largo ? t.substring(0, largo) : t;
    }
}
