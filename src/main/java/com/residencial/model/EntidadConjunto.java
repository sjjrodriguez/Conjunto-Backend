package com.residencial.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.TenantId;

/**
 * P7: lo que pertenece a un conjunto. Hibernate llena conjunto_id al guardar con el conjunto de la
 * petición y filtra por él todas las consultas (ver ConjuntoContexto), así que ningún servicio
 * tiene que acordarse de hacerlo.
 */
@MappedSuperclass
@Getter
public abstract class EntidadConjunto {

    @TenantId
    @Column(name = "conjunto_id", nullable = false, updatable = false)
    private Long conjuntoId;
}
