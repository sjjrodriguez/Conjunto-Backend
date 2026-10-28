package com.residencial.repository;

import com.residencial.model.EmpresaPrestadora;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmpresaPrestadoraRepository extends JpaRepository<EmpresaPrestadora, Long> {
    Optional<EmpresaPrestadora> findByNombreIgnoreCase(String nombre);
    List<EmpresaPrestadora> findAllByOrderByNombreAsc();
}
