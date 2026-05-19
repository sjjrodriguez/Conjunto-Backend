package com.residencial.repository;

import com.residencial.model.Residente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResidenteRepository extends JpaRepository<Residente, Long> {
    List<Residente> findByApartamentoId(Long apartamentoId);
}
