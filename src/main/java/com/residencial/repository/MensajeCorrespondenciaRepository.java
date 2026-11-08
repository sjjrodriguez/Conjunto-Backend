package com.residencial.repository;

import com.residencial.model.MensajeCorrespondencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensajeCorrespondenciaRepository extends JpaRepository<MensajeCorrespondencia, Long> {

    List<MensajeCorrespondencia> findByCorrespondenciaIdOrderByCreadoEnAscIdAsc(Long correspondenciaId);

    long countByCorrespondenciaIdAndDeResidenteTrue(Long correspondenciaId);
}
