package com.residencial.serviceimpl;

import com.residencial.dto.ZonaComunDTO;
import com.residencial.model.ZonaComun;
import com.residencial.repository.ZonaComunRepository;
import com.residencial.service.ZonaComunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZonaComunServiceImpl implements ZonaComunService {

    private final ZonaComunRepository repo;

    private ZonaComunDTO toDTO(ZonaComun z) {
        return new ZonaComunDTO(
                z.getId(), z.getNombre(),
                z.getEstadoRecibido(), z.getDescripcionEstado(),
                z.getEsReservable(), z.getAforo(), z.getHoraApertura(), z.getHoraCierre()
        );
    }

    private ZonaComun toEntity(ZonaComunDTO dto) {
        validar(dto);
        return new ZonaComun(
                dto.getId(), dto.getNombre(),
                dto.getEstadoRecibido(), dto.getDescripcionEstado(),
                dto.getEsReservable() != null ? dto.getEsReservable() : false,
                dto.getAforo(), dto.getHoraApertura(), dto.getHoraCierre()
        );
    }

    /** Aforo de al menos una persona y, si hay horario, que abra antes de cerrar. */
    private static void validar(ZonaComunDTO dto) {
        if (dto.getAforo() != null && dto.getAforo() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El aforo debe ser de al menos una persona");
        }
        if (dto.getHoraApertura() != null && dto.getHoraCierre() != null
                && !dto.getHoraCierre().isAfter(dto.getHoraApertura())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La hora de cierre debe ser después de la de apertura");
        }
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
        validar(dto);
        return repo.findById(id).map(z -> {
            z.setNombre(dto.getNombre());
            z.setAforo(dto.getAforo());
            z.setHoraApertura(dto.getHoraApertura());
            z.setHoraCierre(dto.getHoraCierre());
            z.setEstadoRecibido(dto.getEstadoRecibido());
            z.setDescripcionEstado(dto.getDescripcionEstado());
            z.setEsReservable(dto.getEsReservable() != null ? dto.getEsReservable() : false);
            return toDTO(repo.save(z));
        }).orElse(null);
    }

    @Override
    public void eliminar(Long id) {
        repo.deleteById(id);
    }
}