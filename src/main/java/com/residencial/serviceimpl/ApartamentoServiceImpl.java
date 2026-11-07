package com.residencial.serviceimpl;

import com.residencial.dto.ApartamentoDTO;
import com.residencial.dto.ApartamentoRequest;
import com.residencial.model.Apartamento;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.repository.*;
import org.springframework.transaction.annotation.Transactional;
import com.residencial.model.Residente;
import com.residencial.security.AccesoService;
import com.residencial.security.EstadoApartamento;
import com.residencial.service.ApartamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApartamentoServiceImpl implements ApartamentoService {

    private static final int MIN_CONTRASENA = 6;

    private final ApartamentoRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final AccesoService acceso;
    private final ResidenteRepository residentes;
    private final VehiculoRepository vehiculos;
    private final ObraRepository obras;
    private final ReservaRepository reservas;
    private final CuotaRepository cuotas;
    private final PagoRepository pagos;
    private final DispositivoRepository dispositivos;
    private final EstadoApartamento estado;
    private final CupoApartamentoRepository cuposApto;
    private final com.residencial.security.SesionVersion sesiones;

    private ApartamentoDTO toDTO(Apartamento a) {
        ApartamentoDTO dto = new ApartamentoDTO(a.getId(), a.getTorre(), a.getApto());
        dto.setTipoOcupacion(a.getTipoOcupacion());
        dto.setArrendatarioVeCartera(a.isArrendatarioVeCartera());
        dto.setArrendatarioPuedePagar(a.isArrendatarioPuedePagar());
        dto.setArrendatarioVeObras(a.isArrendatarioVeObras());
        return dto;
    }

    /** Con citófonos (el primero, por defecto, el teléfono del propietario) y si está arrendado. */
    private ApartamentoDTO detalle(Apartamento a) {
        String citofono1 = a.getCitofono1() != null ? a.getCitofono1()
                : residentes.findTitulares(a.getId(), Residente.PROPIETARIO).stream()
                        .filter(Residente::puedeEntrar).map(Residente::getTelefono).filter(t -> t != null && !t.isBlank())
                        .findFirst().orElse(null);
        ApartamentoDTO dto = toDTO(a);
        dto.setCitofono1(citofono1);
        dto.setCitofono2(a.getCitofono2());
        dto.setArrendado(estado.arrendado(a.getId()));
        return dto;
    }

    private String hashValidado(String contrasena) {
        if (contrasena == null || contrasena.isBlank() || contrasena.length() < MIN_CONTRASENA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener al menos " + MIN_CONTRASENA + " caracteres");
        }
        return passwordEncoder.encode(contrasena);
    }

    @Override
    public List<ApartamentoDTO> listarTodos() {
        // Una consulta para saber cuáles están arrendados (el directorio lo muestra)
        java.util.Set<Long> arrendados = residentes.apartamentosArrendados();
        return repo.findAll().stream().map(a -> {
            ApartamentoDTO dto = toDTO(a);
            dto.setArrendado(arrendados.contains(a.getId()));
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public ApartamentoDTO buscarPorId(Long id) {
        acceso.verificarApartamento(id);
        return repo.findById(id).map(this::detalle).orElse(null);
    }

    @Override
    public ApartamentoDTO guardar(ApartamentoRequest data) {
        Apartamento a = new Apartamento(null, data.getTorre(), data.getApto(), hashValidado(data.getContrasena()));
        return toDTO(repo.save(a));
    }

    @Override
    public ApartamentoDTO actualizar(Long id, ApartamentoRequest data) {
        return repo.findById(id).map(a -> {
            a.setTorre(data.getTorre());
            a.setApto(data.getApto());
            // Si no se envía contraseña se conserva la actual
            if (data.getContrasena() != null && !data.getContrasena().isBlank()) {
                a.setContrasena(hashValidado(data.getContrasena()));
            }
            return toDTO(repo.save(a));
        }).orElse(null);
    }

    @Override
    public ApartamentoDTO cambiarCitofonos(Long id, String citofono1, String citofono2) {
        acceso.verificarApartamento(id);
        estado.exigirQueViva(id);
        Apartamento a = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado: " + id));
        a.setCitofono1(telefono(citofono1));
        a.setCitofono2(telefono(citofono2));
        return detalle(repo.save(a));
    }

    private static String telefono(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String t = valor.trim();
        if (!t.matches("^[0-9+ ()-]{5,30}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El teléfono del citófono no es válido");
        }
        return t;
    }

    @Override
    @Transactional
    public ApartamentoDTO cambiarOcupacionYBanderas(Long id, ApartamentoDTO.OcupacionYBanderas cambio) {
        Apartamento a = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado: " + id));
        if (cambio.tipoOcupacion() != null) {
            String tipo = cambio.tipoOcupacion().trim().toUpperCase(java.util.Locale.ROOT);
            if (!List.of(Apartamento.OCUPA_PROPIETARIO, Apartamento.OCUPA_ARRENDATARIO, Apartamento.OCUPA_DESOCUPADO).contains(tipo)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La ocupación debe ser PROPIETARIO, ARRENDATARIO o DESOCUPADO");
            }
            if (cambio.arrendatario() != null && !Apartamento.OCUPA_ARRENDATARIO.equals(tipo)) {
                quitarArrendatario(a.getId(), cambio.arrendatario().trim().toUpperCase(java.util.Locale.ROOT));
            }
            a.setTipoOcupacion(tipo);
        }
        if (cambio.arrendatarioVeCartera() != null) a.setArrendatarioVeCartera(cambio.arrendatarioVeCartera());
        if (cambio.arrendatarioPuedePagar() != null) a.setArrendatarioPuedePagar(cambio.arrendatarioPuedePagar());
        if (cambio.arrendatarioVeObras() != null) a.setArrendatarioVeObras(cambio.arrendatarioVeObras());
        return detalle(repo.save(a));
    }

    /**
     * P11: el apartamento deja de estar arrendado. DESACTIVAR: el arrendatario y su hogar quedan
     * INACTIVO (no entran; se pueden reactivar). ELIMINAR: se borran. En los dos casos se cierran
     * sus sesiones y se quitan sus celulares registrados.
     */
    private void quitarArrendatario(Long apartamentoId, String accion) {
        if (!List.of("DESACTIVAR", "ELIMINAR").contains(accion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica si el arrendatario se desactiva o se elimina");
        }
        List<Residente> hogar = residentes.findByApartamentoIdOrderByIdAsc(apartamentoId).stream()
                .filter(r -> r.esArrendatario() && !Residente.RECHAZADO.equals(r.getEstado()))
                .toList();
        for (Residente r : hogar) {
            sesiones.revocar("residente:" + r.getId());
            if ("ELIMINAR".equals(accion)) residentes.delete(r);
            else { r.setEstado(Residente.INACTIVO); residentes.save(r); }
        }
        dispositivos.deleteAll(dispositivos.findByApartamentoId(apartamentoId).stream()
                .filter(d -> com.residencial.security.TokenService.ROL_ARRENDATARIO.equals(d.getRol())).toList());
    }

    @Override
    @Transactional
    public ApartamentoDTO activarArrendatario(Long id) {
        Apartamento a = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado: " + id));
        List<Residente> todos = residentes.findByApartamentoIdOrderByIdAsc(id);
        List<Residente> inactivos = todos.stream()
                .filter(r -> r.esArrendatario() && Residente.INACTIVO.equals(r.getEstado())).toList();
        if (inactivos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay un arrendatario desactivado en este apartamento");
        }
        boolean hayOtro = todos.stream().anyMatch(r -> r.esArrendatario() && r.isTitular()
                && !Residente.INACTIVO.equals(r.getEstado()) && !Residente.RECHAZADO.equals(r.getEstado()));
        if (hayOtro) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El apartamento ya tiene otro arrendatario: quítalo primero");
        }
        inactivos.forEach(r -> { r.setEstado(Residente.APROBADO); residentes.save(r); });
        a.setTipoOcupacion(Apartamento.OCUPA_ARRENDATARIO);
        return detalle(repo.save(a));
    }

    @Override
    public boolean cambiarContrasena(Long id, String nuevaContrasena) {
        return repo.findById(id).map(a -> {
            a.setContrasena(hashValidado(nuevaContrasena));
            repo.save(a);
            return true;
        }).orElse(false);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Apartamento a = repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado: " + id));
        if (residentes.existsByApartamentoId(id) || vehiculos.existsByApartamentoId(id)
                || obras.existsByApartamentoId(id) || reservas.existsByApartamentoId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Primero quita sus residentes, vehículos, obras y reservas");
        }
        // Lo que se genera solo (cuotas, cartera, celulares) se va con el apartamento
        cuotas.deleteAll(cuotas.findByApartamentoIdOrderByPeriodoDesc(id));
        pagos.findByApartamentoId(id).ifPresent(pagos::delete);
        dispositivos.deleteAll(dispositivos.findByApartamentoId(id));
        cuposApto.deleteAll(cuposApto.findByApartamentoId(id));
        repo.delete(a);
    }
}
