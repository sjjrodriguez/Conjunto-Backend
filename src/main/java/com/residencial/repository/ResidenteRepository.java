package com.residencial.repository;

import com.residencial.model.Residente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResidenteRepository extends JpaRepository<Residente, Long> {
    List<Residente> findByApartamentoId(long idApartamento);
}
