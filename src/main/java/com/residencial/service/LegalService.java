package com.residencial.service;

import com.residencial.model.Consentimiento;
import com.residencial.model.SolicitudDatos;
import com.residencial.model.TextoLegal;
import com.residencial.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Textos legales con su versión, consentimientos y solicitudes de datos personales (P6 bloque 4). */
@Service
@Transactional
public class LegalService {

    public record Texto(Long id, String tipo, int version, String titulo, String contenido) {}
    public record Solicitud(Long id, String nombre, String tipo, String estado, String detalle, String respuesta,
                            LocalDateTime creadaEn, LocalDateTime resueltaEn) {}

    private final TextoLegalRepository textos;
    private final ConsentimientoRepository consentimientos;
    private final SolicitudDatosRepository solicitudes;
    private final AdministradorRepository admins;
    private final ResidenteRepository residentes;
    private final ApartamentoRepository apartamentos;
    private final ZoneId zona;

    public LegalService(TextoLegalRepository textos, ConsentimientoRepository consentimientos,
                        SolicitudDatosRepository solicitudes, AdministradorRepository admins,
                        ResidenteRepository residentes, ApartamentoRepository apartamentos,
                        @Value("${app.zona-horaria:America/Bogota}") String zona) {
        this.textos = textos;
        this.consentimientos = consentimientos;
        this.solicitudes = solicitudes;
        this.admins = admins;
        this.residentes = residentes;
        this.apartamentos = apartamentos;
        this.zona = ZoneId.of(zona);
    }

    @Transactional(readOnly = true)
    public List<Texto> vigentes() {
        return textos.vigentes().stream().map(LegalService::aTexto).toList();
    }

    /** Los textos vigentes que [sujeto] todavía no aceptó. */
    @Transactional(readOnly = true)
    public List<Texto> pendientes(String sujeto) {
        List<TextoLegal> vigentes = textos.vigentes();
        if (vigentes.isEmpty()) return List.of();
        Set<Long> aceptados = Set.copyOf(consentimientos.aceptados(sujeto, vigentes.stream().map(TextoLegal::getId).toList()));
        return vigentes.stream().filter(t -> !aceptados.contains(t.getId())).map(LegalService::aTexto).toList();
    }

    /** Acepta esos textos (solo los vigentes); repetirlo no duplica. */
    public void aceptar(String sujeto, List<Long> ids, String ip) {
        Set<Long> vigentes = textos.vigentes().stream().map(TextoLegal::getId).collect(Collectors.toSet());
        for (Long id : ids == null ? List.<Long>of() : ids) {
            if (!vigentes.contains(id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese texto ya no está vigente");
            if (!consentimientos.existsBySujetoAndTextoId(sujeto, id)) {
                consentimientos.save(new Consentimiento(null, sujeto, id, LocalDateTime.now(zona), ip));
            }
        }
    }

    public Solicitud solicitar(String sujeto, String tipo, String detalle) {
        String t = tipo == null ? "" : tipo.trim().toUpperCase();
        if (!SolicitudDatos.TIPOS.contains(t)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo de solicitud no es válido");
        if (detalle != null && detalle.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El detalle es muy largo");
        SolicitudDatos s = new SolicitudDatos();
        s.setSujeto(sujeto);
        s.setNombre(nombreDe(sujeto));
        s.setTipo(t);
        s.setDetalle(detalle == null || detalle.isBlank() ? null : detalle.trim());
        s.setCreadaEn(LocalDateTime.now(zona));
        return aSolicitud(solicitudes.save(s));
    }

    @Transactional(readOnly = true)
    public List<Solicitud> mias(String sujeto) {
        return solicitudes.findBySujetoOrderByCreadaEnDesc(sujeto).stream().map(LegalService::aSolicitud).toList();
    }

    @Transactional(readOnly = true)
    public List<Solicitud> todas() {
        return solicitudes.findAllByOrderByCreadaEnDesc().stream().map(LegalService::aSolicitud).toList();
    }

    public Solicitud resolver(Long id, String estado, String respuesta, String resueltaPor) {
        SolicitudDatos s = solicitudes.findById(id)
                .orElseThrow(() -> new com.residencial.exception.RecursoNoEncontradoException("Solicitud no encontrada: " + id));
        String e = estado == null ? "" : estado.trim().toUpperCase();
        if (!SolicitudDatos.ATENDIDA.equals(e) && !SolicitudDatos.RECHAZADA.equals(e)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado debe ser ATENDIDA o RECHAZADA");
        }
        if (!SolicitudDatos.PENDIENTE.equals(s.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya fue resuelta");
        }
        s.setEstado(e);
        s.setRespuesta(respuesta == null || respuesta.isBlank() ? null : respuesta.trim());
        s.setResueltaEn(LocalDateTime.now(zona));
        s.setResueltaPor(resueltaPor);
        return aSolicitud(solicitudes.save(s));
    }

    private String nombreDe(String sujeto) {
        try {
            Long id = Long.valueOf(sujeto.substring(sujeto.indexOf(':') + 1));
            if (sujeto.startsWith("admin:")) return admins.findById(id).map(a -> a.getUsuario()).orElse(null);
            if (sujeto.startsWith("residente:")) return residentes.findById(id).map(r -> r.getNombreCompleto()).orElse(null);
            return apartamentos.findById(id).map(a -> "Torre " + a.getTorre() + " · Apto " + a.getApto()).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Texto aTexto(TextoLegal t) {
        return new Texto(t.getId(), t.getTipo(), t.getVersion(), t.getTitulo(), t.getContenido());
    }

    private static Solicitud aSolicitud(SolicitudDatos s) {
        return new Solicitud(s.getId(), s.getNombre(), s.getTipo(), s.getEstado(), s.getDetalle(), s.getRespuesta(),
                s.getCreadaEn(), s.getResueltaEn());
    }
}
