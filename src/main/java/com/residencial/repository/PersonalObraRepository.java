package com.residencial.repository;

import com.residencial.model.PersonalObra;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PersonalObraRepository extends JpaRepository<PersonalObra, Long> {
    List<PersonalObra> findByObraId(Long obraId);
    void deleteByObraId(Long obraId);
}
