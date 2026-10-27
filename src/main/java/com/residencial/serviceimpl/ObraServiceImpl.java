package com.residencial.serviceimpl;

import com.residencial.dto.ObraDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Catalogo;
import com.residencial.model.Obra;
import com.residencial.model.PersonalObra;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ObraRepository;
import com.residencial.repository.PersonalObraRepository;
import com.residencial.security.AccesoService;
import com.residencial.security.TokenService;
import com.residencial.service.NotificacionService;
import com.residencial.service.ObraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Obras de los apartamentos y del conjunto. Desde P5 la obra que registra el propietario
 * espera la aprobación de la administración (que revisa la ARL de su personal); mientras está
 * pendiente él la puede editar toda, y ya aprobada solo su estado y su personal.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ObraServiceImpl implements ObraService {

    public static final String APARTAMENTOS = "APARTAMENTOS";
    public static final String CONJUNTO = "CONJUNTO";

    private final ObraRepository repo;
    private final ApartamentoRepository aptoRepo;
    private final PersonalObraRepository personalRepo;
    private final AccesoService acceso;
    private final com.residencial.security.AccesoArrendatario arrendatario;
    private final NotificacionService notificaciones;

    private ObraDTO toDTO(Obra o, Map<Long, Long> pendientes) {
        Apartamento a = o.getApartamento();
        return new ObraDTO(o.getId(), o.getDescripcionAdecuacion(), o.getFechaInicio(), o.getFechaFinEstimada(),
                o.getEstadoObra(), a == null ? null : a.getId(), o.getAprobacion(), o.getMotivoRechazo(),
                a == null ? null : a.getTorre(), a == null ? null : a.getApto(),
                pendientes.getOrDefault(o.getId(), 0L).intValue());
    }

    private ObraDTO toDTO(Obra o) {
        return toDTO(o, pendientesDe(List.of(o.getId())));
    }

    private List<ObraDTO> toDTOs(List<Obra> obras) {
        Map<Long, Long> pendientes = pendientesDe(obras.stream().map(Obra::getId).toList());
        return obras.stream().map(o -> toDTO(o, pendientes)).toList();
    }

    // Personal pendiente de aprobación por obra, en una consulta
    private Map<Long, Long> pendientesDe(Collection<Long> obraIds) {
        if (obraIds.isEmpty()) return Map.of();
        return personalRepo.pendientesPorObra(obraIds).stream()
                .collect(Collectors.toMap(f -> (Long) f[0], f -> (Long) f[1]));
    }

    private void validarFechas(ObraDTO dto) {
        if (dto.getFechaInicio() == null || dto.getFechaFinEstimada() == null) {
            throw invalido("Las fechas de inicio y fin son obligatorias");
        }
        if (dto.getFechaFinEstimada().isBefore(dto.getFechaInicio())) {
            throw invalido("La fecha fin no puede ser anterior a la fecha de inicio");
        }
        if (dto.getDescripcionAdecuacion() == null || dto.getDescripcionAdecuacion().isBlank()) {
            throw invalido("Describe la obra");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObraDTO> listarPorApartamento(Long apartamentoId) {
        acceso.verificarApartamento(apartamentoId);
        arrendatario.exigirVerObras(apartamentoId);
        return toDTOs(repo.findByApartamentoId(apartamentoId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObraDTO> listarEnProceso() {
        return toDTOs(repo.findByEstadoObra(Obra.EN_PROCESO));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObraDTO> listarTodas() {
        return toDTOs(repo.findAll());
    }

    /** Detalle de una obra: la administración o el apartamento dueño. */
    @Override
    @Transactional(readOnly = true)
    public ObraDTO obtener(Long id) {
        Obra o = repo.findConApartamentoById(id).orElseThrow(() -> new RecursoNoEncontradoException("Obra no encontrada: " + id));
        if (!esAdministracion()) {
            acceso.verificarApartamento(o.apartamentoId());
            arrendatario.exigirVerObras(o.apartamentoId());
        }
        return toDTO(o);
    }

    /** Administración: de apartamentos o del conjunto, por estado y aprobación, y buscando por torre, apto o texto. */
    @Override
    @Transactional(readOnly = true)
    public List<ObraDTO> buscar(String alcance, String estado, String aprobacion, String texto) {
        String al = alcance == null || alcance.isBlank() ? null : alcance.trim().toUpperCase(Locale.ROOT);
        String es = estado == null || estado.isBlank() ? null : Catalogo.ESTADO_OBRA.normalizar(estado);
        String ap = aprobacion == null || aprobacion.isBlank() ? null : aprobacion.trim().toUpperCase(Locale.ROOT);
        List<String> palabras = texto == null ? List.of() : Arrays.stream(normal(texto).split("\\s+")).filter(p -> !p.isBlank()).toList();
        List<Obra> obras = repo.todasConApartamento().stream()
                .filter(o -> al == null || (CONJUNTO.equals(al) == (o.getApartamento() == null)))
                .filter(o -> es == null || es.equals(o.getEstadoObra()))
                .filter(o -> ap == null || ap.equals(o.getAprobacion()))
                .filter(o -> palabras.isEmpty() || coincide(o, palabras))
                .sorted(Comparator.comparing(Obra::getFechaInicio).reversed())
                .toList();
        return toDTOs(obras);
    }

    // "1 101", "torre 1", "cocina": todas las palabras aparecen en la torre, el apto o la descripción
    private static boolean coincide(Obra o, List<String> palabras) {
        Apartamento a = o.getApartamento();
        String en = normal((a == null ? "conjunto" : "torre " + a.getTorre() + " apto " + a.getApto() + " " + a.getTorre() + " " + a.getApto())
                + " " + o.getDescripcionAdecuacion());
        return palabras.stream().allMatch(en::contains);
    }

    private static String normal(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    @Override
    public ObraDTO guardar(ObraDTO dto) {
        validarFechas(dto);
        boolean admin = esAdministracion();
        Apartamento apto = null;
        if (dto.getApartamentoId() != null) {
            acceso.verificarApartamento(dto.getApartamentoId());
            apto = aptoRepo.findById(dto.getApartamentoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado"));
        } else if (!admin) {
            throw invalido("El apartamento es obligatorio");
        }
        if (!admin && TokenService.ROL_ARRENDATARIO.equals(rolActual())) {
            throw new AccessDeniedException("Las obras del apartamento las gestiona el propietario");
        }
        Obra o = new Obra(null, dto.getDescripcionAdecuacion().trim(), dto.getFechaInicio(), dto.getFechaFinEstimada(),
                Catalogo.ESTADO_OBRA.normalizar(dto.getEstadoObra() == null ? Obra.EN_PROCESO : dto.getEstadoObra()), apto);
        // La del residente espera la aprobación; la que registra la administración, no
        o.setAprobacion(admin ? Obra.APROBADA : Obra.PENDIENTE);
        Obra guardada = repo.save(o);
        if (!admin) notificaciones.avisarObraNueva(guardada);
        return toDTO(guardada);
    }

    @Override
    public ObraDTO actualizar(Long id, ObraDTO dto) {
        Obra o = repo.findById(id).orElse(null);
        if (o == null) return null;
        if (esAdministracion()) {
            validarFechas(dto);
            o.setDescripcionAdecuacion(dto.getDescripcionAdecuacion().trim());
            o.setFechaInicio(dto.getFechaInicio());
            o.setFechaFinEstimada(dto.getFechaFinEstimada());
            o.setEstadoObra(Catalogo.ESTADO_OBRA.normalizar(dto.getEstadoObra()));
            return toDTO(repo.save(o));
        }
        acceso.verificarApartamento(o.apartamentoId());
        if (Obra.APROBADA.equals(o.getAprobacion())) {
            // Aprobada: el residente solo cambia el estado (en proceso o finalizada)
            o.setEstadoObra(Catalogo.ESTADO_OBRA.normalizar(dto.getEstadoObra()));
            return toDTO(repo.save(o));
        }
        validarFechas(dto);
        o.setDescripcionAdecuacion(dto.getDescripcionAdecuacion().trim());
        o.setFechaInicio(dto.getFechaInicio());
        o.setFechaFinEstimada(dto.getFechaFinEstimada());
        o.setEstadoObra(Catalogo.ESTADO_OBRA.normalizar(dto.getEstadoObra() == null ? o.getEstadoObra() : dto.getEstadoObra()));
        // La rechazada, corregida, vuelve a revisión
        if (Obra.RECHAZADA.equals(o.getAprobacion())) {
            o.setAprobacion(Obra.PENDIENTE);
            o.setMotivoRechazo(null);
            Obra guardada = repo.save(o);
            notificaciones.avisarObraNueva(guardada);
            return toDTO(guardada);
        }
        return toDTO(repo.save(o));
    }

    @Override
    public ObraDTO cambiarEstado(Long id, String nuevoEstado) {
        String estado = Catalogo.ESTADO_OBRA.normalizar(nuevoEstado);
        return repo.findById(id).map(o -> {
            o.setEstadoObra(estado);
            return toDTO(repo.save(o));
        }).orElse(null);
    }

    /** Aprobar (con su personal pendiente) o rechazar con motivo; al apartamento le llega el aviso. */
    @Override
    public ObraDTO revisar(Long id, boolean aprobada, String motivo) {
        Obra o = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Obra no encontrada: " + id));
        o.setAprobacion(aprobada ? Obra.APROBADA : Obra.RECHAZADA);
        o.setMotivoRechazo(aprobada || motivo == null || motivo.isBlank() ? null : motivo.trim());
        if (aprobada) {
            personalRepo.findByObraId(id).stream().filter(p -> PersonalObra.PENDIENTE.equals(p.getAprobacion())).forEach(p -> {
                p.setAprobacion(PersonalObra.APROBADO);
                personalRepo.save(p);
            });
        }
        Obra guardada = repo.save(o);
        if (guardada.getApartamento() != null) notificaciones.avisarObraRevisada(guardada);
        return toDTO(guardada);
    }

    @Override
    public void eliminar(Long id) {
        Obra o = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Obra no encontrada: " + id));
        if (!esAdministracion()) {
            // El residente retira la suya mientras no esté aprobada
            acceso.verificarApartamento(o.apartamentoId());
            if (Obra.APROBADA.equals(o.getAprobacion())) throw invalido("Una obra aprobada solo la elimina la administración");
        }
        // El personal pertenece a la obra: se borra con ella
        personalRepo.deleteByObraId(id);
        repo.delete(o);
    }

    private boolean esAdministracion() {
        return acceso.esAdminActual() || acceso.tokenActual().isEmpty();
    }

    private String rolActual() {
        return acceso.tokenActual().map(t -> t.getClaimAsString(TokenService.CLAIM_ROL)).orElse(null);
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
