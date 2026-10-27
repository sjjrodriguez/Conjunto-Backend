package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "personal_obra",
        indexes = @Index(name = "idx_personal_obra_obra", columnList = "obra_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonalObra {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombreTrabajador;

    @Column(nullable = false)
    private String cedula;

    @Column(nullable = false)
    private Boolean arlEstado; // true = ARL vigente, false = sin ARL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obra_id", nullable = false)
    private Obra obra;

    // P5: la administración lo aprueba tras revisar el certificado ARL
    @Column(nullable = false, length = 20)
    private String aprobacion = APROBADO;

    @Column(columnDefinition = "TEXT")
    private String motivoRechazo;

    // El certificado en el almacenamiento (se ve con un enlace firmado)
    @Column(length = 500)
    private String arlRuta;

    @Column(length = 100)
    private String arlTipo;

    private LocalDateTime arlSubidoEn;

    public PersonalObra(Long id, String nombreTrabajador, String cedula, Boolean arlEstado, Obra obra) {
        this(id, nombreTrabajador, cedula, arlEstado, obra, APROBADO, null, null, null, null);
    }
}
