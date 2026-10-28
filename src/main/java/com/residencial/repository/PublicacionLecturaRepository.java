package com.residencial.repository;

import com.residencial.model.PublicacionLectura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface PublicacionLecturaRepository extends JpaRepository<PublicacionLectura, PublicacionLectura.Clave> {

    @Modifying
    @Query("delete from PublicacionLectura l where l.publicacionId = :id")
    void borrarDe(Long id);

    @Query("select l.publicacionId from PublicacionLectura l where l.sujeto = :sujeto and l.publicacionId in :ids")
    List<Long> leidasPor(String sujeto, Collection<Long> ids);
}
