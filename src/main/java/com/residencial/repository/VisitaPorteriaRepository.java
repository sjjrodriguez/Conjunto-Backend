package com.residencial.repository;

import com.residencial.model.VisitaPorteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface VisitaPorteriaRepository extends JpaRepository<VisitaPorteria, Long> {

    /** Las visitas recientes para la portería, con su apartamento en la misma consulta. */
    @Query("select v from VisitaPorteria v join fetch v.apartamento where v.creadaEn >= :desde order by v.creadaEn desc, v.id desc")
    List<VisitaPorteria> recientes(LocalDateTime desde);

    @Query("select v from VisitaPorteria v join fetch v.apartamento where v.apartamento.id = :apto and v.estado = 'PENDIENTE' "
            + "and v.venceEn > :ahora order by v.creadaEn desc")
    List<VisitaPorteria> pendientesDe(Long apto, LocalDateTime ahora);

    /** Las que nadie respondió a tiempo. */
    @Modifying
    @Query("update VisitaPorteria v set v.estado = 'EXPIRADA' where v.estado = 'PENDIENTE' and v.venceEn <= :ahora")
    int expirar(LocalDateTime ahora);
}
