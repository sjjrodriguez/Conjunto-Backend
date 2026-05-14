package com.residencial.repository;

import com.residencial.model.Apartamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApartamentoRepository extends JpaRepository<Apartamento, Long> {
    List<Apartamento> findByTorreId(long idTorre);
}
