package com.residencial.serviceimpl;

import com.residencial.dto.PersonalObraDTO;
import com.residencial.model.Obra;
import com.residencial.model.PersonalObra;
import com.residencial.repository.ObraRepository;
import com.residencial.repository.PersonalObraRepository;
import com.residencial.service.PersonalObraService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonalObraServiceImpl implements PersonalObraService {

    private final PersonalObraRepository repo;
    private final ObraRepository obraRepo;

    private PersonalObraDTO toDTO(PersonalObra p) {
        return new PersonalObraDTO(p.getId(), p.getNombreTrabajador(),
                p.getCedula(), p.getArlEstado(), p.getObra().getId());
    }

    @Override
    public List<PersonalObraDTO> listarPorObra(Long obraId) {
        return repo.findByObraId(obraId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public PersonalObraDTO guardar(PersonalObraDTO dto) {
        Obra obra = obraRepo.findById(dto.getObraId())
                .orElseThrow(() -> new RuntimeException("Obra no encontrada: " + dto.getObraId()));
        PersonalObra p = new PersonalObra(null, dto.getNombreTrabajador(),
                dto.getCedula(), dto.getArlEstado(), obra);
        return toDTO(repo.save(p));
    }

    @Override
    public void eliminar(Long id) {
        repo.deleteById(id);
    }
}
