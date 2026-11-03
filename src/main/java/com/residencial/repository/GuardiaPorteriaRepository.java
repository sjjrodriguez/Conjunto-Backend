package com.residencial.repository;

import com.residencial.model.GuardiaPorteria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuardiaPorteriaRepository extends JpaRepository<GuardiaPorteria, Long> {
    List<GuardiaPorteria> findAllByOrderByActivoDescNombreAsc();

    List<GuardiaPorteria> findByActivoTrueOrderByNombreAsc();

    boolean existsByDocumentoAndIdNot(String documento, Long id);

    boolean existsByDocumento(String documento);
}
