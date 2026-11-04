package com.residencial.repository;

import com.residencial.model.DispositivoCuenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DispositivoCuentaRepository extends JpaRepository<DispositivoCuenta, Long> {
    Optional<DispositivoCuenta> findBySujetoAndDispositivo(String sujeto, String dispositivo);

    Optional<DispositivoCuenta> findBySesion(String sesion);

    List<DispositivoCuenta> findBySujetoOrderBySesionDesdeDesc(String sujeto);

    // Las sesiones abiertas de la cuenta, la más antigua primero (la que se cierra al pasar el límite)
    List<DispositivoCuenta> findBySujetoAndSesionIsNotNullOrderBySesionDesdeAsc(String sujeto);
}
