package com.residencial.serviceimpl;

import com.residencial.dto.ZonaComunDTO;
import com.residencial.model.ZonaComun;
import com.residencial.repository.ZonaComunRepository;
import com.residencial.service.ZonaComunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZonaComunServiceImpl implements ZonaComunService {

    private final ZonaComunRepository repo;

    private ZonaComunDTO toDTO(ZonaComun z) {
        return new ZonaComunDTO(z.getId(), z.getNombre(),
                z.getEstadoRecibido(), z.getDescripcionEstado());
    }

    private ZonaComun toEntity(ZonaComunDTO dto) {
        return new ZonaComun(dto.getId(), dto.getNombre(),
                dto.getEstadoRecibido(), dto.getDescripcionEstado());
    }

    @Override
    public List<ZonaComunDTO> listarTodas() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public ZonaComunDTO buscarPorId(Long id) {
        return repo.findById(id).map(this::toDTO).orElse(null);
    }

    @Override
    public ZonaComunDTO guardar(ZonaComunDTO dto) {
        return toDTO(repo.save(toEntity(dto)));
    }

    @Override
    public ZonaComunDTO actualizar(Long id, ZonaComunDTO dto) {
        return repo.findById(id).map(z -> {
            z.setNombre(dto.getNombre());
            z.setEstadoRecibido(dto.getEstadoRecibido());
            z.setDescripcionEstado(dto.getDescripcionEstado());
            return toDTO(repo.save(z));
        }).orElse(null);
    }

    @Override
    public void eliminar(Long id) {
        repo.deleteById(id);
    }
}
