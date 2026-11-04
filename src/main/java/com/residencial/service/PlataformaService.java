package com.residencial.service;

import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.conjunto.ConjuntosActivos;
import com.residencial.dto.PlataformaDTO;
import com.residencial.model.Administrador;
import com.residencial.model.AuditoriaPlataforma;
import com.residencial.model.Conjunto;
import com.residencial.model.OperadorPlataforma;
import com.residencial.repository.AuditoriaPlataformaRepository;
import com.residencial.repository.ConjuntoRepository;
import com.residencial.repository.OperadorPlataformaRepository;
import com.residencial.security.SesionVersion;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * P7 bloque 3: lo que hace la plataforma con los conjuntos. Crea, edita, suspende y reactiva
 * conjuntos, crea su administrador principal y le restablece la contraseña. No ve ni cambia los
 * datos de los residentes: eso lo hace la administración de cada conjunto.
 *
 * Las cuentas de la administración de otro conjunto se tocan con SQL directo: el filtro por
 * conjunto de Hibernate se fija al abrir la sesión, y así conjunto y administrador quedan en una
 * sola transacción. Todo lo que cambia queda en la auditoría.
 */
@Service
public class PlataformaService {

    /** Los 32 departamentos y Bogotá: el selector de las apps agrupa por este nombre exacto. */
    public static final List<String> DEPARTAMENTOS = List.of(
            "Amazonas", "Antioquia", "Arauca", "Atlántico", "Bogotá D.C.", "Bolívar", "Boyacá", "Caldas",
            "Caquetá", "Casanare", "Cauca", "Cesar", "Chocó", "Córdoba", "Cundinamarca", "Guainía",
            "Guaviare", "Huila", "La Guajira", "Magdalena", "Meta", "Nariño", "Norte de Santander",
            "Putumayo", "Quindío", "Risaralda", "San Andrés y Providencia", "Santander", "Sucre",
            "Tolima", "Valle del Cauca", "Vaupés", "Vichada");

    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int LARGO_TEMPORAL = 12;
    private static final int MAX_EVENTOS = 200;

    private static final String RESUMEN = """
            select c.id, c.nombre, c.departamento, c.municipio, c.direccion, c.activo, c.creado_en, c.max_dispositivos,
                   (select count(*) from apartamento a where a.conjunto_id = c.id) as apartamentos,
                   (select count(*) from administrador d where d.conjunto_id = c.id and d.activo = true) as cuentas,
                   (select d.usuario from administrador d
                     where d.conjunto_id = c.id and d.activo = true and d.rol = 'ADMIN'
                     order by d.principal desc, d.id limit 1) as principal
            from conjunto c
            """;

    private final ConjuntoRepository conjuntos;
    private final OperadorPlataformaRepository operadores;
    private final AuditoriaPlataformaRepository auditoria;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PasswordEncoder encoder;
    private final SesionVersion sesiones;
    private final ConjuntosActivos activos;
    private final BiometriaService biometria;
    private final SecureRandom azar = new SecureRandom();

    public PlataformaService(ConjuntoRepository conjuntos, OperadorPlataformaRepository operadores,
                             AuditoriaPlataformaRepository auditoria, JdbcTemplate jdbc, TransactionTemplate tx,
                             PasswordEncoder encoder, SesionVersion sesiones, ConjuntosActivos activos,
                             BiometriaService biometria) {
        this.conjuntos = conjuntos;
        this.operadores = operadores;
        this.auditoria = auditoria;
        this.jdbc = jdbc;
        this.tx = tx;
        this.encoder = encoder;
        this.sesiones = sesiones;
        this.activos = activos;
        this.biometria = biometria;
    }

    public List<PlataformaDTO.ConjuntoResumen> conjuntos() {
        return jdbc.query(RESUMEN + " order by c.activo desc, c.departamento, c.municipio, c.nombre",
                (rs, i) -> resumen(rs));
    }

    public PlataformaDTO.ConjuntoResumen conjunto(long id) {
        return jdbc.query(RESUMEN + " where c.id = ?", (rs, i) -> resumen(rs), id).stream().findFirst()
                .orElseThrow(PlataformaService::noExiste);
    }

    public List<PlataformaDTO.CuentaAdministracion> cuentas(long conjuntoId) {
        existe(conjuntoId);
        return jdbc.query("""
                select id, usuario, nombre_completo, rol, principal, activo from administrador
                where conjunto_id = ? order by activo desc, principal desc, rol, usuario
                """, (rs, i) -> new PlataformaDTO.CuentaAdministracion(rs.getLong("id"), rs.getString("usuario"),
                rs.getString("nombre_completo"), rs.getString("rol"), rs.getBoolean("principal"),
                rs.getBoolean("activo")), conjuntoId);
    }

    /** El conjunto y su administrador principal, con una contraseña temporal que se muestra una sola vez. */
    public PlataformaDTO.Acceso crear(OperadorPlataforma operador, PlataformaDTO.NuevoConjunto datos) {
        PlataformaDTO.DatosConjunto d = normalizar(datos.datos());
        String usuario = datos.usuarioPrincipal().trim().toLowerCase(Locale.ROOT);
        String temporal = temporal();
        Long id = tx.execute(estado -> {
            sinRepetir(d, null);
            Conjunto nuevo = new Conjunto(d.nombre(), d.departamento(), d.municipio(), d.direccion());
            if (d.maxDispositivos() != null) nuevo.setMaxDispositivos(d.maxDispositivos());
            Conjunto c = conjuntos.saveAndFlush(nuevo);
            jdbc.update("""
                    insert into administrador (conjunto_id, usuario, contrasena, rol, principal, nombre_completo, correo, token_version, activo)
                    values (?, ?, ?, ?, true, ?, ?, 0, true)
                    """, c.getId(), usuario, encoder.encode(temporal), Administrador.ADMIN,
                    datos.nombrePrincipal().trim(), vacioANull(datos.correoPrincipal()));
            auditoria.save(new AuditoriaPlataforma(operador, AuditoriaPlataforma.CREAR_CONJUNTO, c.getId(),
                    c.getNombre() + " (" + c.getMunicipio() + ", " + c.getDepartamento() + "); principal: " + usuario));
            return c.getId();
        });
        return new PlataformaDTO.Acceso(id, usuario, temporal);
    }

    public PlataformaDTO.ConjuntoResumen editar(OperadorPlataforma operador, long id, PlataformaDTO.DatosConjunto datos) {
        PlataformaDTO.DatosConjunto d = normalizar(datos);
        tx.executeWithoutResult(estado -> {
            Conjunto c = conjuntos.findById(id).orElseThrow(PlataformaService::noExiste);
            sinRepetir(d, id);
            String antes = c.getNombre() + " (" + c.getMunicipio() + ", " + c.getDepartamento() + ")";
            c.setNombre(d.nombre());
            c.setDepartamento(d.departamento());
            c.setMunicipio(d.municipio());
            c.setDireccion(d.direccion());
            String limite = "";
            if (d.maxDispositivos() != null && d.maxDispositivos() != c.getMaxDispositivos()) {
                limite = "; celulares por cuenta: " + c.getMaxDispositivos() + " → " + d.maxDispositivos();
                c.setMaxDispositivos(d.maxDispositivos());
            }
            conjuntos.save(c);
            auditoria.save(new AuditoriaPlataforma(operador, AuditoriaPlataforma.EDITAR_CONJUNTO, id,
                    antes + " → " + c.getNombre() + " (" + c.getMunicipio() + ", " + c.getDepartamento() + ")" + limite));
        });
        return conjunto(id);
    }

    /**
     * Suspender corta el acceso de todo el conjunto (sesiones y logins, ver FiltroConjunto) y lo
     * quita de la lista para elegir; no borra nada. Reactivar lo deja como estaba.
     */
    public PlataformaDTO.ConjuntoResumen cambiarEstado(OperadorPlataforma operador, long id, PlataformaDTO.CambioEstado cambio) {
        tx.executeWithoutResult(estado -> {
            Conjunto c = conjuntos.findById(id).orElseThrow(PlataformaService::noExiste);
            if (c.isActivo() == cambio.activo()) return;
            c.setActivo(cambio.activo());
            conjuntos.save(c);
            auditoria.save(new AuditoriaPlataforma(operador,
                    cambio.activo() ? AuditoriaPlataforma.REACTIVAR_CONJUNTO : AuditoriaPlataforma.SUSPENDER_CONJUNTO,
                    id, cambio.motivo().trim()));
        });
        activos.olvidar(id);
        return conjunto(id);
    }

    /**
     * Nueva contraseña temporal para el administrador principal (perdió el acceso o cambió la
     * persona). Sus sesiones y su huella dejan de valer. Si ninguna cuenta está marcada como
     * principal (bases anteriores a P5), lo es el administrador más antiguo y queda marcado.
     */
    public PlataformaDTO.Acceso restablecerPrincipal(OperadorPlataforma operador, long conjuntoId) {
        existe(conjuntoId);
        String temporal = temporal();
        Object[] cuenta = tx.execute(estado -> {
            Optional<Object[]> principal = jdbc.query("""
                    select id, usuario from administrador
                    where conjunto_id = ? and activo = true and rol = 'ADMIN'
                    order by principal desc, id limit 1
                    """, (rs, i) -> new Object[]{rs.getLong(1), rs.getString(2)}, conjuntoId).stream().findFirst();
            Object[] p = principal.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este conjunto no tiene un administrador activo"));
            jdbc.update("""
                    update administrador set contrasena = ?, principal = true, token_version = token_version + 1
                    where id = ? and conjunto_id = ?
                    """, encoder.encode(temporal), p[0], conjuntoId);
            auditoria.save(new AuditoriaPlataforma(operador, AuditoriaPlataforma.RESTABLECER_PRINCIPAL, conjuntoId,
                    "Contraseña temporal para " + p[1]));
            return p;
        });
        String sujeto = "admin:" + cuenta[0];
        sesiones.olvidar(sujeto);
        // P7 bloque 5: sus celulares quedan sin sesión (los tokens ya no valen por la versión)
        jdbc.update("update dispositivo_cuenta set sesion = null, sesion_desde = null where sujeto = ?", sujeto);
        // La huella es del conjunto: se borra con su filtro, en una transacción propia
        ConjuntoContexto.en(conjuntoId, () -> biometria.revocarTodas(sujeto));
        return new PlataformaDTO.Acceso(conjuntoId, (String) cuenta[1], temporal);
    }

    public List<PlataformaDTO.Evento> eventos(Long conjuntoId) {
        PageRequest pagina = PageRequest.of(0, MAX_EVENTOS);
        List<AuditoriaPlataforma> lista = conjuntoId == null
                ? auditoria.findAllByOrderByIdDesc(pagina)
                : auditoria.findByConjuntoIdOrderByIdDesc(conjuntoId, pagina);
        return lista.stream().map(e -> new PlataformaDTO.Evento(e.getId(), e.getOperadorUsuario(), e.getAccion(),
                e.getConjuntoId(), e.getDetalle(), e.getFecha())).toList();
    }

    public void registrarInicio(OperadorPlataforma operador) {
        auditoria.save(new AuditoriaPlataforma(operador, AuditoriaPlataforma.INICIO_SESION, null, null));
    }

    private PlataformaDTO.DatosConjunto normalizar(PlataformaDTO.DatosConjunto d) {
        String departamento = DEPARTAMENTOS.stream()
                .filter(x -> x.equalsIgnoreCase(d.departamento().trim()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige un departamento de la lista"));
        return new PlataformaDTO.DatosConjunto(espacios(d.nombre()), departamento, espacios(d.municipio()),
                vacioANull(d.direccion()), d.maxDispositivos());
    }

    /** Dos conjuntos con el mismo nombre en el mismo municipio no se distinguirían en el selector. */
    private void sinRepetir(PlataformaDTO.DatosConjunto d, Long excepto) {
        boolean repetido = conjuntos.findAll().stream().anyMatch(c -> !c.getId().equals(excepto)
                && c.getNombre().equalsIgnoreCase(d.nombre())
                && c.getDepartamento().equalsIgnoreCase(d.departamento())
                && c.getMunicipio().equalsIgnoreCase(d.municipio()));
        if (repetido) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay un conjunto con ese nombre en " + d.municipio());
        }
    }

    private void existe(long id) {
        if (!conjuntos.existsById(id)) throw noExiste();
    }

    private String temporal() {
        StringBuilder sb = new StringBuilder(LARGO_TEMPORAL);
        for (int i = 0; i < LARGO_TEMPORAL; i++) sb.append(LETRAS.charAt(azar.nextInt(LETRAS.length())));
        return sb.toString();
    }

    private static PlataformaDTO.ConjuntoResumen resumen(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Timestamp creado = rs.getTimestamp("creado_en");
        return new PlataformaDTO.ConjuntoResumen(rs.getLong("id"), rs.getString("nombre"), rs.getString("departamento"),
                rs.getString("municipio"), rs.getString("direccion"), rs.getBoolean("activo"),
                creado == null ? null : creado.toLocalDateTime(), rs.getLong("apartamentos"), rs.getLong("cuentas"),
                rs.getString("principal"), rs.getInt("max_dispositivos"));
    }

    private static String espacios(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : espacios(s);
    }

    private static ResponseStatusException noExiste() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "El conjunto no existe");
    }
}
