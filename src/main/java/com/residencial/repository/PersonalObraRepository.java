package com.residencial.repository;

import com.residencial.model.PersonalObra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PersonalObraRepository extends JpaRepository<PersonalObra, Long> {

    // El DTO muestra torre y apto: se traen la obra y su apartamento en la misma consulta
    // (sin esto eran 1 + 2 consultas por cada obra distinta)
    @Override
    @EntityGraph(attributePaths = {"obra", "obra.apartamento"})
    List<PersonalObra> findAll();

    @EntityGraph(attributePaths = {"obra", "obra.apartamento"})
    List<PersonalObra> findByObraId(Long obraId);

    void deleteByObraId(Long obraId);
}
