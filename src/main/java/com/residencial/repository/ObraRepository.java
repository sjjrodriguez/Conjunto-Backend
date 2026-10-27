package com.residencial.repository;

import com.residencial.model.Obra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ObraRepository extends JpaRepository<Obra, Long> {
    List<Obra> findByApartamentoId(Long apartamentoId);
    List<Obra> findByEstadoObra(String estadoObra);

    // Para cuando se necesitan torre y apto (no solo el id) fuera de una transacción
    @EntityGraph(attributePaths = "apartamento")
    Optional<Obra> findConApartamentoById(Long id);

    // Búsqueda de la administración: todas con su apartamento (puede no tener: obra del conjunto)
    @EntityGraph(attributePaths = "apartamento")
    @Query("select o from Obra o")
    List<Obra> todasConApartamento();

    boolean existsByApartamentoId(Long apartamentoId);
}
