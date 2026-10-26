package com.residencial.serviceimpl;

import com.residencial.correo.Correo;
import com.residencial.correo.Plantillas;
import com.residencial.dto.ResidenteDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Catalogo;
import com.residencial.model.Residente;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ResidenteRepository;
import com.residencial.repository.DispositivoRepository;
import com.residencial.security.AccesoService;
import com.residencial.security.EstadoApartamento;
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
    private final Correo correo;
    private final EstadoApartamento estado;
    private final DispositivoRepository dispositivos;

    private ResidenteDTO toDTO(Residente r) {
        Apartamento a = r.getApartamento();
        return new ResidenteDTO(
            r.getId(), r.getNombreCompleto(), r.getTelefono(),
            r.getIdentificacion(), r.getTipoHabitante(), a.getId(),
            r.getEstado(), r.getContrasena() != null, a.getTorre(), a.getApto(), null,
            r.getCorreo(), r.isTitular(), r.getAprobadoPropietario()
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
        boolean conCuenta = dto.getContrasena() != null && !dto.getContrasena().isBlank();
        String rol = rolActual();
        if (!porAdministrador) {
            acceso.verificarApartamento(dto.getApartamentoId());
            if (conCuenta) {
                // Solo el propietario agrega a su arrendatario (titular con contraseña)
                if (!TokenService.ROL_PROPIETARIO.equals(rol)) {
                    throw new AccessDeniedException("Solo el propietario puede agregar residentes");
                }
            } else {
                // Persona del hogar: la agrega quien vive en el apartamento (P5)
                if (!TokenService.ROL_PROPIETARIO.equals(rol) && !TokenService.ROL_ARRENDATARIO.equals(rol)) {
                    throw new AccessDeniedException("Solo los residentes pueden agregar personas a su hogar");
                }
                estado.exigirQueViva(dto.getApartamentoId());
            }
        }
        Apartamento apto = aptoRepo.findById(dto.getApartamentoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado: " + dto.getApartamentoId()));

        Residente r = new Residente();
        if (!porAdministrador && !conCuenta) dto.setTipoHabitante(rol);
        copiarDatos(dto, r);
        r.setApartamento(apto);
        if (repo.existsByApartamentoIdAndIdentificacion(apto.getId(), r.getIdentificacion())) {
            throw invalido("Ya hay un residente con ese documento en este apartamento");
        }
        if (porAdministrador) {
            r.setEstado(Residente.APROBADO);
            if (conCuenta) {
                r.setContrasena(cifrar(dto.getContrasena()));
                r.setTitular(true);
            }
        } else if (conCuenta) {
            if (!"ARRENDATARIO".equals(r.getTipoHabitante())) {
                throw invalido("Solo puedes agregar arrendatarios; los propietarios los registra la administración");
            }
            // Con esta contraseña entrará cuando la administración lo apruebe
            r.setContrasena(cifrar(dto.getContrasena()));
            r.setEstado(Residente.PENDIENTE);
            // Titular arrendatario que agrega el propietario: ya cuenta con su aprobación
            r.setTitular(true);
            r.setAprobadoPropietario(Boolean.TRUE);
        } else {
            r.setEstado(Residente.APROBADO);
        }
        Residente guardado = repo.save(r);
        if (!porAdministrador && conCuenta) notificaciones.avisarNuevoResidente(guardado);
        return toDTO(guardado);
    }

    @Override
    public ResidenteDTO actualizar(Long id, ResidenteDTO dto) {
        Residente r = repo.findById(id).orElse(null);
        if (r == null) return null;
        if (!esAdministracion()) {
            // El residente edita a las personas de su hogar, no a los titulares
            if (r.isTitular()) throw new AccessDeniedException("Los datos del titular los cambia la administración");
            acceso.verificarApartamento(r.getApartamento().getId());
            estado.exigirQueViva(r.getApartamento().getId());
            dto.setTipoHabitante(r.getTipoHabitante());
        }
        copiarDatos(dto, r);
        return toDTO(repo.save(r));
    }

    @Override
    public void eliminar(Long id) {
        Residente r = buscar(id);
        if (!esAdministracion()) {
            String rol = rolActual();
            acceso.verificarApartamento(r.getApartamento().getId());
            boolean esSuArrendatario = TokenService.ROL_PROPIETARIO.equals(rol) && r.esArrendatario();
            if (r.isTitular()) {
                // El propietario quita a su arrendatario y recupera el apartamento
                if (!esSuArrendatario) throw new AccessDeniedException("Solo puedes quitar arrendatarios de tu apartamento");
            } else {
                estado.exigirQueViva(r.getApartamento().getId());
                if (!r.getTipoHabitante().equals(rol) && !esSuArrendatario) {
                    throw new AccessDeniedException("Solo puedes quitar personas de tu hogar");
                }
            }
        }
        if (r.isTitular() && r.esArrendatario()) quitarHogarDelArrendatario(r.getApartamento().getId());
        repo.delete(r);
    }

    /** Se fue el arrendatario: con él, las personas de su hogar y sus celulares registrados. */
    private void quitarHogarDelArrendatario(Long apartamentoId) {
        repo.findByApartamentoIdOrderByIdAsc(apartamentoId).stream()
                .filter(p -> !p.isTitular() && p.esArrendatario())
                .forEach(repo::delete);
        dispositivos.deleteAll(dispositivos.findByApartamentoId(apartamentoId).stream()
                .filter(d -> TokenService.ROL_ARRENDATARIO.equals(d.getRol())).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ResidenteDTO arrendatarioDe(Long apartamentoId) {
        acceso.verificarApartamento(apartamentoId);
        return repo.findTitulares(apartamentoId, Residente.ARRENDATARIO).stream()
                .filter(r -> !Residente.RECHAZADO.equals(r.getEstado()))
                .reduce((a, b) -> b).map(this::toDTO).orElse(null);
    }

    /** El propietario acepta o rechaza al arrendatario que se registró en su apartamento. */
    @Override
    public ResidenteDTO aprobarComoPropietario(Long id, boolean aprobado, String motivo) {
        Residente r = buscar(id);
        if (!TokenService.ROL_PROPIETARIO.equals(rolActual())) {
            throw new AccessDeniedException("Solo el propietario aprueba a su arrendatario");
        }
        acceso.verificarApartamento(r.getApartamento().getId());
        if (!r.isTitular() || !r.esArrendatario() || Residente.RECHAZADO.equals(r.getEstado())
                || !Boolean.FALSE.equals(r.getAprobadoPropietario())) {
            throw invalido("Esta solicitud ya fue revisada");
        }
        String razon = motivo == null || motivo.isBlank() ? null : motivo.trim();
        r.setAprobadoPropietario(aprobado);
        if (!aprobado) r.setEstado(Residente.RECHAZADO);
        Residente guardado = repo.save(r);
        repo.findByApartamentoIdOrderByIdAsc(r.getApartamento().getId()).stream()
                .filter(p -> !p.isTitular() && p.esArrendatario() && !Residente.RECHAZADO.equals(p.getEstado()))
                .forEach(p -> {
                    p.setAprobadoPropietario(aprobado);
                    if (!aprobado) p.setEstado(Residente.RECHAZADO);
                    repo.save(p);
                });
        notificaciones.avisarRevisionDelPropietario(guardado, razon);
        if (guardado.getCorreo() != null && (!aprobado || guardado.puedeEntrar())) {
            correo.enviar(guardado.getCorreo(), guardado.getNombreCompleto(),
                    aprobado ? Plantillas.asuntoAprobado() : Plantillas.asuntoRechazado(),
                    aprobado ? Plantillas.aprobado(guardado)
                            : Plantillas.rechazado(guardado, razon == null ? "El propietario no aprobó la solicitud" : razon));
        }
        return toDTO(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResidenteDTO> pendientes() {
        // Solo los titulares: las personas de su hogar se aprueban junto con ellos
        return repo.findByEstadoAndTitularTrueOrderByIdAsc(Residente.PENDIENTE).stream().map(this::toDTO).toList();
    }

    @Override
    public ResidenteDTO revisar(Long id, String estado, String motivo) {
        String nuevo = Catalogo.ESTADO_RESIDENTE.normalizar(estado);
        if (Residente.PENDIENTE.equals(nuevo)) throw invalido("Indica si lo apruebas o lo rechazas");
        Residente r = buscar(id);
        r.setEstado(nuevo);
        Residente guardado = repo.save(r);
        String razon = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (guardado.isTitular()) {
            // Las personas que registró con él siguen su suerte
            repo.findByApartamentoIdOrderByIdAsc(guardado.getApartamento().getId()).stream()
                    .filter(p -> !p.isTitular() && Residente.PENDIENTE.equals(p.getEstado())
                            && p.getTipoHabitante().equals(guardado.getTipoHabitante()))
                    .forEach(p -> { p.setEstado(nuevo); repo.save(p); });
            if (guardado.getCorreo() != null) {
                boolean aprobado = Residente.APROBADO.equals(nuevo);
                correo.enviar(guardado.getCorreo(), guardado.getNombreCompleto(),
                        aprobado ? Plantillas.asuntoAprobado() : Plantillas.asuntoRechazado(),
                        aprobado ? Plantillas.aprobado(guardado) : Plantillas.rechazado(guardado, razon));
            }
        }
        notificaciones.avisarResidenteRevisado(guardado, razon);
        return toDTO(guardado);
    }

    /** Da acceso con el documento (o cambia la contraseña); sin contraseña se le quita el acceso. */
    @Override
    public ResidenteDTO cambiarAcceso(Long id, String contrasena) {
        Residente r = buscar(id);
        if (!esAdministracion()) {
            // El propietario administra el acceso de su arrendatario (P5)
            if (!TokenService.ROL_PROPIETARIO.equals(rolActual()) || !r.isTitular() || !r.esArrendatario()) {
                throw new AccessDeniedException("Solo puedes cambiar el acceso de tu arrendatario");
            }
            acceso.verificarApartamento(r.getApartamento().getId());
            if (contrasena == null || contrasena.isBlank()) throw invalido("Escribe la nueva contraseña");
        }
        r.setContrasena(contrasena == null || contrasena.isBlank() ? null : cifrar(contrasena));
        r.setTitular(r.getContrasena() != null || (r.isTitular() && r.esArrendatario()));
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
