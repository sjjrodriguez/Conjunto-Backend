package com.residencial.repository;

import com.residencial.model.Cuota;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface CuotaRepository extends JpaRepository<Cuota, Long> {

    List<Cuota> findByApartamentoIdOrderByPeriodoDesc(Long apartamentoId);

    // Lista del contador: por mes, por estado o ambos (el orden por torre y apto lo pone el servicio)
    @EntityGraph(attributePaths = "apartamento")
    List<Cuota> findByPeriodo(String periodo);

    @EntityGraph(attributePaths = "apartamento")
    List<Cuota> findByEstado(String estado);

    @EntityGraph(attributePaths = "apartamento")
    List<Cuota> findByPeriodoAndEstado(String periodo, String estado);

    @Query("select c.apartamento.id from Cuota c where c.periodo = :periodo")
    Set<Long> apartamentosConPeriodo(String periodo);
}
