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

    // Los adjuntos de una página en una sola consulta (no uno por publicación)
    @Query("select a from PublicacionAdjunto a where a.publicacion.id in :ids order by a.orden")
    List<PublicacionAdjunto> adjuntosDe(Collection<Long> ids);
}
