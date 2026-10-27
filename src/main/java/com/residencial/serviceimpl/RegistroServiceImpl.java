package com.residencial.serviceimpl;

import com.residencial.correo.Correo;
import com.residencial.correo.Plantillas;
import com.residencial.dto.RegistroDTO;
import com.residencial.model.Apartamento;
import com.residencial.model.Catalogo;
import com.residencial.model.Residente;
import com.residencial.model.VisitanteFrecuente;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ResidenteRepository;
import com.residencial.repository.VisitanteFrecuenteRepository;
import com.residencial.service.NotificacionService;
import com.residencial.service.RegistroService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Cada apartamento tiene un titular propietario y, si está arrendado, un titular arrendatario.
 * Quien se registra en un apartamento sin propietario queda como propietario; si ya tiene
 * uno, como arrendatario. Todo registro espera la aprobación de la administración, y el del
 * arrendatario también la del propietario.
 */
@Service
@Transactional
public class RegistroServiceImpl implements RegistroService {

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CEDULA = Pattern.compile("^[0-9A-Za-z]{5,15}$");
    private static final int MAXIMO_PERSONAS = 15;
    private static final int MAXIMO_VISITANTES = 20;

    private final ApartamentoRepository aptoRepo;
    private final ResidenteRepository residenteRepo;
    private final VisitanteFrecuenteRepository visitanteRepo;
    private final NotificacionService notificaciones;
    private final Correo correo;
    private final PasswordEncoder passwordEncoder;
    private final ZoneId zonaHoraria;
    private final String hashFicticio;

    public RegistroServiceImpl(ApartamentoRepository aptoRepo, ResidenteRepository residenteRepo,
                               VisitanteFrecuenteRepository visitanteRepo, NotificacionService notificaciones,
                               Correo correo, PasswordEncoder passwordEncoder,
                               @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
        this.aptoRepo = aptoRepo;
        this.residenteRepo = residenteRepo;
        this.visitanteRepo = visitanteRepo;
        this.notificaciones = notificaciones;
        this.correo = correo;
        this.passwordEncoder = passwordEncoder;
        this.zonaHoraria = ZoneId.of(zonaHoraria);
        this.hashFicticio = passwordEncoder.encode("contrasena-ficticia");
    }

    @Override
    @Transactional(readOnly = true)
    public RegistroDTO.Verificado verificar(RegistroDTO.Verificar s) {
        Apartamento a = apartamento(s == null ? null : s.torre(), s == null ? null : s.apto());
        return new RegistroDTO.Verificado(rolDisponible(a));
    }

    @Override
    public RegistroDTO.Recibido registrar(RegistroDTO.Solicitud s) {
        if (s == null) throw invalido("Faltan los datos del registro");
        Apartamento a = apartamento(s.torre(), s.apto());
        String rol = rolDisponible(a);
        if (!s.aceptaDatos() || !s.aceptaTerminos()) {
            throw invalido("Debes aceptar el tratamiento de datos y los términos y condiciones");
        }
        String nombres = texto(s.nombres(), "Los nombres son obligatorios");
        String apellidos = texto(s.apellidos(), "Los apellidos son obligatorios");
        String cedula = texto(s.cedula(), "La cédula es obligatoria").replaceAll("[.\\s-]", "");
        if (!CEDULA.matcher(cedula).matches()) throw invalido("La cédula no es válida");
        String telefono = texto(s.telefono(), "El teléfono es obligatorio");
        String email = texto(s.correo(), "El correo es obligatorio").toLowerCase();
        if (!CORREO.matcher(email).matches()) throw invalido("El correo no es válido");
        if (s.contrasena() == null || s.contrasena().length() < ResidenteServiceImpl.LONGITUD_MINIMA_CONTRASENA) {
            throw invalido("La contraseña debe tener al menos " + ResidenteServiceImpl.LONGITUD_MINIMA_CONTRASENA + " caracteres");
        }
        List<RegistroDTO.Persona> personas = s.personas() == null ? List.of() : s.personas();
        List<RegistroDTO.Persona> visitantes = s.visitantes() == null ? List.of() : s.visitantes();
        if (personas.size() > MAXIMO_PERSONAS) throw invalido("Máximo " + MAXIMO_PERSONAS + " personas en el registro");
        if (visitantes.size() > MAXIMO_VISITANTES) throw invalido("Máximo " + MAXIMO_VISITANTES + " visitantes frecuentes");

        // Si la administración ya lo tenía como residente (sin cuenta), se completa ese registro
        Residente titular = residenteRepo.findByApartamentoIdOrderByIdAsc(a.getId()).stream()
                .filter(r -> cedula.equalsIgnoreCase(r.getIdentificacion()) && !r.isTitular())
                .findFirst().orElseGet(Residente::new);
        LocalDateTime ahora = LocalDateTime.now(zonaHoraria);
        titular.setApartamento(a);
        titular.setNombreCompleto(nombres + " " + apellidos);
        titular.setIdentificacion(cedula);
        titular.setTelefono(telefono);
        titular.setCorreo(email);
        titular.setTipoHabitante(rol);
        titular.setTitular(true);
        titular.setContrasena(passwordEncoder.encode(s.contrasena()));
        titular.setEstado(Residente.PENDIENTE);
        titular.setTerminosAceptadosEn(ahora);
        titular.setAprobadoPropietario(Residente.ARRENDATARIO.equals(rol) ? Boolean.FALSE : null);
        Residente guardado = residenteRepo.save(titular);

        for (RegistroDTO.Persona p : personas) {
            if (p == null || p.nombreCompleto() == null || p.nombreCompleto().isBlank()) continue;
            String doc = p.identificacion() == null || p.identificacion().isBlank() ? "Sin documento" : p.identificacion().trim();
            if (residenteRepo.existsByApartamentoIdAndIdentificacion(a.getId(), doc) && !"Sin documento".equals(doc)) continue;
            // Viven con el titular: mismo tipo de habitante, sin cuenta; pendientes como él
            Residente r = new Residente(null, p.nombreCompleto().trim(), vacioANull(p.telefono()), doc, rol, a, null, Residente.PENDIENTE);
            r.setAprobadoPropietario(guardado.getAprobadoPropietario());
            residenteRepo.save(r);
        }
        for (RegistroDTO.Persona v : visitantes) {
            if (v == null || v.nombreCompleto() == null || v.nombreCompleto().isBlank()) continue;
            visitanteRepo.save(new VisitanteFrecuente(null, a, v.nombreCompleto().trim(),
                    vacioANull(v.identificacion()), vacioANull(v.telefono()), ahora));
        }

        notificaciones.avisarNuevoRegistro(guardado);
        correo.enviar(email, guardado.getNombreCompleto(), Plantillas.asuntoRecibido(), Plantillas.recibido(guardado));
        return new RegistroDTO.Recibido(rol, Residente.PENDIENTE, guardado.esArrendatario());
    }

    @Override
    @Transactional(readOnly = true)
    public Residente login(String torre, String apto, String rol, String contrasena) {
        if (vacio(torre) || vacio(apto) || vacio(contrasena)) return null;
        String tipo = Catalogo.TIPO_HABITANTE.normalizar(rol == null ? Residente.PROPIETARIO : rol);
        for (Apartamento a : aptoRepo.findByTorreAndApto(torre.trim(), apto.trim())) {
            for (Residente r : residenteRepo.findTitulares(a.getId(), tipo)) {
                if (Residente.RECHAZADO.equals(r.getEstado()) || r.getContrasena() == null) continue;
                if (!passwordEncoder.matches(contrasena, r.getContrasena())) continue;
                if (r.puedeEntrar()) return r;
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        Residente.APROBADO.equals(r.getEstado())
                                ? "Tu registro espera la aprobación del propietario"
                                : "Tu registro espera la aprobación de la administración");
            }
        }
        passwordEncoder.matches(contrasena, hashFicticio);
        return null;
    }

    /** Propietario si el apartamento no tiene titular propietario (aprobado o pendiente); si no, arrendatario. */
    private String rolDisponible(Apartamento a) {
        if (residenteRepo.findTitulares(a.getId(), Residente.PROPIETARIO).stream().noneMatch(this::vigente)) {
            return Residente.PROPIETARIO;
        }
        if (residenteRepo.findTitulares(a.getId(), Residente.ARRENDATARIO).stream().anyMatch(this::vigente)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este apartamento ya tiene propietario y arrendatario registrados. Comunícate con la administración.");
        }
        return Residente.ARRENDATARIO;
    }

    private boolean vigente(Residente r) {
        return !Residente.RECHAZADO.equals(r.getEstado());
    }

    private Apartamento apartamento(String torre, String apto) {
        if (vacio(torre) || vacio(apto)) throw invalido("Escribe la torre y el apartamento");
        return aptoRepo.findByTorreAndApto(torre.trim(), apto.trim()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ese apartamento no está registrado en el conjunto. Revisa la torre y el número."));
    }

    private static String texto(String valor, String error) {
        if (valor == null || valor.isBlank()) throw invalido(error);
        return valor.trim();
    }

    private static String vacioANull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static boolean vacio(String v) {
        return v == null || v.isBlank();
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
