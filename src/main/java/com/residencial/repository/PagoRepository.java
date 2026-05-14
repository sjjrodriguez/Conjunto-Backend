package com.residencial.repository;

import com.residencial.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByApartamentoId(long idApartamento);
    List<Pago> findByApartamentoIdAndEstado(long idApartamento, Pago.EstadoPago estado);
    boolean existsByApartamentoIdAndMesAndAnio(long idApartamento, int mes, int anio);
}
