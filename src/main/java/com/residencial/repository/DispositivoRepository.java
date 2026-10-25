package com.residencial.repository;

import com.residencial.model.Dispositivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {
    Optional<Dispositivo> findByToken(String token);

    List<Dispositivo> findByRol(String rol);

    /** Celulares de los residentes de un apartamento (sus avisos). */
    List<Dispositivo> findByApartamentoId(Long apartamentoId);

    @Transactional
    @Modifying
    @Query("delete from Dispositivo d where d.token in :tokens")
    int borrarTokens(Collection<String> tokens);
}
