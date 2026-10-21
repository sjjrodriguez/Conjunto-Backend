package com.residencial.repository;

import com.residencial.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findTop50ByOrderByFechaDescIdDesc();

    long countByLeidaFalse();

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.leida = false")
    int marcarTodasLeidas();
}
