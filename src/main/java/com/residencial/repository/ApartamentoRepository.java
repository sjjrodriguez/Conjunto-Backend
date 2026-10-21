package com.residencial.repository;

import com.residencial.model.Apartamento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApartamentoRepository extends JpaRepository<Apartamento, Long> {
    // torre + apto no tiene restriccion UNIQUE en la tabla, por eso se devuelve una lista
    List<Apartamento> findByTorreAndApto(String torre, String apto);
}
