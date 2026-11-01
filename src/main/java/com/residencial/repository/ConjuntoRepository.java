package com.residencial.repository;

import com.residencial.model.Conjunto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConjuntoRepository extends JpaRepository<Conjunto, Long> {

    List<Conjunto> findByActivoTrueOrderByDepartamentoAscMunicipioAscNombreAsc();

    List<Conjunto> findByActivoTrueAndDepartamentoIgnoreCaseAndMunicipioIgnoreCaseOrderByNombreAsc(
            String departamento, String municipio);
}
