package com.residencial.repository;

import com.residencial.model.VisitaPorteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface VisitaPorteriaRepository extends JpaRepository<VisitaPorteria, Long> {

    /** Las visitas recientes para la portería, con su apartamento en la misma consulta. */
    @Query("select v from VisitaPorteria v join fetch v.apartamento where v.creadaEn >= :desde order by v.creadaEn desc, v.id desc")
    List<VisitaPorteria> recientes(LocalDateTime desde);

    @Query("select v from VisitaPorteria v join fetch v.apartamento where v.apartamento.id = :apto and v.estado = 'PENDIENTE' "
            + "and v.venceEn > :ahora order by v.creadaEn desc")
    List<VisitaPorteria> pendientesDe(Long apto, LocalDateTime ahora);

    /** P8 bloque 4: las visitas que ocupan un parqueadero de invitados (pendientes o autorizadas, sin salida). */
    @Query("select v from VisitaPorteria v join fetch v.parqueadero join fetch v.apartamento where v.salidaEn is null "
            + "and v.estado in ('PENDIENTE', 'AUTORIZADA')")
    List<VisitaPorteria> conParqueaderoOcupado();

    /** Las que nadie respondió a tiempo. */
    // Con su propia transacción: la abre la tarea programada, que no está en ninguna (P7)
    @Transactional
    @Modifying
    @Query("update VisitaPorteria v set v.estado = 'EXPIRADA' where v.estado = 'PENDIENTE' and v.venceEn <= :ahora")
    int expirar(LocalDateTime ahora);
}
