package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/** Cupo de vehículos de un tipo para un apartamento en particular (P5); si no hay, rige el general. */
@Entity
@Table(name = "cupo_apartamento")
@IdClass(CupoApartamento.Clave.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CupoApartamento {

    @Id
    @Column(name = "apartamento_id")
    private Long apartamentoId;

    @Id
    @Column(length = 40)
    private String tipo;

    @Column(nullable = false)
    private Integer maximo;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Clave implements Serializable {
        private Long apartamentoId;
        private String tipo;
    }
}
