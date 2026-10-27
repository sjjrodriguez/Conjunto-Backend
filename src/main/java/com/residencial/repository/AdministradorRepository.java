package com.residencial.repository;

import com.residencial.model.Administrador;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {
    Optional<Administrador> findByUsuario(String usuario);

    boolean existsByPrincipalTrue();

    long countByRolAndPrincipalFalse(String rol);

    /**
     * El que maneja el equipo (P5). Si ninguna cuenta está marcada (base nueva o creada a mano),
     * cualquier administrador lo es hasta que cree la primera cuenta y quede marcado.
     */
    default boolean esPrincipal(Administrador a) {
        return a.isPrincipal() || (Administrador.ADMIN.equals(a.getRol()) && !existsByPrincipalTrue());
    }
}
