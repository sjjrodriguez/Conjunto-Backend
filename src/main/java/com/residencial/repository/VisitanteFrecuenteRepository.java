package com.residencial.repository;

import com.residencial.model.VisitanteFrecuente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VisitanteFrecuenteRepository extends JpaRepository<VisitanteFrecuente, Long> {
    List<VisitanteFrecuente> findByApartamentoIdOrderByNombreCompletoAsc(Long apartamentoId);
}
