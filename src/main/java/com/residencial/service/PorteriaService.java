package com.residencial.service;

import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Parqueadero;
import com.residencial.model.Residente;
import com.residencial.model.VisitaPorteria;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ParqueaderoRepository;
import com.residencial.repository.ResidenteRepository;
import com.residencial.repository.VisitaPorteriaRepository;
import com.residencial.security.AccesoService;
import com.residencial.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Portería (P6 bloque 5): el portero registra una visita, al apartamento le llega el aviso con
 * prioridad alta y tiene 5 minutos (P10; antes 2) para autorizarla o rechazarla; si no responde, expira.
 * Si el residente contesta por teléfono, el portero registra su respuesta ("por llamada").
 */
@Service
@Transactional
public class PorteriaService {

    public record Visita(Long id, Long apartamentoId, String torre, String apto, String visitante, String documento,
                         String placa, String motivo, String estado, LocalDateTime creadaEn, LocalDateTime venceEn,
                         LocalDateTime respondidaEn, String canal, String telefono, String guardia,
                         // P8 bloque 4: a pie o en vehículo, el parqueadero de invitados y cuándo salió
                         String tipoIngreso, Long parqueaderoId, String parqueadero, LocalDateTime salidaEn,
                         // P10 bloque 7: si hay foto del visitante (fotoUrl solo para el residente que la responde
                         // y al registrarla), hasta cuándo está abierto el chat y cuántos mensajes escribió el residente
                         boolean tieneFoto, String fotoUrl, LocalDateTime chatHasta, long mensajesResidente) {}

    /** P10 bloque 7: un mensaje del chat de una visita rechazada. */
    public record MensajeChat(Long id, boolean deResidente, String autor, String texto, LocalDateTime creadoEn) {}

    /** P10 bloque 7: el chat de una visita: abierto mientras no pasen {@link VisitaPorteria#MINUTOS_CHAT} minutos. */
    public record Chat(Long visitaId, String visitante, String torre, String apto, boolean abierto,
                       LocalDateTime cierraEn, List<MensajeChat> mensajes) {}

    public record NuevoMensaje(String texto) {}

    /**
     * tipoIngreso: PEATONAL (por defecto) o VEHICULO. Con vehículo la placa es obligatoria y, si el
     * conjunto tiene parqueaderos de invitados, ocupa uno: parqueaderoId, o el primero libre.
     */
    public record Nueva(String torre, String apto, String visitante, String documento, String placa, String motivo,
                        String tipoIngreso, Long parqueaderoId, String fotoId) {}

    /** P8 bloque 4: un parqueadero de invitados y, si está ocupado, la visita que lo ocupa. */
    public record ParqueaderoInvitados(Long id, String numero, boolean libre, Long visitaId, String visitante,
                                       String placa, String torre, String apto, String estado, LocalDateTime desde) {}

    private final VisitaPorteriaRepository visitas;
    private final ApartamentoRepository apartamentos;
    private final ResidenteRepository residentes;
    private final NotificacionService notificaciones;
    private final AccesoService acceso;
    private final GuardiaService guardias;
    private final ParqueaderoRepository parqueaderos;
    private final ZoneId zona;
    private final FotosPorteria fotos;
    private final com.residencial.repository.MensajeVisitaRepository mensajes;
    private final org.springframework.context.ApplicationEventPublisher eventos;
    private final com.residencial.repository.VisitanteFrecuenteRepository frecuentes;

    public PorteriaService(VisitaPorteriaRepository visitas, ApartamentoRepository apartamentos,
                           ResidenteRepository residentes, NotificacionService notificaciones, AccesoService acceso,
                           GuardiaService guardias, ParqueaderoRepository parqueaderos,
                           @Value("${app.zona-horaria:America/Bogota}") String zona, FotosPorteria fotos,
                           com.residencial.repository.MensajeVisitaRepository mensajes,
                           org.springframework.context.ApplicationEventPublisher eventos,
                           com.residencial.repository.VisitanteFrecuenteRepository frecuentes) {
        this.frecuentes = frecuentes;
        this.fotos = fotos;
        this.mensajes = mensajes;
        this.eventos = eventos;
        this.guardias = guardias;
        this.parqueaderos = parqueaderos;
        this.visitas = visitas;
        this.apartamentos = apartamentos;
        this.residentes = residentes;
        this.notificaciones = notificaciones;
        this.acceso = acceso;
        this.zona = ZoneId.of(zona);
    }

    public Visita registrar(Nueva n) {
        String nombre = limpio(n.visitante());
        if (nombre == null) throw invalido("Escribe el nombre del visitante");
        if (nombre.length() > 150) throw invalido("El nombre del visitante es muy largo");
        if (largo(n.documento()) > 40) throw invalido("El documento es muy largo (máximo 40 caracteres)");
        if (largo(n.placa()) > 20) throw invalido("La placa es muy larga (máximo 20 caracteres)");
        if (largo(n.motivo()) > 200) throw invalido("El motivo es muy largo (máximo 200 caracteres)");
        List<Apartamento> encontrados = n.torre() == null || n.apto() == null ? List.of()
                : apartamentos.findByTorreAndApto(n.torre().trim(), n.apto().trim());
        if (encontrados.isEmpty()) throw new RecursoNoEncontradoException("No existe ese apartamento");
        String tipo = limpio(n.tipoIngreso()) == null ? VisitaPorteria.PEATONAL : n.tipoIngreso().trim().toUpperCase(Locale.ROOT);
        if (!tipo.equals(VisitaPorteria.PEATONAL) && !tipo.equals(VisitaPorteria.VEHICULO)) {
            throw invalido("El ingreso debe ser PEATONAL o VEHICULO");
        }
        String placa = limpio(n.placa());
        if (placa != null) placa = placa.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (tipo.equals(VisitaPorteria.VEHICULO) && placa == null) throw invalido("Escribe la placa del vehículo");
        LocalDateTime ahora = LocalDateTime.now(zona);
        visitas.expirar(ahora);
        VisitaPorteria v = new VisitaPorteria();
        v.setApartamento(encontrados.get(0));
        v.setVisitanteNombre(nombre);
        v.setVisitanteDocumento(limpio(n.documento()));
        v.setPlaca(placa);
        v.setTipoIngreso(tipo);
        if (tipo.equals(VisitaPorteria.VEHICULO)) v.setParqueadero(parqueaderoParaVisita(n.parqueaderoId()));
        v.setMotivo(limpio(n.motivo()));
        v.setFotoRuta(fotos.rutaDe(n.fotoId()));
        v.setCreadaEn(ahora);
        v.setVenceEn(ahora.plusMinutes(VisitaPorteria.MINUTOS_VIGENCIA));
        v.setRegistradaPor(acceso.tokenActual().map(Jwt::getSubject).orElse(null));
        // P7 bloque 4: el guardia del turno de la recepción
        v.setGuardiaNombre(guardias.delTurno().map(g -> g.getNombre()).orElse(null));
        // P11: un visitante frecuente del apartamento entra sin preguntar; al apartamento solo se le avisa
        boolean frecuente = esFrecuente(v.getApartamento().getId(), nombre, v.getVisitanteDocumento());
        if (frecuente) {
            v.setEstado(VisitaPorteria.AUTORIZADA);
            v.setRespondidaEn(ahora);
            v.setCanalRespuesta(VisitaPorteria.CANAL_FRECUENTE);
        }
        v = visitas.save(v);
        if (frecuente) notificaciones.avisarVisitaFrecuente(v); else notificaciones.avisarVisita(v);
        return aDTO(v, true, fotos.enlace(v.getFotoRuta()), 0);
    }

    /** P11: un visitante frecuente de un apartamento, para que la portería lo elija al registrar. */
    public record Frecuente(String nombre, String documento) {}

    /** P11: los visitantes frecuentes de un apartamento (la portería los ve al escribir torre y apto). */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Frecuente> frecuentesDe(String torre, String apto) {
        if (limpio(torre) == null || limpio(apto) == null) return List.of();
        return apartamentos.findByTorreAndApto(torre.trim(), apto.trim()).stream().findFirst()
                .map(a -> frecuentes.findByApartamentoIdOrderByNombreCompletoAsc(a.getId()).stream()
                        .map(f -> new Frecuente(f.getNombreCompleto(), f.getIdentificacion())).toList())
                .orElse(List.of());
    }

    /**
     * P11: si [nombre] es un visitante frecuente del apartamento. Si los dos tienen documento, debe
     * ser el mismo; si no, el nombre (sin tildes, mayúsculas ni espacios de más).
     */
    private boolean esFrecuente(Long apartamentoId, String nombre, String documento) {
        String n = normalNombre(nombre);
        String d = normalDocumento(documento);
        return frecuentes.findByApartamentoIdOrderByNombreCompletoAsc(apartamentoId).stream().anyMatch(f -> {
            String fd = normalDocumento(f.getIdentificacion());
            if (d != null && fd != null) return d.equals(fd);
            return n.equals(normalNombre(f.getNombreCompleto()));
        });
    }

    private static String normalNombre(String s) {
        return s == null ? "" : PorteriaTableroService.sinTildes(s).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String normalDocumento(String s) {
        if (s == null) return null;
        String d = s.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
        return d.isEmpty() ? null : d;
    }

    /** Las últimas 24 horas, para la portería y la administración. */
    public List<Visita> recientes() {
        LocalDateTime ahora = LocalDateTime.now(zona);
        visitas.expirar(ahora);
        List<VisitaPorteria> lista = visitas.recientes(ahora.minusHours(24));
        Map<Long, Long> escritos = mensajesDelResidente(lista);
        return lista.stream().map(v -> aDTO(v, true, null, escritos.getOrDefault(v.getId(), 0L))).toList();
    }

    /** Las que espera el apartamento de la sesión, con el enlace de la foto del visitante. */
    public List<Visita> pendientesDelApartamento() {
        Long apto = acceso.apartamentoActual().orElseThrow(() -> new AccessDeniedException("Sin apartamento"));
        LocalDateTime ahora = LocalDateTime.now(zona);
        visitas.expirar(ahora);
        List<VisitaPorteria> lista = visitas.pendientesDe(apto, ahora);
        List<String> rutas = lista.stream().map(VisitaPorteria::getFotoRuta).filter(java.util.Objects::nonNull).toList();
        Map<String, String> enlaces = rutas.isEmpty() ? Map.of() : fotos.enlaces(rutas);
        return lista.stream().map(v -> aDTO(v, false, v.getFotoRuta() == null ? null : enlaces.get(v.getFotoRuta()), 0)).toList();
    }

    /** P10 bloque 7: las visitas rechazadas del apartamento de la sesión con el chat todavía abierto. */
    @Transactional(readOnly = true)
    public List<Visita> chatsDelApartamento() {
        Long apto = acceso.apartamentoActual().orElseThrow(() -> new AccessDeniedException("Sin apartamento"));
        LocalDateTime desde = LocalDateTime.now(zona).minusMinutes(VisitaPorteria.MINUTOS_CHAT);
        return visitas.findByApartamentoIdAndEstadoAndRespondidaEnAfterOrderByRespondidaEnDesc(apto, VisitaPorteria.RECHAZADA, desde)
                .stream().map(v -> aDTO(v, false, null, 0)).toList();
    }

    /** P10 bloque 7: subir la foto antes de registrar la visita o la correspondencia. */
    public FotosPorteria.Subida subirFoto(org.springframework.web.multipart.MultipartFile archivo) {
        return fotos.subir(archivo);
    }

    /** P10 bloque 7: el enlace de la foto del visitante, para la portería o el apartamento de la visita. */
    @Transactional(readOnly = true)
    public FotosPorteria.Enlace foto(Long id) {
        VisitaPorteria v = visitaVisible(id);
        if (v.getFotoRuta() == null) throw new RecursoNoEncontradoException("Esta visita no tiene foto");
        return new FotosPorteria.Enlace(fotos.enlace(v.getFotoRuta()));
    }

    /** P10 bloque 7: el chat de una visita rechazada (la portería o el apartamento de la visita). */
    @Transactional(readOnly = true)
    public Chat chat(Long id) {
        VisitaPorteria v = visitaVisible(id);
        LocalDateTime hasta = v.chatHasta();
        if (hasta == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo hay chat cuando la visita se rechaza");
        Apartamento a = v.getApartamento();
        return new Chat(v.getId(), v.getVisitanteNombre(), a.getTorre(), a.getApto(), hasta.isAfter(LocalDateTime.now(zona)), hasta,
                mensajes.findByVisitaIdOrderByCreadoEnAscIdAsc(v.getId()).stream()
                        .map(m -> new MensajeChat(m.getId(), m.isDeResidente(), m.getAutor(), m.getTexto(), m.getCreadoEn())).toList());
    }

    /** P10 bloque 7: escribir en el chat mientras esté abierto; al otro lado le llega el aviso. */
    public MensajeChat escribir(Long id, NuevoMensaje cuerpo) {
        String texto = cuerpo == null ? null : limpio(cuerpo.texto());
        if (texto == null) throw invalido("Escribe el mensaje");
        if (texto.length() > 500) throw invalido("El mensaje es muy largo (máximo 500 caracteres)");
        VisitaPorteria v = visitaVisible(id);
        LocalDateTime hasta = v.chatHasta();
        LocalDateTime ahora = LocalDateTime.now(zona);
        if (hasta == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo hay chat cuando la visita se rechaza");
        if (!hasta.isAfter(ahora)) throw new ResponseStatusException(HttpStatus.CONFLICT, "El chat de esta visita ya se cerró");
        boolean deResidente = !esPersonal();
        Apartamento a = v.getApartamento();
        com.residencial.model.MensajeVisita m = new com.residencial.model.MensajeVisita();
        m.setVisita(v);
        m.setDeResidente(deResidente);
        m.setAutor(deResidente ? "Apto " + a.getTorre() + "-" + a.getApto()
                : guardias.delTurno().map(g -> g.getNombre()).orElse("Portería"));
        m.setTexto(texto);
        m.setCreadoEn(ahora);
        m = mensajes.save(m);
        eventos.publishEvent(new com.residencial.push.AvisosPush.MensajeVisitaNuevo(v.getId(), a.getId(), deResidente,
                (deResidente ? "Apto " + a.getTorre() + "-" + a.getApto() : "Portería") + ": " + texto));
        return new MensajeChat(m.getId(), m.isDeResidente(), m.getAutor(), m.getTexto(), m.getCreadoEn());
    }

    /** La visita si la sesión es de la portería o la administración, o del apartamento de la visita. */
    private VisitaPorteria visitaVisible(Long id) {
        VisitaPorteria v = visitas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Visita no encontrada: " + id));
        if (!esPersonal()) {
            Long suyo = acceso.apartamentoActual().orElse(null);
            if (suyo == null || !suyo.equals(v.getApartamento().getId())) throw new AccessDeniedException("Esa visita no es de tu apartamento");
        }
        return v;
    }

    private boolean esPersonal() {
        String rol = acceso.tokenActual().map(t -> t.getClaimAsString(TokenService.CLAIM_ROL)).orElse(null);
        return TokenService.ROL_PORTERIA.equals(rol) || TokenService.ROL_ADMIN.equals(rol);
    }

    private Map<Long, Long> mensajesDelResidente(List<VisitaPorteria> lista) {
        List<Long> rechazadas = lista.stream().filter(v -> v.chatHasta() != null).map(VisitaPorteria::getId).toList();
        if (rechazadas.isEmpty()) return Map.of();
        return mensajes.delResidentePorVisita(rechazadas).stream()
                .collect(Collectors.toMap(f -> (Long) f[0], f -> (Long) f[1]));
    }

    public Visita responder(Long id, String decision) {
        String d = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!d.equals("AUTORIZAR") && !d.equals("RECHAZAR")) throw invalido("La decisión debe ser AUTORIZAR o RECHAZAR");
        VisitaPorteria v = visitas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Visita no encontrada: " + id));
        Jwt sesion = acceso.tokenActual().orElseThrow(() -> new AccessDeniedException("Debes iniciar sesión"));
        String rol = sesion.getClaimAsString(TokenService.CLAIM_ROL);
        boolean personal = TokenService.ROL_PORTERIA.equals(rol) || TokenService.ROL_ADMIN.equals(rol);
        if (!personal) {
            // Un residente solo responde las de su apartamento
            Long suyo = acceso.apartamentoActual().orElse(null);
            if (suyo == null || !suyo.equals(v.getApartamento().getId())) throw new AccessDeniedException("Esa visita no es de tu apartamento");
        }
        LocalDateTime ahora = LocalDateTime.now(zona);
        if (VisitaPorteria.PENDIENTE.equals(v.getEstado()) && !v.getVenceEn().isAfter(ahora)) v.setEstado(VisitaPorteria.EXPIRADA);
        if (!VisitaPorteria.PENDIENTE.equals(v.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    VisitaPorteria.EXPIRADA.equals(v.getEstado()) ? "La visita ya expiró" : "La visita ya fue respondida");
        }
        v.setEstado(d.equals("AUTORIZAR") ? VisitaPorteria.AUTORIZADA : VisitaPorteria.RECHAZADA);
        v.setRespondidaEn(ahora);
        v.setRespondidaPor(sesion.getSubject());
        v.setCanalRespuesta(personal ? VisitaPorteria.CANAL_LLAMADA : VisitaPorteria.CANAL_APP);
        return aDTO(visitas.save(v), personal, null, 0);
    }

    /**
     * P8 bloque 4: el parqueadero de invitados para una visita en vehículo. Sin parqueaderos de
     * invitados en el conjunto, la visita entra sin él; si los hay y están todos ocupados, 409.
     */
    private Parqueadero parqueaderoParaVisita(Long elegido) {
        List<Parqueadero> deInvitados = parqueaderos.findByTipoOrderByNumeroAsc(Parqueadero.INVITADOS);
        if (deInvitados.isEmpty()) {
            if (elegido != null) throw new RecursoNoEncontradoException("Parqueadero no encontrado");
            return null;
        }
        Set<Long> ocupados = visitas.conParqueaderoOcupado().stream().map(x -> x.getParqueadero().getId()).collect(Collectors.toSet());
        Long id = elegido != null ? elegido : deInvitados.stream().map(Parqueadero::getId)
                .filter(p -> !ocupados.contains(p)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No hay parqueaderos de invitados libres"));
        Parqueadero p = parqueaderos.paraAsignar(id).orElseThrow(() -> new RecursoNoEncontradoException("Parqueadero no encontrado"));
        if (!Parqueadero.INVITADOS.equals(p.getTipo())) throw invalido("Ese parqueadero no es de invitados");
        // Con la fila bloqueada se vuelve a mirar: otro portero pudo ocuparlo hace un instante
        if (ocupados.contains(p.getId()) || visitas.conParqueaderoOcupado().stream().anyMatch(x -> x.getParqueadero().getId().equals(p.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El parqueadero " + p.getNumero() + " está ocupado");
        }
        return p;
    }

    /** P8 bloque 4: los parqueaderos de invitados, libres u ocupados (y por quién). */
    public List<ParqueaderoInvitados> parqueaderosInvitados() {
        visitas.expirar(LocalDateTime.now(zona));
        Map<Long, VisitaPorteria> ocupados = visitas.conParqueaderoOcupado().stream()
                .collect(Collectors.toMap(x -> x.getParqueadero().getId(), x -> x, (a, b) -> a));
        return parqueaderos.findByTipoOrderByNumeroAsc(Parqueadero.INVITADOS).stream().map(p -> {
            VisitaPorteria v = ocupados.get(p.getId());
            return v == null
                    ? new ParqueaderoInvitados(p.getId(), p.getNumero(), true, null, null, null, null, null, null, null)
                    : new ParqueaderoInvitados(p.getId(), p.getNumero(), false, v.getId(), v.getVisitanteNombre(), v.getPlaca(),
                            v.getApartamento().getTorre(), v.getApartamento().getApto(), v.getEstado(), v.getCreadaEn());
        }).toList();
    }

    /** P8 bloque 4: el visitante salió; si ocupaba un parqueadero de invitados, queda libre. */
    public Visita registrarSalida(Long id) {
        VisitaPorteria v = visitas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Visita no encontrada: " + id));
        if (v.getSalidaEn() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "La salida ya estaba registrada");
        v.setSalidaEn(LocalDateTime.now(zona));
        return aDTO(visitas.save(v), true, null, 0);
    }

    /** Cada 15 segundos: las visitas sin respuesta pasan a EXPIRADA, en todos los conjuntos (P7). */
    @Scheduled(fixedDelayString = "${app.porteria.expiracion-ms:15000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void expirar() {
        ConjuntoContexto.comoRaiz(() -> visitas.expirar(LocalDateTime.now(zona)));
    }

    /** P8 bloque 5: para la portería (con el teléfono del apartamento y el guardia). */
    public Visita aDTO(VisitaPorteria v) {
        return aDTO(v, true, null, 0);
    }

    private Visita aDTO(VisitaPorteria v, boolean conTelefono, String fotoUrl, long mensajesResidente) {
        Apartamento a = v.getApartamento();
        String telefono = conTelefono ? residentes.findByApartamentoIdOrderByIdAsc(a.getId()).stream()
                .filter(r -> Residente.APROBADO.equals(r.getEstado()) && r.getTelefono() != null && !r.getTelefono().isBlank())
                .map(Residente::getTelefono).findFirst().orElse(null) : null;
        return new Visita(v.getId(), a.getId(), a.getTorre(), a.getApto(), v.getVisitanteNombre(), v.getVisitanteDocumento(),
                v.getPlaca(), v.getMotivo(), v.getEstado(), v.getCreadaEn(), v.getVenceEn(), v.getRespondidaEn(),
                v.getCanalRespuesta(), telefono, conTelefono ? v.getGuardiaNombre() : null,
                v.getTipoIngreso(), v.getParqueadero() == null ? null : v.getParqueadero().getId(),
                v.getParqueadero() == null ? null : v.getParqueadero().getNumero(), v.getSalidaEn(),
                v.getFotoRuta() != null, fotoUrl, v.chatHasta(), mensajesResidente);
    }

    private static int largo(String s) {
        return s == null ? 0 : s.trim().length();
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
