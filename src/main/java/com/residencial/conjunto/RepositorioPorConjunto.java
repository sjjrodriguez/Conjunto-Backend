package com.residencial.conjunto;

import com.residencial.model.EntidadConjunto;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.util.Optional;

/**
 * P7: base de todos los repositorios. Hibernate 6.4 filtra por conjunto las consultas, pero no la
 * carga por id (em.find): sin esto, quien conozca el id de algo de otro conjunto podría abrirlo.
 * deleteById pasa por aquí también. getReferenceById no consulta la base y solo se usa con ids
 * que salen del token.
 */
public class RepositorioPorConjunto<T, ID> extends SimpleJpaRepository<T, ID> {

    public RepositorioPorConjunto(JpaEntityInformation<T, ?> informacion, EntityManager em) {
        super(informacion, em);
    }

    @Override
    public Optional<T> findById(ID id) {
        return super.findById(id).filter(RepositorioPorConjunto::delConjuntoActual);
    }

    static boolean delConjuntoActual(Object entidad) {
        if (!(entidad instanceof EntidadConjunto e) || e.getConjuntoId() == null) return true;
        long actual = ConjuntoContexto.actual();
        return actual == ConjuntoContexto.RAIZ || e.getConjuntoId() == actual;
    }
}
