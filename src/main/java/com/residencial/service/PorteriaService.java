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
 * prioridad alta y tiene 2 minutos para autorizarla o rechazarla; si no responde, expira.
 * Si el residente contesta por teléfono, el portero registra su respuesta ("por llamada").
 */
@Service
@Transactional
public class PorteriaService {

    public record Visita(Long id, Long apartamentoId, String torre, String apto, String visitante, String documento,
                         String placa, String motivo, String estado, LocalDateTime creadaEn, LocalDateTime venceEn,
                         LocalDateTime respondidaEn, String canal, String telefono, String guardia,
                         // P8 bloque 4: a pie o en vehículo, el parqueadero de invitados y cuándo salió
                         String tipoIngreso, Long parqueaderoId, String parqueadero, LocalDateTime salidaEn) {}

    /**
     * tipoIngreso: PEATONAL (por defecto) o VEHICULO. Con vehículo la placa es obligatoria y, si el
     * conjunto tiene parqueaderos de invitados, ocupa uno: parqueaderoId, o el primero libre.
     */
    public record Nueva(String torre, String apto, String visitante, String documento, String placa, String motivo,
                        String tipoIngreso, Long parqueaderoId) {}

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

    public PorteriaService(VisitaPorteriaRepository visitas, ApartamentoRepository apartamentos,
                           ResidenteRepository residentes, NotificacionService notificaciones, AccesoService acceso,
                           GuardiaService guardias, ParqueaderoRepository parqueaderos,
                           @Value("${app.zona-horaria:America/Bogota}") String zona) {
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
        v.setCreadaEn(ahora);
        v.setVenceEn(ahora.plusMinutes(VisitaPorteria.MINUTOS_VIGENCIA));
        v.setRegistradaPor(acceso.tokenActual().map(Jwt::getSubject).orElse(null));
        // P7 bloque 4: el guardia del turno de la recepción
        v.setGuardiaNombre(guardias.delTurno().map(g -> g.getNombre()).orElse(null));
        v = visitas.save(v);
        notificaciones.avisarVisita(v);
        return aDTO(v, true);
    }

    /** Las últimas 24 horas, para la portería y la administración. */
    public List<Visita> recientes() {
        LocalDateTime ahora = LocalDateTime.now(zona);
        visitas.expirar(ahora);
        return visitas.recientes(ahora.minusHours(24)).stream().map(v -> aDTO(v, true)).toList();
    }

    /** Las que espera el apartamento de la sesión. */
    public List<Visita> pendientesDelApartamento() {
        Long apto = acceso.apartamentoActual().orElseThrow(() -> new AccessDeniedException("Sin apartamento"));
        LocalDateTime ahora = LocalDateTime.now(zona);
        visitas.expirar(ahora);
        return visitas.pendientesDe(apto, ahora).stream().map(v -> aDTO(v, false)).toList();
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
        return aDTO(visitas.save(v), personal);
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
        return aDTO(visitas.save(v), true);
    }

    /** Cada 15 segundos: las visitas sin respuesta pasan a EXPIRADA, en todos los conjuntos (P7). */
    @Scheduled(fixedDelayString = "${app.porteria.expiracion-ms:15000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void expirar() {
        ConjuntoContexto.comoRaiz(() -> visitas.expirar(LocalDateTime.now(zona)));
    }

    /** P8 bloque 5: para la portería (con el teléfono del apartamento y el guardia). */
    public Visita aDTO(VisitaPorteria v) {
        return aDTO(v, true);
    }

    private Visita aDTO(VisitaPorteria v, boolean conTelefono) {
        Apartamento a = v.getApartamento();
        String telefono = conTelefono ? residentes.findByApartamentoIdOrderByIdAsc(a.getId()).stream()
                .filter(r -> Residente.APROBADO.equals(r.getEstado()) && r.getTelefono() != null && !r.getTelefono().isBlank())
                .map(Residente::getTelefono).findFirst().orElse(null) : null;
        return new Visita(v.getId(), a.getId(), a.getTorre(), a.getApto(), v.getVisitanteNombre(), v.getVisitanteDocumento(),
                v.getPlaca(), v.getMotivo(), v.getEstado(), v.getCreadaEn(), v.getVenceEn(), v.getRespondidaEn(),
                v.getCanalRespuesta(), telefono, conTelefono ? v.getGuardiaNombre() : null,
                v.getTipoIngreso(), v.getParqueadero() == null ? null : v.getParqueadero().getId(),
                v.getParqueadero() == null ? null : v.getParqueadero().getNumero(), v.getSalidaEn());
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
