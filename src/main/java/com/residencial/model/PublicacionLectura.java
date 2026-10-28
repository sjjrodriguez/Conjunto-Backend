package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Marca de que [sujeto] (el del token) ya leyó la publicación (P6 bloque 3). */
@Entity
@Table(name = "publicacion_lectura")
@IdClass(PublicacionLectura.Clave.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PublicacionLectura {

    @Id
    @Column(name = "publicacion_id")
    private Long publicacionId;

    @Id
    @Column(length = 60)
    private String sujeto;

    @Column(name = "leido_en", nullable = false)
    private LocalDateTime leidoEn;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long publicacionId;
        private String sujeto;
    }
}
