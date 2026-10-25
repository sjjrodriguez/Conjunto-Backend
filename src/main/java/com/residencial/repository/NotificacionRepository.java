package com.residencial.repository;

import com.residencial.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/**
 * Cada consulta va en dos versiones: las del administrador (destinatario null) y las de un
 * apartamento. Separadas y no con un parámetro opcional: en Postgres un "? is null" sin tipo
 * puede fallar.
 */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    // --- Administrador ---
    List<Notificacion> findTop50ByDestinatarioApartamentoIdIsNullOrderByFechaDescIdDesc();

    long countByLeidaFalseAndDestinatarioApartamentoIdIsNull();

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.leida = false and n.destinatarioApartamentoId is null")
    int marcarTodasLeidasAdmin();

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.id = :id and n.destinatarioApartamentoId is null")
    int marcarLeidaAdmin(Long id);

    @Modifying
    @Query("delete from Notificacion n where n.destinatarioApartamentoId is null")
    int eliminarTodasAdmin();

    @Modifying
    @Query("delete from Notificacion n where n.id = :id and n.destinatarioApartamentoId is null")
    int eliminarAdmin(Long id);

    // --- Residentes de un apartamento ---
    List<Notificacion> findTop50ByDestinatarioApartamentoIdOrderByFechaDescIdDesc(Long apartamentoId);

    long countByLeidaFalseAndDestinatarioApartamentoId(Long apartamentoId);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.leida = false and n.destinatarioApartamentoId = :apartamentoId")
    int marcarTodasLeidasApartamento(Long apartamentoId);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.id = :id and n.destinatarioApartamentoId = :apartamentoId")
    int marcarLeidaApartamento(Long id, Long apartamentoId);

    @Modifying
    @Query("delete from Notificacion n where n.destinatarioApartamentoId = :apartamentoId")
    int eliminarTodasApartamento(Long apartamentoId);

    @Modifying
    @Query("delete from Notificacion n where n.id = :id and n.destinatarioApartamentoId = :apartamentoId")
    int eliminarApartamento(Long id, Long apartamentoId);
}
