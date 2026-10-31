package com.residencial.repository;

import com.residencial.model.SolicitudDatos;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudDatosRepository extends JpaRepository<SolicitudDatos, Long> {
    List<SolicitudDatos> findBySujetoOrderByCreadaEnDesc(String sujeto);
    List<SolicitudDatos> findAllByOrderByCreadaEnDesc();
}
