package com.residencial.repository;

import com.residencial.model.ZonaBloqueo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ZonaBloqueoRepository extends JpaRepository<ZonaBloqueo, Long> {
    boolean existsByZonaIdAndFecha(Long zonaId, LocalDate fecha);
}
