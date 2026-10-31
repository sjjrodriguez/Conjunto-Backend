package com.residencial.repository;

import com.residencial.model.TrabajadorConjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TrabajadorConjuntoRepository extends JpaRepository<TrabajadorConjunto, Long> {
    Optional<TrabajadorConjunto> findByDocumento(String documento);

    @Query("select t from TrabajadorConjunto t left join fetch t.empresa order by t.activo desc, t.nombreCompleto")
    List<TrabajadorConjunto> todos();

    @Query("select t from TrabajadorConjunto t where t.activo = true")
    List<TrabajadorConjunto> activos();

    boolean existsByEmpresaId(Long empresaId);
}
