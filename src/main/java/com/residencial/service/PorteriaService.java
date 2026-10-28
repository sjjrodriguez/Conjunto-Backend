package com.residencial.service;

import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Residente;
import com.residencial.model.VisitaPorteria;
import com.residencial.repository.ApartamentoRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

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
                         LocalDateTime respondidaEn, String canal, String telefono) {}

    public record Nueva(String torre, String apto, String visitante, String documento, String placa, String motivo) {}

    private final VisitaPorteriaRepository visitas;
    private final ApartamentoRepository apartamentos;
    private final ResidenteRepository residentes;
    private final NotificacionService notificaciones;
    private final AccesoService acceso;
    private final ZoneId zona;

    public PorteriaService(VisitaPorteriaRepository visitas, ApartamentoRepository apartamentos,
                           ResidenteRepository residentes, NotificacionService notificaciones, AccesoService acceso,
                           @Value("${app.zona-horaria:America/Bogota}") String zona) {
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
        List<Apartamento> encontrados = n.torre() == null || n.apto() == null ? List.of()
                : apartamentos.findByTorreAndApto(n.torre().trim(), n.apto().trim());
        if (encontrados.isEmpty()) throw new RecursoNoEncontradoException("No existe ese apartamento");
        LocalDateTime ahora = LocalDateTime.now(zona);
        VisitaPorteria v = new VisitaPorteria();
        v.setApartamento(encontrados.get(0));
        v.setVisitanteNombre(nombre);
        v.setVisitanteDocumento(limpio(n.documento()));
        String placa = limpio(n.placa());
        v.setPlaca(placa == null ? null : placa.toUpperCase(Locale.ROOT));
        v.setMotivo(limpio(n.motivo()));
        v.setCreadaEn(ahora);
        v.setVenceEn(ahora.plusMinutes(VisitaPorteria.MINUTOS_VIGENCIA));
        v.setRegistradaPor(acceso.tokenActual().map(Jwt::getSubject).orElse(null));
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

    /** Cada 15 segundos: las visitas sin respuesta pasan a EXPIRADA. */
    @Scheduled(fixedDelayString = "${app.porteria.expiracion-ms:15000}")
    public void expirar() {
        visitas.expirar(LocalDateTime.now(zona));
    }

    private Visita aDTO(VisitaPorteria v, boolean conTelefono) {
        Apartamento a = v.getApartamento();
        String telefono = conTelefono ? residentes.findByApartamentoIdOrderByIdAsc(a.getId()).stream()
                .filter(r -> Residente.APROBADO.equals(r.getEstado()) && r.getTelefono() != null && !r.getTelefono().isBlank())
                .map(Residente::getTelefono).findFirst().orElse(null) : null;
        return new Visita(v.getId(), a.getId(), a.getTorre(), a.getApto(), v.getVisitanteNombre(), v.getVisitanteDocumento(),
                v.getPlaca(), v.getMotivo(), v.getEstado(), v.getCreadaEn(), v.getVenceEn(), v.getRespondidaEn(),
                v.getCanalRespuesta(), telefono);
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
