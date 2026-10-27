package com.residencial.repository;

import com.residencial.model.PersonalObra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface PersonalObraRepository extends JpaRepository<PersonalObra, Long> {

    // El DTO muestra torre y apto: se traen la obra y su apartamento en la misma consulta
    // (sin esto eran 1 + 2 consultas por cada obra distinta)
    @Override
    @EntityGraph(attributePaths = {"obra", "obra.apartamento"})
    List<PersonalObra> findAll();

    @EntityGraph(attributePaths = {"obra", "obra.apartamento"})
    List<PersonalObra> findByObraId(Long obraId);

    // (obraId, cuántos trabajadores esperan aprobación)
    @Query("select p.obra.id, count(p) from PersonalObra p where p.aprobacion = 'PENDIENTE' and p.obra.id in :ids group by p.obra.id")
    List<Object[]> pendientesPorObra(@Param("ids") Collection<Long> ids);

    void deleteByObraId(Long obraId);
}
