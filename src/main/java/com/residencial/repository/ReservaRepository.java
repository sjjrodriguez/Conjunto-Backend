package com.residencial.repository;

import com.residencial.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    List<Reserva> findByApartamentoId(Long apartamentoId);

    boolean existsByZonaComunIdAndFechaAndHoraAndEstadoIn(
            Long zonaComunId, LocalDate fecha, LocalTime hora, List<String> estados
    );
}