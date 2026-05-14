package com.residencial.repository;

import com.residencial.model.ZonaComun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZonaComunRepository extends JpaRepository<ZonaComun, Long> {
    List<ZonaComun> findByDisponibleTrue();
}
