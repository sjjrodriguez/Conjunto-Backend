package com.residencial.repository;

import com.residencial.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    List<Reserva> findByApartamentoId(Long apartamentoId);

    // Candidatas a choque: una reserva tarde en la noche puede cruzar al día siguiente
    List<Reserva> findByZonaComunIdAndFechaBetweenAndEstadoIn(
            Long zonaComunId, LocalDate desde, LocalDate hasta, List<String> estados
    );

    boolean existsByApartamentoId(Long apartamentoId);
}
