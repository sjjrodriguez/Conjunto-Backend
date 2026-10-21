package com.residencial.repository;

import com.residencial.model.ZonaComun;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ZonaComunRepository extends JpaRepository<ZonaComun, Long> {

    /**
     * Bloquea la fila de la zona (SELECT ... FOR UPDATE) hasta que termine la
     * transacción: dos reservas simultáneas de la misma zona se procesan en fila
     * y la segunda ya ve la primera al buscar choques.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select z from ZonaComun z where z.id = :id")
    Optional<ZonaComun> findByIdParaReservar(@Param("id") Long id);
}
