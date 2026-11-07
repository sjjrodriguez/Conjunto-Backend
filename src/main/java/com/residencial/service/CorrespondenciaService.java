package com.residencial.service;

import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Correspondencia;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.CorrespondenciaRepository;
import com.residencial.security.AccesoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * P9 bloque 3: la correspondencia de la portería. La portería (o la administración) registra lo
 * que llega para un apartamento, o un recibo público para todos, y al apartamento le llega el
 * aviso. Cuando lo recogen se marca entregada con el nombre de quien lo recogió.
 */
@Service
@Transactional
public class CorrespondenciaService {

    static final int DIAS_MAX = 90;

    public record Item(Long id, Long apartamentoId, String torre, String apto, String tipo, String remitente, String nota,
                       String estado, LocalDateTime recibidaEn, String guardia, LocalDateTime entregadaEn,
                       String entregadaA, String entregadaPor,
                       // P10 bloque 7: foto del paquete que tomó la portería (se ve con GET /{id}/foto)
                       boolean tieneFoto) {}

    public record Nueva(String torre, String apto, String tipo, String remitente, String nota, String fotoId) {}

    public record NuevoReciboParaTodos(String remitente, String nota) {}

    public record Lote(String lote, int creadas) {}

    public record Entrega(String recibidaPor) {}

    private final CorrespondenciaRepository repo;
    private final ApartamentoRepository apartamentos;
    private final NotificacionService notificaciones;
    private final GuardiaService guardias;
    private final AccesoService acceso;
    private final ZoneId zona;
    private final FotosPorteria fotos;

    public CorrespondenciaService(CorrespondenciaRepository repo, ApartamentoRepository apartamentos,
                                  NotificacionService notificaciones, GuardiaService guardias, AccesoService acceso,
                                  @Value("${app.zona-horaria:America/Bogota}") String zona, FotosPorteria fotos) {
        this.fotos = fotos;
        this.repo = repo;
        this.apartamentos = apartamentos;
        this.notificaciones = notificaciones;
        this.guardias = guardias;
        this.acceso = acceso;
        this.zona = ZoneId.of(zona);
    }

    public Item registrar(Nueva n) {
        String tipo = n.tipo() == null || n.tipo().isBlank() ? Correspondencia.PAQUETE : n.tipo().trim().toUpperCase(Locale.ROOT);
        if (!Correspondencia.TIPOS.contains(tipo)) throw invalido("El tipo debe ser PAQUETE, CARTA, DOCUMENTO o RECIBO");
        List<Apartamento> encontrados = n.torre() == null || n.apto() == null ? List.of()
                : apartamentos.findByTorreAndApto(n.torre().trim(), n.apto().trim());
        if (encontrados.isEmpty()) throw new RecursoNoEncontradoException("No existe ese apartamento");
        Correspondencia c = nueva(encontrados.get(0), tipo, validar(n.remitente(), 150, "El remitente"), validar(n.nota(), 300, "La nota"), null);
        c.setFotoRuta(fotos.rutaDe(n.fotoId()));
        c = repo.save(c);
        notificaciones.avisarCorrespondencia(c);
        return aItem(c);
    }

    /** Un recibo público (agua, luz, gas) para cada apartamento del conjunto, con el mismo lote. */
    public Lote reciboParaTodos(NuevoReciboParaTodos n) {
        String remitente = validar(n.remitente(), 150, "La empresa");
        if (remitente == null) throw invalido("Escribe de qué empresa es el recibo");
        String nota = validar(n.nota(), 300, "La nota");
        List<Apartamento> todos = apartamentos.findAll();
        if (todos.isEmpty()) throw invalido("El conjunto no tiene apartamentos");
        String lote = UUID.randomUUID().toString();
        for (Apartamento a : todos) {
            Correspondencia c = repo.save(nueva(a, Correspondencia.RECIBO, remitente, nota, lote));
            notificaciones.avisarCorrespondencia(c);
        }
        return new Lote(lote, todos.size());
    }

    /** Lo pendiente (lo más viejo primero) o lo entregado en los últimos [dias]; [q] busca apartamento o remitente. */
    @Transactional(readOnly = true)
    public List<Item> listar(String estado, String q, Integer dias) {
        boolean entregadas = Correspondencia.ENTREGADA.equalsIgnoreCase(estado == null ? "" : estado.trim());
        int d = dias == null ? 7 : Math.max(1, Math.min(DIAS_MAX, dias));
        List<Correspondencia> lista = entregadas
                ? repo.entregadasDesde(LocalDateTime.now(zona).toLocalDate().minusDays(d - 1L).atStartOfDay())
                : repo.pendientes();
        String buscado = q == null ? "" : PorteriaTableroService.sinTildes(q.trim());
        return lista.stream().filter(c -> buscado.isEmpty() || coincide(c, buscado)).map(this::aItem).toList();
    }

    @Transactional(readOnly = true)
    public long pendientes() {
        return repo.countByEstado(Correspondencia.PENDIENTE);
    }

    public Item entregar(Long id, Entrega e) {
        String quien = validar(e == null ? null : e.recibidaPor(), 150, "El nombre");
        if (quien == null) throw invalido("Escribe el nombre de quien recoge");
        Correspondencia c = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No se encontró esa correspondencia"));
        if (Correspondencia.ENTREGADA.equals(c.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya se había entregado a " + c.getEntregadaA());
        }
        c.setEstado(Correspondencia.ENTREGADA);
        c.setEntregadaEn(LocalDateTime.now(zona));
        c.setEntregadaA(quien);
        c.setEntregadaPor(guardias.delTurno().map(g -> g.getNombre())
                .orElse(acceso.tokenActual().map(t -> t.getClaimAsString("usuario") != null ? t.getClaimAsString("usuario") : t.getSubject()).orElse(null)));
        c = repo.save(c);
        notificaciones.avisarCorrespondenciaEntregada(c);
        return aItem(c);
    }

    /** La del apartamento de la sesión: lo pendiente y lo de los últimos 60 días. */
    @Transactional(readOnly = true)
    public List<Item> delApartamento() {
        Long apto = acceso.apartamentoActual().orElseThrow(() -> new AccessDeniedException("Sin apartamento"));
        return repo.delApartamento(apto, LocalDateTime.now(zona).minusDays(60)).stream().map(this::aItem).toList();
    }

    /** P11: el residente quita de su lista algo ya entregado. Lo de otro apartamento no existe para él. */
    public void ocultarDelApartamento(Long id) {
        Long apto = acceso.apartamentoActual().orElseThrow(() -> new AccessDeniedException("Sin apartamento"));
        Correspondencia c = repo.findById(id).filter(x -> x.getApartamento().getId().equals(apto))
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró esa correspondencia"));
        if (!Correspondencia.ENTREGADA.equals(c.getEstado())) throw invalido("Solo se puede quitar lo que ya recogiste");
        c.setOcultaResidente(true);
        repo.save(c);
    }

    /** P11: la portería quita de su lista algo ya entregado; el registro se conserva. */
    public void ocultarDeLaPorteria(Long id) {
        Correspondencia c = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No se encontró esa correspondencia"));
        if (!Correspondencia.ENTREGADA.equals(c.getEstado())) throw invalido("Solo se puede quitar lo que ya se entregó");
        c.setOcultaPorteria(true);
        repo.save(c);
    }

    /** P10 bloque 7: la foto del paquete, para la portería o el apartamento al que llegó. */
    @Transactional(readOnly = true)
    public FotosPorteria.Enlace foto(Long id) {
        Correspondencia c = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No se encontró esa correspondencia"));
        String rol = acceso.tokenActual().map(t -> t.getClaimAsString(com.residencial.security.TokenService.CLAIM_ROL)).orElse(null);
        boolean personal = com.residencial.security.TokenService.ROL_PORTERIA.equals(rol) || com.residencial.security.TokenService.ROL_ADMIN.equals(rol);
        if (!personal) {
            Long suyo = acceso.apartamentoActual().orElse(null);
            if (suyo == null || !suyo.equals(c.getApartamento().getId())) throw new AccessDeniedException("Esa correspondencia no es de tu apartamento");
        }
        if (c.getFotoRuta() == null) throw new RecursoNoEncontradoException("Esta correspondencia no tiene foto");
        return new FotosPorteria.Enlace(fotos.enlace(c.getFotoRuta()));
    }

    private Correspondencia nueva(Apartamento a, String tipo, String remitente, String nota, String lote) {
        Correspondencia c = new Correspondencia();
        c.setApartamento(a);
        c.setTipo(tipo);
        c.setRemitente(remitente);
        c.setNota(nota);
        c.setLote(lote);
        c.setRecibidaEn(LocalDateTime.now(zona));
        c.setRegistradaPor(acceso.tokenActual().map(Jwt::getSubject).orElse(null));
        c.setGuardiaNombre(guardias.delTurno().map(g -> g.getNombre()).orElse(null));
        return c;
    }

    private Item aItem(Correspondencia c) {
        Apartamento a = c.getApartamento();
        return new Item(c.getId(), a.getId(), a.getTorre(), a.getApto(), c.getTipo(), c.getRemitente(), c.getNota(),
                c.getEstado(), c.getRecibidaEn(), c.getGuardiaNombre(), c.getEntregadaEn(), c.getEntregadaA(), c.getEntregadaPor(),
                c.getFotoRuta() != null);
    }

    private static boolean coincide(Correspondencia c, String q) {
        Apartamento a = c.getApartamento();
        return PorteriaTableroService.sinTildes(a.getTorre() + "-" + a.getApto()).contains(q)
                || PorteriaTableroService.sinTildes(a.getApto()).equals(q)
                || PorteriaTableroService.sinTildes(c.getRemitente()).contains(q)
                || PorteriaTableroService.sinTildes(c.getEntregadaA()).contains(q);
    }

    private static String validar(String s, int max, String campo) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.length() > max) throw invalido(campo + " es muy largo (máximo " + max + " caracteres)");
        return t;
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
