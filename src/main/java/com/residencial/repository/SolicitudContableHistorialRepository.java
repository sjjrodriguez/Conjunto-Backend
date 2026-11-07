package com.residencial.repository;

import com.residencial.model.SolicitudContableHistorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudContableHistorialRepository extends JpaRepository<SolicitudContableHistorial, Long> {
    List<SolicitudContableHistorial> findBySolicitudIdOrderByIdAsc(Long solicitudId);
}
