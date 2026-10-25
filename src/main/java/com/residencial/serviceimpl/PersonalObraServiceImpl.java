package com.residencial.serviceimpl;

import com.residencial.dto.PersonalObraDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Obra;
import com.residencial.model.PersonalObra;
import com.residencial.repository.ObraRepository;
import com.residencial.repository.PersonalObraRepository;
import com.residencial.security.AccesoService;
import com.residencial.service.PersonalObraService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonalObraServiceImpl implements PersonalObraService {

    private final PersonalObraRepository repo;
    private final ObraRepository obraRepo;
    private final AccesoService acceso;

    private PersonalObraDTO toDTO(PersonalObra p) {
        String torre = "";
        String apto = "";
        if (p.getObra() != null && p.getObra().getApartamento() != null) {
            torre = p.getObra().getApartamento().getTorre();
            apto = p.getObra().getApartamento().getApto();
        }
        return new PersonalObraDTO(
                p.getId(),
                p.getNombreTrabajador(),
                p.getCedula(),
                p.getArlEstado(),
                p.getObra().getId(),
                torre,
                apto
        );
    }

    @Override
    public List<PersonalObraDTO> listarTodos() {
        return repo.findAll().stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<PersonalObraDTO> listarPorObra(Long obraId) {
        obraRepo.findById(obraId).ifPresent(o -> acceso.verificarApartamento(o.getApartamento().getId()));
        return repo.findByObraId(obraId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public PersonalObraDTO guardar(PersonalObraDTO dto) {
        // Con su apartamento: toDTO necesita torre y apto
        Obra obra = obraRepo.findConApartamentoById(dto.getObraId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra no encontrada: " + dto.getObraId()));
        acceso.verificarApartamento(obra.getApartamento().getId());
        PersonalObra p = new PersonalObra(null, dto.getNombreTrabajador(),
                dto.getCedula(), dto.getArlEstado(), obra);
        return toDTO(repo.save(p));
    }

    @Override
    @Transactional
    public PersonalObraDTO actualizar(Long id, PersonalObraDTO dto) {
        PersonalObra p = buscar(id);
        Obra obra = obraRepo.findConApartamentoById(p.getObra().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra no encontrada"));
        acceso.verificarApartamento(obra.getApartamento().getId());
        p.setNombreTrabajador(dto.getNombreTrabajador());
        p.setCedula(dto.getCedula());
        p.setArlEstado(dto.getArlEstado());
        p.setObra(obra);
        return toDTO(repo.save(p));
    }

    @Override
    public void eliminar(Long id) {
        PersonalObra p = buscar(id);
        obraRepo.findById(p.getObra().getId()).ifPresent(o -> acceso.verificarApartamento(o.getApartamento().getId()));
        repo.delete(p);
    }

    private PersonalObra buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Trabajador no encontrado: " + id));
    }
}