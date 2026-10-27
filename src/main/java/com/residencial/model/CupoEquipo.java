package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

/** Cuántas cuentas de un rol (ADMIN asistente o CONTADOR) puede crear el administrador principal. */
@Entity
@Table(name = "cupo_equipo")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CupoEquipo {

    @Id
    @Column(length = 20)
    private String rol;

    @Column(nullable = false)
    private Integer maximo;
}
