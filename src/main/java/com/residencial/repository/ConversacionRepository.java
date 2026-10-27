package com.residencial.repository;

import com.residencial.model.Conversacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {

    @EntityGraph(attributePaths = "apartamento")
    Optional<Conversacion> findByApartamentoIdAndParticipante(Long apartamentoId, String participante);

    @EntityGraph(attributePaths = "apartamento")
    @Query("select c from Conversacion c where c.id = :id")
    Optional<Conversacion> conApartamento(@Param("id") Long id);

    // Bandeja de la administración: las que tienen mensajes, la más reciente primero
    @EntityGraph(attributePaths = "apartamento")
    List<Conversacion> findByUltimoMensajeEnIsNotNullOrderByUltimoMensajeEnDesc();
}
