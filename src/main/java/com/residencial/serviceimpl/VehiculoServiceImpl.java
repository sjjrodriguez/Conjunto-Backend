package com.residencial.serviceimpl;

import com.residencial.dto.CupoVehiculoDTO;
import com.residencial.dto.VehiculoDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Catalogo;
import com.residencial.model.CupoVehiculo;
import com.residencial.model.Vehiculo;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.CupoVehiculoRepository;
import com.residencial.repository.VehiculoRepository;
import com.residencial.model.CupoApartamento;
import com.residencial.model.Parqueadero;
import com.residencial.repository.CupoApartamentoRepository;
import com.residencial.security.AccesoService;
import com.residencial.security.ConfiguracionService;
import com.residencial.security.TokenService;
import org.springframework.security.access.AccessDeniedException;
import com.residencial.service.NotificacionService;
import com.residencial.service.VehiculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehiculoServiceImpl implements VehiculoService {

    /** Tipos que deben llevar placa. */
    private static final Set<String> CON_PLACA = Set.of("Carro", "Moto");
    /** Estados que ocupan cupo: lo rechazado no cuenta. */
    private static final List<String> OCUPAN_CUPO = List.of(Vehiculo.PENDIENTE, Vehiculo.APROBADO);
    private static final int MAXIMO_CUPO = 20;

    private final VehiculoRepository repo;
    private final ApartamentoRepository aptoRepo;
    private final CupoVehiculoRepository cupoRepo;
    private final AccesoService acceso;
    private final NotificacionService notificaciones;
    private final ParqueaderoServiceImpl parqueaderos;
    private final CupoApartamentoRepository cuposApto;
    private final ConfiguracionService configuracion;

    private VehiculoDTO toDTO(Vehiculo v) {
        return toDTO(v, parqueaderos.deVehiculos(List.of(v.getId())).get(v.getId()));
    }

    private VehiculoDTO toDTO(Vehiculo v, Parqueadero p) {
        Apartamento a = v.getApartamento();
        return new VehiculoDTO(v.getId(), v.getPlaca(), v.getMarca(), v.getColor(), v.getTipo(), a.getId(),
                v.getEstado(), v.getMotivoRechazo(), a.getTorre(), a.getApto(),
                p == null ? null : p.getId(), p == null ? null : p.getNumero());
    }

    /** Con el parqueadero de cada uno, en una sola consulta. */
    private List<VehiculoDTO> toDTOs(List<Vehiculo> lista) {
        Map<Long, Parqueadero> deCada = parqueaderos.deVehiculos(lista.stream().map(Vehiculo::getId).toList());
        return lista.stream().map(v -> toDTO(v, deCada.get(v.getId()))).toList();
    }

    /**
     * Registrar, editar y quitar vehículos del apartamento es del propietario (aunque esté arrendado)
     * o de la administración. El arrendatario solo consulta. Si el conjunto lo pide
     * (vehiculos_alta_solo_admin), el propietario tampoco: lo hace la administración.
     */
    private void exigirGestion() {
        String rol = acceso.tokenActual().map(t -> t.getClaimAsString(TokenService.CLAIM_ROL)).orElse(null);
        if (rol == null || TokenService.ROL_ADMIN.equals(rol)) return; // administración, o modo transición sin token
        if (!TokenService.ROL_PROPIETARIO.equals(rol)) {
            throw new AccessDeniedException("Los vehículos del apartamento los gestiona el propietario");
        }
        if (configuracion.vehiculosAltaSoloAdmin()) {
            throw new AccessDeniedException("En este conjunto los vehículos los registra la administración");
        }
    }

    @Override
    public List<VehiculoDTO> listarPorApartamento(Long apartamentoId) {
        acceso.verificarApartamento(apartamentoId);
        return toDTOs(repo.findByApartamentoId(apartamentoId));
    }

    @Override
    @Transactional // el vehículo y su notificación se guardan juntos
    public VehiculoDTO guardar(VehiculoDTO dto) {
        acceso.verificarApartamento(dto.getApartamentoId());
        exigirGestion();
        Apartamento apto = aptoRepo.findById(dto.getApartamentoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado"));
        String tipo = Catalogo.TIPO_VEHICULO.normalizar(dto.getTipo());
        String placa = vacioANull(dto.getPlaca());
        if (placa == null && CON_PLACA.contains(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La placa es obligatoria para " + tipo.toLowerCase(Locale.ROOT));
        }

        // El administrador registra directo; lo que registra el residente espera su aprobación
        boolean esAdmin = acceso.esAdminActual();
        if (!esAdmin) verificarCupo(apto.getId(), tipo);
        Vehiculo v = new Vehiculo(null, placa == null ? null : placa.toUpperCase(Locale.ROOT),
                vacioANull(dto.getMarca()), vacioANull(dto.getColor()), tipo, apto,
                esAdmin ? Vehiculo.APROBADO : Vehiculo.PENDIENTE, null);
        Vehiculo guardado = repo.save(v);
        notificaciones.avisarNuevoVehiculo(guardado);
        return toDTO(guardado);
    }

    @Override
    @Transactional
    public VehiculoDTO actualizar(Long id, VehiculoDTO dto) {
        Vehiculo v = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Vehículo no encontrado: " + id));
        acceso.verificarApartamento(v.getApartamento().getId());
        exigirGestion();
        String tipo = Catalogo.TIPO_VEHICULO.normalizar(dto.getTipo());
        String placa = vacioANull(dto.getPlaca());
        if (placa == null && CON_PLACA.contains(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La placa es obligatoria para " + tipo.toLowerCase(Locale.ROOT));
        }
        boolean esAdmin = acceso.esAdminActual();
        // Si cambia de tipo ocupa un cupo de otro tipo
        if (!esAdmin && !tipo.equals(v.getTipo())) verificarCupo(v.getApartamento().getId(), tipo);
        v.setTipo(tipo);
        v.setPlaca(placa == null ? null : placa.toUpperCase(Locale.ROOT));
        v.setMarca(vacioANull(dto.getMarca()));
        v.setColor(vacioANull(dto.getColor()));
        if (!esAdmin) {
            // Lo que cambia el residente lo vuelve a revisar la administración
            v.setEstado(Vehiculo.PENDIENTE);
            v.setMotivoRechazo(null);
        }
        Vehiculo guardado = repo.save(v);
        notificaciones.avisarNuevoVehiculo(guardado);
        return toDTO(guardado);
    }

    private void verificarCupo(Long apartamentoId, String tipo) {
        int maximo = cuposApto.findById(new CupoApartamento.Clave(apartamentoId, tipo))
                .map(CupoApartamento::getMaximo).orElseGet(() -> maximo(tipo));
        long ocupados = repo.countByApartamentoIdAndTipoAndEstadoIn(apartamentoId, tipo, OCUPAN_CUPO);
        if (ocupados >= maximo) {
            String nombre = tipo.toLowerCase(Locale.ROOT);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, maximo == 0
                    ? "El conjunto no permite registrar vehículos de tipo " + nombre
                    : "Ya registraste el máximo de vehículos de tipo " + nombre + " (" + maximo
                        + "). Elimina uno o pide más cupo a la administración");
        }
    }

    @Override
    public void eliminar(Long id) {
        repo.findById(id).ifPresent(v -> {
            acceso.verificarApartamento(v.getApartamento().getId());
            exigirGestion();
            parqueaderos.liberarDe(v.getId());
            repo.delete(v);
        });
    }

    @Override
    public List<VehiculoDTO> listarPorEstado(String estado) {
        return toDTOs(repo.findByEstadoOrderByIdAsc(Catalogo.ESTADO_VEHICULO.normalizar(estado)));
    }

    @Override
    @Transactional // el cambio y la notificación al residente van juntos
    public VehiculoDTO cambiarEstado(Long id, String estado, String motivo) {
        return cambiarEstado(id, estado, motivo, null);
    }

    /** Aprobar (con el parqueadero que se le asigna, si hay) o rechazar (libera el que tuviera). */
    @Override
    @Transactional
    public VehiculoDTO cambiarEstado(Long id, String estado, String motivo, Long parqueaderoId) {
        String nuevo = Catalogo.ESTADO_VEHICULO.normalizar(estado);
        if (Vehiculo.PENDIENTE.equals(nuevo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El vehículo solo se puede aprobar o rechazar");
        }
        Vehiculo v = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Vehículo no encontrado"));
        if (nuevo.equals(v.getEstado()) && parqueaderoId == null) return toDTO(v);
        v.setEstado(nuevo);
        v.setMotivoRechazo(Vehiculo.RECHAZADO.equals(nuevo) ? vacioANull(motivo) : null);
        Vehiculo guardado = repo.save(v);
        String parqueadero = null;
        if (Vehiculo.APROBADO.equals(nuevo)) parqueadero = parqueaderos.asignarAlAprobar(guardado, parqueaderoId);
        else parqueaderos.liberarDe(guardado.getId());
        notificaciones.avisarVehiculoRevisado(guardado, parqueadero);
        return toDTO(guardado);
    }

    @Override
    public List<CupoVehiculoDTO> cupos() {
        Map<String, Integer> guardados = cupoRepo.findAll().stream()
                .collect(Collectors.toMap(CupoVehiculo::getTipo, CupoVehiculo::getMaximo));
        return CupoVehiculo.POR_DEFECTO.entrySet().stream()
                .map(e -> new CupoVehiculoDTO(e.getKey(), guardados.getOrDefault(e.getKey(), e.getValue()),
                        CON_PLACA.contains(e.getKey())))
                .collect(Collectors.toList());
    }

    /** Los cupos que rigen para un apartamento: los suyos o, si no tiene, los generales. */
    @Override
    @Transactional(readOnly = true)
    public List<CupoVehiculoDTO> cuposDe(Long apartamentoId) {
        acceso.verificarApartamentoOCartera(apartamentoId);
        Map<String, Integer> propios = cuposApto.findByApartamentoId(apartamentoId).stream()
                .collect(Collectors.toMap(CupoApartamento::getTipo, CupoApartamento::getMaximo));
        return cupos().stream().map(c -> propios.containsKey(c.getTipo())
                ? new CupoVehiculoDTO(c.getTipo(), propios.get(c.getTipo()), c.getRequierePlaca(), true)
                : new CupoVehiculoDTO(c.getTipo(), c.getMaximo(), c.getRequierePlaca(), false)).toList();
    }

    /** Administrador: cupos propios del apartamento; maximo null vuelve al cupo general. */
    @Override
    @Transactional
    public List<CupoVehiculoDTO> guardarCuposDe(Long apartamentoId, List<CupoVehiculoDTO> cupos) {
        if (!aptoRepo.existsById(apartamentoId)) throw new RecursoNoEncontradoException("Apartamento no encontrado");
        for (CupoVehiculoDTO c : cupos) {
            String tipo = Catalogo.TIPO_VEHICULO.normalizar(c.getTipo());
            if (c.getMaximo() == null) {
                cuposApto.deleteById(new CupoApartamento.Clave(apartamentoId, tipo));
                continue;
            }
            if (c.getMaximo() < 0 || c.getMaximo() > MAXIMO_CUPO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El cupo de " + tipo.toLowerCase(Locale.ROOT) + " debe estar entre 0 y " + MAXIMO_CUPO);
            }
            cuposApto.save(new CupoApartamento(apartamentoId, tipo, c.getMaximo()));
        }
        return cuposDe(apartamentoId);
    }

    @Override
    @Transactional
    public List<CupoVehiculoDTO> guardarCupos(List<CupoVehiculoDTO> cupos) {
        for (CupoVehiculoDTO c : cupos) {
            String tipo = Catalogo.TIPO_VEHICULO.normalizar(c.getTipo());
            if (c.getMaximo() == null || c.getMaximo() < 0 || c.getMaximo() > MAXIMO_CUPO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El cupo de " + tipo.toLowerCase(Locale.ROOT) + " debe estar entre 0 y " + MAXIMO_CUPO);
            }
            cupoRepo.save(new CupoVehiculo(tipo, c.getMaximo()));
        }
        return cupos();
    }

    private int maximo(String tipo) {
        return cupoRepo.findById(tipo).map(CupoVehiculo::getMaximo)
                .orElse(CupoVehiculo.POR_DEFECTO.getOrDefault(tipo, 1));
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
