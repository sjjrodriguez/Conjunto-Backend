package com.residencial.repository;

import com.residencial.model.CupoApartamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CupoApartamentoRepository extends JpaRepository<CupoApartamento, CupoApartamento.Clave> {
    List<CupoApartamento> findByApartamentoId(Long apartamentoId);
}
