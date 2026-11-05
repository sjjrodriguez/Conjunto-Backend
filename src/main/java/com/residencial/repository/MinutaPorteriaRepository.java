package com.residencial.repository;

import com.residencial.model.MinutaPorteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MinutaPorteriaRepository extends JpaRepository<MinutaPorteria, Long> {

    @Query("select m from MinutaPorteria m where m.creadaEn >= :desde order by m.creadaEn desc, m.id desc")
    List<MinutaPorteria> desde(LocalDateTime desde);

    long countByCreadaEnGreaterThanEqual(LocalDateTime desde);
}
