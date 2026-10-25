package com.residencial.serviceimpl;

import com.residencial.dto.ResidenteDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Catalogo;
import com.residencial.model.Residente;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ResidenteRepository;
import com.residencial.security.AccesoService;
import com.residencial.security.TokenService;
import com.residencial.service.NotificacionService;
import com.residencial.service.ResidenteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Residentes de cada apartamento. El administrador los gestiona todos; el propietario
 * ve los de su apartamento y agrega arrendatarios, que quedan pendientes hasta que el
 * administrador los aprueba. Cada residente puede tener contraseña propia para entrar
 * con su documento.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ResidenteServiceImpl implements ResidenteService {

    static final int LONGITUD_MINIMA_CONTRASENA = 6;

    private final ResidenteRepository repo;
    private final ApartamentoRepository aptoRepo;
    private final AccesoService acceso;
    private final NotificacionService notificaciones;
    private final PasswordEncoder passwordEncoder;

    private ResidenteDTO toDTO(Residente r) {
        Apartamento a = r.getApartamento();
        return new ResidenteDTO(
            r.getId(), r.getNombreCompleto(), r.getTelefono(),
            r.getIdentificacion(), r.getTipoHabitante(), a.getId(),
            r.getEstado(), r.getContrasena() != null, a.getTorre(), a.getApto(), null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResidenteDTO> listarPorApartamento(Long apartamentoId) {
        acceso.verificarApartamento(apartamentoId);
        return repo.findByApartamentoIdOrderByIdAsc(apartamentoId).stream().map(this::toDTO).toList();
    }

    @Override
    public ResidenteDTO guardar(ResidenteDTO dto) {
        if (dto.getApartamentoId() == null) throw invalido("El apartamento es obligatorio");
        boolean porAdministrador = esAdministracion();
        if (!porAdministrador) {
            // El propietario solo agrega arrendatarios a su propio apartamento
            if (!TokenService.ROL_PROPIETARIO.equals(rolActual())) {
                throw new AccessDeniedException("Solo el propietario puede agregar residentes");
            }
            acceso.verificarApartamento(dto.getApartamentoId());
        }
        Apartamento apto = aptoRepo.findById(dto.getApartamentoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado: " + dto.getApartamentoId()));

        Residente r = new Residente();
        copiarDatos(dto, r);
        r.setApartamento(apto);
        if (repo.existsByApartamentoIdAndIdentificacion(apto.getId(), r.getIdentificacion())) {
            throw invalido("Ya hay un residente con ese documento en este apartamento");
        }
        if (porAdministrador) {
            r.setEstado(Residente.APROBADO);
            if (dto.getContrasena() != null && !dto.getContrasena().isBlank()) r.setContrasena(cifrar(dto.getContrasena()));
        } else {
            if (!"ARRENDATARIO".equals(r.getTipoHabitante())) {
                throw invalido("Solo puedes agregar arrendatarios; los propietarios los registra la administración");
            }
            // Con esta contraseña entrará cuando la administración lo apruebe
            r.setContrasena(cifrar(dto.getContrasena()));
            r.setEstado(Residente.PENDIENTE);
        }
        Residente guardado = repo.save(r);
        if (!porAdministrador) notificaciones.avisarNuevoResidente(guardado);
        return toDTO(guardado);
    }

    @Override
    public ResidenteDTO actualizar(Long id, ResidenteDTO dto) {
        return repo.findById(id).map(r -> {
            copiarDatos(dto, r);
            return toDTO(repo.save(r));
        }).orElse(null);
    }

    @Override
    public void eliminar(Long id) {
        Residente r = buscar(id);
        if (!esAdministracion()) {
            // El propietario puede quitar a sus arrendatarios, no a otros propietarios
            if (!TokenService.ROL_PROPIETARIO.equals(rolActual()) || !"ARRENDATARIO".equals(r.getTipoHabitante())) {
                throw new AccessDeniedException("Solo puedes quitar arrendatarios de tu apartamento");
            }
            acceso.verificarApartamento(r.getApartamento().getId());
        }
        repo.delete(r);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResidenteDTO> pendientes() {
        return repo.findByEstadoOrderByIdAsc(Residente.PENDIENTE).stream().map(this::toDTO).toList();
    }

    @Override
    public ResidenteDTO revisar(Long id, String estado, String motivo) {
        String nuevo = Catalogo.ESTADO_RESIDENTE.normalizar(estado);
        if (Residente.PENDIENTE.equals(nuevo)) throw invalido("Indica si lo apruebas o lo rechazas");
        Residente r = buscar(id);
        r.setEstado(nuevo);
        Residente guardado = repo.save(r);
        notificaciones.avisarResidenteRevisado(guardado, motivo == null || motivo.isBlank() ? null : motivo.trim());
        return toDTO(guardado);
    }

    /** Da acceso con el documento (o cambia la contraseña); sin contraseña se le quita el acceso. */
    @Override
    public ResidenteDTO cambiarAcceso(Long id, String contrasena) {
        Residente r = buscar(id);
        r.setContrasena(contrasena == null || contrasena.isBlank() ? null : cifrar(contrasena));
        return toDTO(repo.save(r));
    }

    private void copiarDatos(ResidenteDTO dto, Residente r) {
        if (dto.getNombreCompleto() == null || dto.getNombreCompleto().isBlank()) throw invalido("El nombre es obligatorio");
        if (dto.getIdentificacion() == null || dto.getIdentificacion().isBlank()) throw invalido("El documento es obligatorio");
        r.setNombreCompleto(dto.getNombreCompleto().trim());
        r.setTelefono(dto.getTelefono());
        r.setIdentificacion(dto.getIdentificacion().trim());
        r.setTipoHabitante(Catalogo.TIPO_HABITANTE.normalizar(dto.getTipoHabitante()));
    }

    private String cifrar(String contrasena) {
        if (contrasena == null || contrasena.length() < LONGITUD_MINIMA_CONTRASENA) {
            throw invalido("La contraseña debe tener al menos " + LONGITUD_MINIMA_CONTRASENA + " caracteres");
        }
        return passwordEncoder.encode(contrasena);
    }

    private Residente buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Residente no encontrado: " + id));
    }

    // Administrador, o petición sin token en modo transición (la app antigua solo la usaba el administrador)
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
