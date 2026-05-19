package com.residencial.repository;

import com.residencial.model.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {
    List<Vehiculo> findByApartamentoId(Long apartamentoId);
}
