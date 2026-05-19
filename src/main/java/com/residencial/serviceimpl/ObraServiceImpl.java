package com.residencial.serviceimpl;

import com.residencial.dto.ObraDTO;
import com.residencial.model.Apartamento;
import com.residencial.model.Obra;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ObraRepository;
import com.residencial.service.ObraService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ObraServiceImpl implements ObraService {

    private final ObraRepository repo;
    private final ApartamentoRepository aptoRepo;

    private ObraDTO toDTO(Obra o) {
        return new ObraDTO(o.getId(), o.getDescripcionAdecuacion(),
                o.getFechaInicio(), o.getFechaFinEstimada(),
                o.getEstadoObra(), o.getApartamento().getId());
    }

    private Obra toEntity(ObraDTO dto) {
        Apartamento apto = aptoRepo.findById(dto.getApartamentoId())
                .orElseThrow(() -> new RuntimeException("Apartamento no encontrado"));
        Obra o = new Obra();
        o.setDescripcionAdecuacion(dto.getDescripcionAdecuacion());
        o.setFechaInicio(dto.getFechaInicio());
        o.setFechaFinEstimada(dto.getFechaFinEstimada());
        o.setEstadoObra(dto.getEstadoObra());
        o.setApartamento(apto);
        return o;
    }

    @Override
    public List<ObraDTO> listarPorApartamento(Long apartamentoId) {
        return repo.findByApartamentoId(apartamentoId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<ObraDTO> listarEnProceso() {
        return repo.findByEstadoObra("EN_PROCESO").stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public ObraDTO guardar(ObraDTO dto) {
        return toDTO(repo.save(toEntity(dto)));
    }

    @Override
    public ObraDTO actualizar(Long id, ObraDTO dto) {
        return repo.findById(id).map(o -> {
            o.setDescripcionAdecuacion(dto.getDescripcionAdecuacion());
            o.setFechaInicio(dto.getFechaInicio());
            o.setFechaFinEstimada(dto.getFechaFinEstimada());
            o.setEstadoObra(dto.getEstadoObra());
            return toDTO(repo.save(o));
        }).orElse(null);
    }

    @Override
    public void eliminar(Long id) {
        repo.deleteById(id);
    }
}
