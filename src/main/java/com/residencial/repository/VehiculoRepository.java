package com.residencial.repository;

import com.residencial.model.Vehiculo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {
    // Con su apartamento: el DTO lleva torre y apto, y fuera de la transacción no se podría cargar
    @EntityGraph(attributePaths = "apartamento")
    List<Vehiculo> findByApartamentoId(Long apartamentoId);

    /** Para la lista de aprobación: con su apartamento en la misma consulta (torre y apto). */
    @EntityGraph(attributePaths = "apartamento")
    List<Vehiculo> findByEstadoOrderByIdAsc(String estado);

    /** Cuántos de un tipo ocupan cupo en el apartamento (pendientes y aprobados). */
    long countByApartamentoIdAndTipoAndEstadoIn(Long apartamentoId, String tipo, Collection<String> estados);

    boolean existsByApartamentoId(Long apartamentoId);
}
