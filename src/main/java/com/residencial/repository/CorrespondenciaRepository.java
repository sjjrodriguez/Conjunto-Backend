package com.residencial.repository;

import com.residencial.model.Correspondencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface CorrespondenciaRepository extends JpaRepository<Correspondencia, Long> {

    /** Lo que sigue en la portería, lo más viejo primero (lo que lleva más tiempo esperando). */
    @Query("select c from Correspondencia c join fetch c.apartamento where c.estado = 'PENDIENTE' order by c.recibidaEn asc, c.id asc")
    List<Correspondencia> pendientes();

    /** Lo entregado desde [desde], lo más reciente primero. */
    @Query("select c from Correspondencia c join fetch c.apartamento where c.estado = 'ENTREGADA' and c.ocultaPorteria = false and c.entregadaEn >= :desde "
            + "order by c.entregadaEn desc, c.id desc")
    List<Correspondencia> entregadasDesde(LocalDateTime desde);

    /** La de un apartamento: todo lo pendiente y lo recibido desde [desde]. */
    @Query("select c from Correspondencia c join fetch c.apartamento where c.apartamento.id = :apto "
            + "and c.ocultaResidente = false and (c.estado = 'PENDIENTE' or c.recibidaEn >= :desde) order by c.recibidaEn desc, c.id desc")
    List<Correspondencia> delApartamento(Long apto, LocalDateTime desde);

    long countByEstado(String estado);

    /** P12 bloque 3: lo que espera que el apartamento autorice la entrega (sin vencer). */
    @Query("select c from Correspondencia c join fetch c.apartamento where c.apartamento.id = :apto and c.estado = 'PENDIENTE' "
            + "and c.solicitudEstado = 'PENDIENTE' and c.solicitudVenceEn > :ahora order by c.solicitudCreadaEn desc, c.id desc")
    List<Correspondencia> solicitudesPendientes(Long apto, LocalDateTime ahora);

    /** P12: correspondencia con foto del paquete recibida antes de [limite] (para borrar la foto). */
    @Query("select c from Correspondencia c where c.fotoRuta is not null and c.recibidaEn < :limite order by c.id")
    List<Correspondencia> conFotoAntesDe(LocalDateTime limite, org.springframework.data.domain.Pageable pagina);

    /** P12: con foto de quien la recogió, pedida antes de [limite]. */
    @Query("select c from Correspondencia c where c.solicitudFotoRuta is not null and c.solicitudCreadaEn < :limite order by c.id")
    List<Correspondencia> conFotoRecogeAntesDe(LocalDateTime limite, org.springframework.data.domain.Pageable pagina);
}
