package com.residencial.security;

import com.residencial.model.Residente;
import com.residencial.repository.ResidenteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Si un apartamento está arrendado (P5): tiene un arrendatario titular que ya puede entrar.
 * Mientras tanto quien vive es el arrendatario, así que el propietario no reserva zonas, no
 * registra vehículos ni gestiona Mi hogar; sigue con la cartera y las obras.
 */
@Component
@RequiredArgsConstructor
public class EstadoApartamento {

    private final ResidenteRepository residentes;
    private final AccesoService acceso;

    public boolean arrendado(Long apartamentoId) {
        return residentes.findTitulares(apartamentoId, Residente.ARRENDATARIO).stream().anyMatch(Residente::puedeEntrar);
    }

    /** Para lo que hace quien vive en el apartamento: falla si es el propietario de uno arrendado. */
    public void exigirQueViva(Long apartamentoId) {
        String rol = acceso.tokenActual().map(t -> t.getClaimAsString(TokenService.CLAIM_ROL)).orElse(null);
        if (TokenService.ROL_PROPIETARIO.equals(rol) && arrendado(apartamentoId)) {
            throw new AccessDeniedException("El apartamento está arrendado: esto lo gestiona el arrendatario");
        }
    }
}
