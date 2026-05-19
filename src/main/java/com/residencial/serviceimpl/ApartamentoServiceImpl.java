package com.residencial.serviceimpl;

import com.residencial.model.Apartamento;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.service.ApartamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApartamentoServiceImpl implements ApartamentoService {

    private final ApartamentoRepository repo;

    @Override
    public List<Apartamento> listarTodos() {
        return repo.findAll();
    }

    @Override
    public Apartamento buscarPorId(Long id) {
        return repo.findById(id).orElse(null);
    }

    @Override
    public Apartamento guardar(Apartamento apartamento) {
        return repo.save(apartamento);
    }

    @Override
    public Apartamento actualizar(Long id, Apartamento data) {
        return repo.findById(id).map(a -> {
            a.setTorre(data.getTorre());
            a.setApto(data.getApto());
            a.setContrasena(data.getContrasena());
            return repo.save(a);
        }).orElse(null);
    }

    @Override
    public void eliminar(Long id) {
        repo.deleteById(id);
    }
}
