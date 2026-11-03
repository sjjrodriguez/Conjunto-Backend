package com.residencial.repository;

import com.residencial.model.OperadorPlataforma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OperadorPlataformaRepository extends JpaRepository<OperadorPlataforma, Long> {
    Optional<OperadorPlataforma> findByUsuarioIgnoreCase(String usuario);
}
