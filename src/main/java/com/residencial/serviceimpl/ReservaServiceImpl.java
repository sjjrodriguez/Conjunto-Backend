package com.residencial.serviceimpl;

import com.residencial.dto.ReservaDTO;
import com.residencial.model.Apartamento;
import com.residencial.model.Reserva;
import com.residencial.model.ZonaComun;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ReservaRepository;
import com.residencial.repository.ZonaComunRepository;
import com.residencial.service.ReservaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservaServiceImpl implements ReservaService {

    private final ReservaRepository repo;
    private final ApartamentoRepository aptoRepo;
    private final ZonaComunRepository zonaRepo;

    private ReservaDTO toDTO(Reserva r) {
        return new ReservaDTO(r.getId(), r.getFecha(), r.getHora(),
                r.getEstado(), r.getZonaComun().getId(), r.getApartamento().getId());
    }

    @Override
    public List<ReservaDTO> listarTodas() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<ReservaDTO> listarPorApartamento(Long apartamentoId) {
        return repo.findByApartamentoId(apartamentoId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public ReservaDTO guardar(ReservaDTO dto) {
        Apartamento apto = aptoRepo.findById(dto.getApartamentoId())
                .orElseThrow(() -> new RuntimeException("Apartamento no encontrado"));
        ZonaComun zona = zonaRepo.findById(dto.getZonaComunId())
                .orElseThrow(() -> new RuntimeException("Zona Común no encontrada"));
        Reserva r = new Reserva(null, dto.getFecha(), dto.getHora(), "Pendiente", zona, apto);
        return toDTO(repo.save(r));
    }

    @Override
    public ReservaDTO cambiarEstado(Long id, String nuevoEstado) {
        return repo.findById(id).map(r -> {
            r.setEstado(nuevoEstado);
            return toDTO(repo.save(r));
        }).orElse(null);
    }

    @Override
    public void eliminar(Long id) {
        repo.deleteById(id);
    }
}
