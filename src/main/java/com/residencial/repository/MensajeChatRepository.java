package com.residencial.repository;

import com.residencial.model.MensajeChat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensajeChatRepository extends JpaRepository<MensajeChat, Long> {

    // Los últimos (de más nuevo a más viejo); el servicio los voltea
    List<MensajeChat> findByConversacionIdOrderByIdDesc(Long conversacionId, Pageable pagina);

    // Los nuevos desde el último que tiene la app (consulta cada pocos segundos)
    List<MensajeChat> findByConversacionIdAndIdGreaterThanOrderByIdAsc(Long conversacionId, Long id);
}
