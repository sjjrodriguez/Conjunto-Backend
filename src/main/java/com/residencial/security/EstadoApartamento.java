package com.residencial.security;

import com.residencial.model.Residente;
import com.residencial.repository.ResidenteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;

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

    /**
     * P10: avisos del día a día de quien vive en el apartamento. Si está arrendado le llegan al
     * arrendatario y no al propietario, que conserva los suyos (cartera, obras, publicaciones y
     * solicitudes de su arrendatario).
     */
    public static final Set<String> AVISOS_DEL_HOGAR = Set.of("VISITA_PORTERIA", "VISITA_FRECUENTE", "CORRESPONDENCIA", "RECIBO",
            "CORRESPONDENCIA_ENTREGADA", "RESERVA_APROBADA", "RESERVA_RECHAZADA", "VEHICULO_APROBADO", "VEHICULO_RECHAZADO");

    /** P10: si a la sesión actual (propietario de un apartamento arrendado) no le tocan los avisos del hogar. */
    public boolean sinAvisosDelHogar(Long apartamentoId) {
        String rol = acceso.tokenActual().map(t -> t.getClaimAsString(TokenService.CLAIM_ROL)).orElse(null);
        return apartamentoId != null && TokenService.ROL_PROPIETARIO.equals(rol) && arrendado(apartamentoId);
    }

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
