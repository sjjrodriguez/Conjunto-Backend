package com.residencial.repository;

import com.residencial.model.Residente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    boolean existsByApartamentoId(Long apartamentoId);

    // P5: los titulares (con cuenta) de un apartamento con ese rol
    @EntityGraph(attributePaths = "apartamento")
    @Query("select r from Residente r where r.apartamento.id = :apartamentoId and r.titular = true "
            + "and r.tipoHabitante = :tipo order by r.id")
    List<Residente> findTitulares(Long apartamentoId, String tipo);

    @EntityGraph(attributePaths = "apartamento")
    List<Residente> findByEstadoAndTitularTrueOrderByIdAsc(String estado);

    /** Apartamentos arrendados: con arrendatario titular que ya puede entrar (ver Residente.puedeEntrar). */
    @Query("select distinct r.apartamento.id from Residente r where r.titular = true and r.tipoHabitante = 'ARRENDATARIO' "
            + "and r.estado = 'APROBADO' and r.contrasena is not null "
            + "and (r.aprobadoPropietario is null or r.aprobadoPropietario = true)")
    java.util.Set<Long> apartamentosArrendados();
}
