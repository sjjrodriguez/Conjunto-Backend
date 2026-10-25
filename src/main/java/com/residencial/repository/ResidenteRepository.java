package com.residencial.repository;

import com.residencial.model.Residente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResidenteRepository extends JpaRepository<Residente, Long> {
    @EntityGraph(attributePaths = "apartamento")
    List<Residente> findByApartamentoIdOrderByIdAsc(Long apartamentoId);

    // Login por documento: puede estar en más de un apartamento
    @EntityGraph(attributePaths = "apartamento")
    List<Residente> findByIdentificacion(String identificacion);

    @EntityGraph(attributePaths = "apartamento")
    List<Residente> findByEstadoOrderByIdAsc(String estado);

    boolean existsByApartamentoIdAndIdentificacion(Long apartamentoId, String identificacion);
}
