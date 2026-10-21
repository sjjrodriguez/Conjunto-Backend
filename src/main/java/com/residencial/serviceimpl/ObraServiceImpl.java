package com.residencial.serviceimpl;
import com.residencial.dto.ObraDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Catalogo;
import com.residencial.model.Obra;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ObraRepository;
import com.residencial.repository.PersonalObraRepository;
import com.residencial.security.AccesoService;
import com.residencial.service.ObraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
public class ObraServiceImpl implements ObraService {
    private final ObraRepository repo;
    private final ApartamentoRepository aptoRepo;
    private final PersonalObraRepository personalRepo;
    private final AccesoService acceso;
    private ObraDTO toDTO(Obra o) {
        return new ObraDTO(o.getId(), o.getDescripcionAdecuacion(),
                o.getFechaInicio(), o.getFechaFinEstimada(),
                o.getEstadoObra(), o.getApartamento().getId());
    }
    private Obra toEntity(ObraDTO dto) {
        Apartamento apto = aptoRepo.findById(dto.getApartamentoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado"));
        validarFechas(dto);
        Obra o = new Obra();
        o.setDescripcionAdecuacion(dto.getDescripcionAdecuacion());
        o.setFechaInicio(dto.getFechaInicio());
        o.setFechaFinEstimada(dto.getFechaFinEstimada());
        o.setEstadoObra(Catalogo.ESTADO_OBRA.normalizar(dto.getEstadoObra()));
        o.setApartamento(apto);
        return o;
    }
    private void validarFechas(ObraDTO dto) {
        if (dto.getFechaInicio() == null || dto.getFechaFinEstimada() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las fechas de inicio y fin son obligatorias");
        }
        if (dto.getFechaFinEstimada().isBefore(dto.getFechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha fin no puede ser anterior a la fecha de inicio");
        }
    }
    @Override
    public List<ObraDTO> listarPorApartamento(Long apartamentoId) {
        acceso.verificarApartamento(apartamentoId);
        return repo.findByApartamentoId(apartamentoId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }
    @Override
    public List<ObraDTO> listarEnProceso() {
        return repo.findByEstadoObra("EN_PROCESO").stream()
                .map(this::toDTO).collect(Collectors.toList());
    }
    @Override
    public List<ObraDTO> listarTodas() {
        return repo.findAll().stream()
                .map(this::toDTO).collect(Collectors.toList());
    }
    @Override
    public ObraDTO guardar(ObraDTO dto) {
        acceso.verificarApartamento(dto.getApartamentoId());
        return toDTO(repo.save(toEntity(dto)));
    }
    @Override
    public ObraDTO actualizar(Long id, ObraDTO dto) {
        return repo.findById(id).map(o -> {
            acceso.verificarApartamento(o.getApartamento().getId());
            validarFechas(dto);
            o.setDescripcionAdecuacion(dto.getDescripcionAdecuacion());
            o.setFechaInicio(dto.getFechaInicio());
            o.setFechaFinEstimada(dto.getFechaFinEstimada());
            o.setEstadoObra(Catalogo.ESTADO_OBRA.normalizar(dto.getEstadoObra()));
            return toDTO(repo.save(o));
        }).orElse(null);
    }
    @Override
    public ObraDTO cambiarEstado(Long id, String nuevoEstado) {
        String estado = Catalogo.ESTADO_OBRA.normalizar(nuevoEstado);
        return repo.findById(id).map(o -> {
            o.setEstadoObra(estado);
            return toDTO(repo.save(o));
        }).orElse(null);
    }
    @Override
    @Transactional
    public void eliminar(Long id) {
        // El personal pertenece a la obra: se borra con ella
        personalRepo.deleteByObraId(id);
        repo.deleteById(id);
    }
}
