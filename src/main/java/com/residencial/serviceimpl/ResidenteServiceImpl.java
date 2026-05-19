package com.residencial.serviceimpl;

import com.residencial.dto.ResidenteDTO;
import com.residencial.model.Apartamento;
import com.residencial.model.Residente;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ResidenteRepository;
import com.residencial.service.ResidenteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResidenteServiceImpl implements ResidenteService {

    private final ResidenteRepository repo;
    private final ApartamentoRepository aptoRepo;

    private ResidenteDTO toDTO(Residente r) {
        return new ResidenteDTO(
            r.getId(), r.getNombreCompleto(), r.getTelefono(),
            r.getIdentificacion(), r.getTipoHabitante(),
            r.getApartamento().getId()
        );
    }

    private Residente toEntity(ResidenteDTO dto) {
        Apartamento apto = aptoRepo.findById(dto.getApartamentoId())
                .orElseThrow(() -> new RuntimeException("Apartamento no encontrado: " + dto.getApartamentoId()));
        Residente r = new Residente();
        r.setNombreCompleto(dto.getNombreCompleto());
        r.setTelefono(dto.getTelefono());
        r.setIdentificacion(dto.getIdentificacion());
        r.setTipoHabitante(dto.getTipoHabitante());
        r.setApartamento(apto);
        return r;
    }

    @Override
    public List<ResidenteDTO> listarPorApartamento(Long apartamentoId) {
        return repo.findByApartamentoId(apartamentoId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public ResidenteDTO guardar(ResidenteDTO dto) {
        return toDTO(repo.save(toEntity(dto)));
    }

    @Override
    public ResidenteDTO actualizar(Long id, ResidenteDTO dto) {
        return repo.findById(id).map(r -> {
            r.setNombreCompleto(dto.getNombreCompleto());
            r.setTelefono(dto.getTelefono());
            r.setIdentificacion(dto.getIdentificacion());
            r.setTipoHabitante(dto.getTipoHabitante());
            return toDTO(repo.save(r));
        }).orElse(null);
    }

    @Override
    public void eliminar(Long id) {
        repo.deleteById(id);
    }
}
