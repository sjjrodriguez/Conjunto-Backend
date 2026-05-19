package com.residencial.repository;

import com.residencial.model.Obra;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ObraRepository extends JpaRepository<Obra, Long> {
    List<Obra> findByApartamentoId(Long apartamentoId);
    List<Obra> findByEstadoObra(String estadoObra);
}
