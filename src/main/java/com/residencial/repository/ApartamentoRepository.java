package com.residencial.repository;

import com.residencial.model.Apartamento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApartamentoRepository extends JpaRepository<Apartamento, Long> {
    Optional<Apartamento> findByTorreAndAptoAndContrasena(String torre, String apto, String contrasena);
}
