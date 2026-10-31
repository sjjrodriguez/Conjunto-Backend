package com.residencial.repository;

import com.residencial.model.NominaTrabajador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NominaTrabajadorRepository extends JpaRepository<NominaTrabajador, Long> {

    @Query("select n from NominaTrabajador n join fetch n.trabajador where n.periodo = :periodo order by n.trabajador.nombreCompleto")
    List<NominaTrabajador> delPeriodo(String periodo);

    boolean existsByTrabajadorIdAndPeriodo(Long trabajadorId, String periodo);
}
