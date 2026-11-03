package com.residencial.repository;

import com.residencial.model.AuditoriaPlataforma;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditoriaPlataformaRepository extends JpaRepository<AuditoriaPlataforma, Long> {
    List<AuditoriaPlataforma> findAllByOrderByIdDesc(Pageable pagina);

    List<AuditoriaPlataforma> findByConjuntoIdOrderByIdDesc(Long conjuntoId, Pageable pagina);
}
