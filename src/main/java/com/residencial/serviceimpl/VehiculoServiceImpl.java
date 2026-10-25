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
import com.residencial.security.AccesoService;
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

    private VehiculoDTO toDTO(Vehiculo v) {
        Apartamento a = v.getApartamento();
        return new VehiculoDTO(v.getId(), v.getPlaca(), v.getMarca(), v.getColor(), v.getTipo(), a.getId(),
                v.getEstado(), v.getMotivoRechazo(), a.getTorre(), a.getApto());
    }

    @Override
    public List<VehiculoDTO> listarPorApartamento(Long apartamentoId) {
        acceso.verificarApartamento(apartamentoId);
        return repo.findByApartamentoId(apartamentoId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional // el vehículo y su notificación se guardan juntos
    public VehiculoDTO guardar(VehiculoDTO dto) {
        acceso.verificarApartamento(dto.getApartamentoId());
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

    private void verificarCupo(Long apartamentoId, String tipo) {
        int maximo = maximo(tipo);
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
            repo.delete(v);
        });
    }

    @Override
    public List<VehiculoDTO> listarPorEstado(String estado) {
        return repo.findByEstadoOrderByIdAsc(Catalogo.ESTADO_VEHICULO.normalizar(estado)).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional // el cambio y la notificación al residente van juntos
    public VehiculoDTO cambiarEstado(Long id, String estado, String motivo) {
        String nuevo = Catalogo.ESTADO_VEHICULO.normalizar(estado);
        if (Vehiculo.PENDIENTE.equals(nuevo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El vehículo solo se puede aprobar o rechazar");
        }
        Vehiculo v = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Vehículo no encontrado"));
        if (nuevo.equals(v.getEstado())) return toDTO(v);
        v.setEstado(nuevo);
        v.setMotivoRechazo(Vehiculo.RECHAZADO.equals(nuevo) ? vacioANull(motivo) : null);
        Vehiculo guardado = repo.save(v);
        notificaciones.avisarVehiculoRevisado(guardado);
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
