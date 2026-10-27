package com.residencial.repository;

import com.residencial.model.ZonaBloqueo;
import com.residencial.model.ZonaHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

/** Horario semanal y fechas bloqueadas de las zonas (P5 bloque 5). */
public interface ZonaDisponibilidadRepository extends JpaRepository<ZonaHorario, Long> {

    @Query("select h from ZonaHorario h where h.zona.id = :zonaId order by h.diaSemana")
    List<ZonaHorario> horarioDe(Long zonaId);

    @Modifying
    @Query("delete from ZonaHorario h where h.zona.id = :zonaId")
    void borrarHorarioDe(Long zonaId);

    @Query("select b from ZonaBloqueo b where b.zona.id = :zonaId order by b.fecha")
    List<ZonaBloqueo> bloqueosDe(Long zonaId);

    @Query("select b from ZonaBloqueo b where b.zona.id = :zonaId and b.fecha between :desde and :hasta")
    List<ZonaBloqueo> bloqueosEntre(Long zonaId, LocalDate desde, LocalDate hasta);
}
