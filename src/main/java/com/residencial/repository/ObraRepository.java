package com.residencial.repository;

import com.residencial.model.Obra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ObraRepository extends JpaRepository<Obra, Long> {
    List<Obra> findByApartamentoId(Long apartamentoId);
    List<Obra> findByEstadoObra(String estadoObra);

    // Para cuando se necesitan torre y apto (no solo el id) fuera de una transacción
    @EntityGraph(attributePaths = "apartamento")
    Optional<Obra> findConApartamentoById(Long id);
}
