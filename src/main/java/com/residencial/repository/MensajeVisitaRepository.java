package com.residencial.repository;

import com.residencial.model.MensajeVisita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface MensajeVisitaRepository extends JpaRepository<MensajeVisita, Long> {

    List<MensajeVisita> findByVisitaIdOrderByCreadoEnAscIdAsc(Long visitaId);

    /** Cuántos mensajes escribió el residente en cada visita: [visitaId, cantidad]. */
    @Query("select m.visita.id, count(m) from MensajeVisita m where m.deResidente = true and m.visita.id in :visitas group by m.visita.id")
    List<Object[]> delResidentePorVisita(Collection<Long> visitas);
}
