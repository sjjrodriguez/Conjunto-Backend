package com.residencial.serviceimpl;

import com.residencial.dto.VehiculoDTO;
import com.residencial.model.Apartamento;
import com.residencial.model.Vehiculo;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.VehiculoRepository;
import com.residencial.service.VehiculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehiculoServiceImpl implements VehiculoService {

    private final VehiculoRepository repo;
    private final ApartamentoRepository aptoRepo;

    private VehiculoDTO toDTO(Vehiculo v) {
        return new VehiculoDTO(v.getId(), v.getPlaca(), v.getMarca(),
                v.getColor(), v.getTipo(), v.getApartamento().getId());
    }

    @Override
    public List<VehiculoDTO> listarPorApartamento(Long apartamentoId) {
        return repo.findByApartamentoId(apartamentoId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public VehiculoDTO guardar(VehiculoDTO dto) {
        Apartamento apto = aptoRepo.findById(dto.getApartamentoId())
                .orElseThrow(() -> new RuntimeException("Apartamento no encontrado"));
        Vehiculo v = new Vehiculo(null, dto.getPlaca(), dto.getMarca(),
                dto.getColor(), dto.getTipo(), apto);
        return toDTO(repo.save(v));
    }

    @Override
    public void eliminar(Long id) {
        repo.deleteById(id);
    }
}
