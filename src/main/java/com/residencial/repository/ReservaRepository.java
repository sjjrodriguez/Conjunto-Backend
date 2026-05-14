package com.residencial.repository;

import com.residencial.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    List<Reserva> findByApartamentoId(long idApartamento);

    @Query("""
        SELECT r FROM Reserva r
        WHERE r.zonaComun.id = :idZona
          AND r.fechaReserva = :fecha
          AND r.estado <> 'CANCELADA'
          AND (r.horaInicio < :horaFin AND r.horaFin > :horaInicio)
    """)
    List<Reserva> findConflictos(
            @Param("idZona") long idZona,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin
    );
}
