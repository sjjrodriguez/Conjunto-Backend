package com.residencial.repository;

import com.residencial.model.SolicitudContable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface SolicitudContableRepository extends JpaRepository<SolicitudContable, Long> {

    @EntityGraph(attributePaths = {"cuota", "cuota.apartamento"})
    List<SolicitudContable> findByEstadoOrderByCreadaEnDesc(String estado);

    @EntityGraph(attributePaths = {"cuota", "cuota.apartamento"})
    List<SolicitudContable> findAllByOrderByCreadaEnDesc();

    long countByEstado(String estado);

    // Cuotas con un pago en efectivo esperando al administrador
    @Query("select s.cuota.id from SolicitudContable s where s.estado = 'PENDIENTE' and s.tipo = 'PAGO_MANUAL'")
    Set<Long> cuotasConPagoPorAprobar();

    boolean existsByTipoAndEstadoAndPeriodo(String tipo, String estado, String periodo);
}
