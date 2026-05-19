package com.residencial.repository;

import com.residencial.model.Administrador;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {
    Optional<Administrador> findByUsuarioAndContrasena(String usuario, String contrasena);
}
