package com.residencial.repository;

import com.residencial.model.VisitaPorteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface VisitaPorteriaRepository extends JpaRepository<VisitaPorteria, Long> {

    /** P10 bloque 7: las rechazadas hace poco (chat abierto) de un apartamento. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "apartamento")
    List<VisitaPorteria> findByApartamentoIdAndEstadoAndRespondidaEnAfterOrderByRespondidaEnDesc(Long apartamentoId, String estado, LocalDateTime desde);

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

    /** P8 bloque 5: autorizadas sin salida; las de antes de [desde] solo si ocupan un parqueadero. */
    @Query("select v from VisitaPorteria v join fetch v.apartamento left join fetch v.parqueadero where v.salidaEn is null "
            + "and v.estado = 'AUTORIZADA' and (v.creadaEn >= :desde or v.parqueadero is not null) order by v.creadaEn desc, v.id desc")
    List<VisitaPorteria> dentro(LocalDateTime desde);

    /** P12: lo mismo que {@link #dentro}, solo las de un apartamento (los visitantes activos del residente). */
    @Query("select v from VisitaPorteria v join fetch v.apartamento left join fetch v.parqueadero where v.salidaEn is null "
            + "and v.estado = 'AUTORIZADA' and v.apartamento.id = :apto and (v.creadaEn >= :desde or v.parqueadero is not null) "
            + "order by v.creadaEn desc, v.id desc")
    List<VisitaPorteria> dentroDe(Long apto, LocalDateTime desde);

    /**
     * P8 bloque 5: el historial. [patron] es "%texto%" en minúsculas y sin tildes: se compara con el
     * nombre, el documento, la placa y "torre-apto", también en minúsculas y sin tildes.
     */
    @Query("select v from VisitaPorteria v join fetch v.apartamento a left join fetch v.parqueadero where v.creadaEn >= :desde and ("
            + "function('translate', lower(v.visitanteNombre), 'áéíóúüñ', 'aeiouun') like :patron "
            + "or lower(coalesce(v.visitanteDocumento, '')) like :patron "
            + "or lower(coalesce(v.placa, '')) like :patron "
            + "or lower(concat(a.torre, '-', a.apto)) like :patron "
            + "or function('translate', lower(coalesce(v.motivo, '')), 'áéíóúüñ', 'aeiouun') like :patron) "
            + "order by v.creadaEn desc, v.id desc")
    List<VisitaPorteria> historial(LocalDateTime desde, String patron, org.springframework.data.domain.Pageable pagina);

    long countByCreadaEnGreaterThanEqual(LocalDateTime desde);

    long countByEstadoAndVenceEnAfter(String estado, LocalDateTime ahora);

    /** Las que nadie respondió a tiempo. */
    // Con su propia transacción: la abre la tarea programada, que no está en ninguna (P7)
    @Transactional
    @Modifying
    @Query("update VisitaPorteria v set v.estado = 'EXPIRADA' where v.estado = 'PENDIENTE' and v.venceEn <= :ahora")
    int expirar(LocalDateTime ahora);
}
