package com.residencial.repository;

import com.residencial.model.Consentimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ConsentimientoRepository extends JpaRepository<Consentimiento, Long> {
    @Query("select c.textoId from Consentimiento c where c.sujeto = :sujeto and c.textoId in :textos")
    List<Long> aceptados(String sujeto, Collection<Long> textos);

    boolean existsBySujetoAndTextoId(String sujeto, Long textoId);
}
