package com.residencial.security;

import com.residencial.model.Apartamento;
import com.residencial.repository.ApartamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * Lo que la administración le deja ver o hacer al arrendatario de un apartamento (P6): cartera,
 * pagos y obras. Solo restringe al ARRENDATARIO; el propietario y la administración pasan siempre.
 * Las banderas están en el apartamento y valen "no" hasta que la administración las activa.
 */
@Component
@RequiredArgsConstructor
public class AccesoArrendatario {

    private final AccesoService acceso;
    private final ApartamentoRepository apartamentos;

    public void exigirVerCartera(Long apartamentoId) {
        exigir(apartamentoId, Apartamento::isArrendatarioVeCartera, "la cartera");
    }

    public void exigirPoderPagar(Long apartamentoId) {
        exigir(apartamentoId, Apartamento::isArrendatarioPuedePagar, "los pagos");
    }

    public void exigirVerObras(Long apartamentoId) {
        exigir(apartamentoId, Apartamento::isArrendatarioVeObras, "las obras");
    }

    private void exigir(Long apartamentoId, Predicate<Apartamento> bandera, String que) {
        String rol = acceso.tokenActual().map(t -> t.getClaimAsString(TokenService.CLAIM_ROL)).orElse(null);
        if (!TokenService.ROL_ARRENDATARIO.equals(rol) || apartamentoId == null) return;
        boolean permitido = apartamentos.findById(apartamentoId).map(bandera::test).orElse(false);
        if (!permitido) {
            throw new AccessDeniedException("La administración no te permite ver " + que + " de este apartamento");
        }
    }
}
