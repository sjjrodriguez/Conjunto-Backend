package com.residencial.repository;

import com.residencial.model.Publicacion;
import com.residencial.model.PublicacionAdjunto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {

    Page<Publicacion> findAllByOrderByCreadaEnDescIdDesc(Pageable pagina);

    /**
     * El feed de quien lo mira: [todo] = la administración ve todo; un residente, lo dirigido a su rol
     * (o a todos) y a su torre (o a todas). Fijadas primero.
     */
    @Query("select p from Publicacion p where :todo = true "
            + "or ((p.audiencia = 'TODOS' or p.audiencia = :audiencia) and (p.torre is null or p.torre = :torre)) "
            + "order by p.fijado desc, p.creadaEn desc, p.id desc")
    Page<Publicacion> feed(boolean todo, String audiencia, String torre, Pageable pagina);

    // Los adjuntos de una página en una sola consulta (no uno por publicación)
    @Query("select a from PublicacionAdjunto a where a.publicacion.id in :ids order by a.orden")
    List<PublicacionAdjunto> adjuntosDe(Collection<Long> ids);
}
