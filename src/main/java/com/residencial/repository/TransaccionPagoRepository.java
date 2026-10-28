package com.residencial.repository;

import com.residencial.model.TransaccionPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransaccionPagoRepository extends JpaRepository<TransaccionPago, Long> {
    Optional<TransaccionPago> findByReferencia(String referencia);
}
