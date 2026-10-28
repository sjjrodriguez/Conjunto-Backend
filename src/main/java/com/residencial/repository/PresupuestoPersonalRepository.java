package com.residencial.repository;

import com.residencial.model.PresupuestoPersonal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PresupuestoPersonalRepository extends JpaRepository<PresupuestoPersonal, Long> {
    List<PresupuestoPersonal> findByPeriodo(String periodo);
    Optional<PresupuestoPersonal> findByPeriodoAndCargo(String periodo, String cargo);
}
