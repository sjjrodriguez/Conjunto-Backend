package com.residencial.repository;

import com.residencial.model.TurnoTrabajador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface TurnoTrabajadorRepository extends JpaRepository<TurnoTrabajador, Long> {

    @Query("select t from TurnoTrabajador t join fetch t.trabajador where t.fecha between :desde and :hasta order by t.fecha, t.horaInicio")
    List<TurnoTrabajador> entre(LocalDate desde, LocalDate hasta);

    /** Turnos del mismo trabajador y día que se cruzan con [inicio, fin), sin contar el [excluir]. */
    @Query("select t from TurnoTrabajador t where t.trabajador.id = :trabajador and t.fecha = :fecha "
            + "and t.horaInicio < :fin and t.horaFin > :inicio and t.id <> :excluir")
    List<TurnoTrabajador> cruzados(Long trabajador, LocalDate fecha, LocalTime inicio, LocalTime fin, Long excluir);
}
