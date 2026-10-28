package com.residencial.repository;

import com.residencial.model.CredencialDispositivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CredencialDispositivoRepository extends JpaRepository<CredencialDispositivo, Long> {
    Optional<CredencialDispositivo> findBySujetoAndDispositivoIdAndTipoApp(String sujeto, String dispositivoId, String tipoApp);
    Optional<CredencialDispositivo> findByHashCredencialAndDispositivoIdAndTipoApp(String hash, String dispositivoId, String tipoApp);

    @Modifying
    @Query("delete from CredencialDispositivo c where c.sujeto = :sujeto")
    void borrarDe(String sujeto);
}
