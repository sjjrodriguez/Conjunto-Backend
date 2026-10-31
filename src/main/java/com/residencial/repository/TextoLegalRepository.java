package com.residencial.repository;

import com.residencial.model.TextoLegal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TextoLegalRepository extends JpaRepository<TextoLegal, Long> {
    /** La última versión de cada tipo. */
    @Query("select t from TextoLegal t where t.version = (select max(x.version) from TextoLegal x where x.tipo = t.tipo) order by t.tipo")
    List<TextoLegal> vigentes();
}
