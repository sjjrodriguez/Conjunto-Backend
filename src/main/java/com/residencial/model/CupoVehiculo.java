package com.residencial.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cuántos vehículos de un tipo puede tener cada apartamento. Los edita el administrador. */
@Entity
@Table(name = "cupo_vehiculo")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CupoVehiculo {

    /** Cupos iniciales (los mismos de la migración V3), en el orden en que se muestran. */
    public static final Map<String, Integer> POR_DEFECTO = new LinkedHashMap<>();
    static {
        POR_DEFECTO.put("Carro", 1);
        POR_DEFECTO.put("Moto", 1);
        POR_DEFECTO.put("Bicicleta", 2);
        POR_DEFECTO.put("Patineta", 2);
        POR_DEFECTO.put("Patineta eléctrica", 1);
        POR_DEFECTO.put("Otro", 1);
    }

    @Id
    @Column(length = 40)
    private String tipo;

    @Column(nullable = false)
    private Integer maximo;
}
