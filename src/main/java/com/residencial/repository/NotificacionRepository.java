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
    List<Notificacion> findTop50ByDestinatarioApartamentoIdIsNullAndTipoNotInOrderByFechaDescIdDesc(java.util.Collection<String> fuera);

    long countByLeidaFalseAndDestinatarioApartamentoIdIsNullAndTipoNotIn(java.util.Collection<String> fuera);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.leida = false and n.destinatarioApartamentoId is null and n.tipo not in :fuera")
    int marcarTodasLeidasAdmin(java.util.Collection<String> fuera);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.id = :id and n.destinatarioApartamentoId is null and n.tipo not in :fuera")
    int marcarLeidaAdmin(Long id, java.util.Collection<String> fuera);

    @Modifying
    @Query("delete from Notificacion n where n.destinatarioApartamentoId is null and n.tipo not in :fuera")
    int eliminarTodasAdmin(java.util.Collection<String> fuera);

    @Modifying
    @Query("delete from Notificacion n where n.id = :id and n.destinatarioApartamentoId is null and n.tipo not in :fuera")
    int eliminarAdmin(Long id, java.util.Collection<String> fuera);

    // --- P11: el contador solo ve, de las de la administración, los tipos que le tocan ---
    List<Notificacion> findTop50ByDestinatarioApartamentoIdIsNullAndTipoInOrderByFechaDescIdDesc(java.util.Collection<String> tipos);
    long countByLeidaFalseAndDestinatarioApartamentoIdIsNullAndTipoIn(java.util.Collection<String> tipos);
    @Modifying
    @Query("update Notificacion n set n.leida = true where n.leida = false and n.destinatarioApartamentoId is null and n.tipo in :tipos")
    int marcarTodasLeidasContador(java.util.Collection<String> tipos);
    @Modifying
    @Query("update Notificacion n set n.leida = true where n.id = :id and n.destinatarioApartamentoId is null and n.tipo in :tipos")
    int marcarLeidaContador(Long id, java.util.Collection<String> tipos);
    @Modifying
    @Query("delete from Notificacion n where n.destinatarioApartamentoId is null and n.tipo in :tipos")
    int eliminarTodasContador(java.util.Collection<String> tipos);
    @Modifying
    @Query("delete from Notificacion n where n.id = :id and n.destinatarioApartamentoId is null and n.tipo in :tipos")
    int eliminarContador(Long id, java.util.Collection<String> tipos);
    // --- Residentes de un apartamento ---
    List<Notificacion> findTop50ByDestinatarioApartamentoIdOrderByFechaDescIdDesc(Long apartamentoId);

    long countByLeidaFalseAndDestinatarioApartamentoId(Long apartamentoId);

    // P10: el propietario de un apartamento arrendado no ve los avisos del hogar
    List<Notificacion> findTop50ByDestinatarioApartamentoIdAndTipoNotInOrderByFechaDescIdDesc(Long apartamentoId, java.util.Collection<String> tipos);

    long countByLeidaFalseAndDestinatarioApartamentoIdAndTipoNotIn(Long apartamentoId, java.util.Collection<String> tipos);

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
