package com.residencial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "apartamento")
@Data
@NoArgsConstructor
public class Apartamento {

    // P6: cómo está ocupado. Lo cambia la administración; lo que ve el arrendatario depende de las banderas
    public static final String OCUPA_PROPIETARIO = "PROPIETARIO";
    public static final String OCUPA_ARRENDATARIO = "ARRENDATARIO";
    public static final String OCUPA_DESOCUPADO = "DESOCUPADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String torre;

    @Column(nullable = false)
    private String apto;

    @Column(nullable = false)
    private String contrasena;

    // P5: teléfonos del citófono (portería). Null = sin configurar
    @Column(length = 30)
    private String citofono1;

    @Column(length = 30)
    private String citofono2;

    @Column(name = "tipo_ocupacion", nullable = false, length = 20)
    private String tipoOcupacion = OCUPA_PROPIETARIO;

    @Column(name = "arrendatario_ve_cartera", nullable = false)
    private boolean arrendatarioVeCartera;

    @Column(name = "arrendatario_puede_pagar", nullable = false)
    private boolean arrendatarioPuedePagar;

    @Column(name = "arrendatario_ve_obras", nullable = false)
    private boolean arrendatarioVeObras;

    // Sube cuando se revocan las sesiones del apartamento (ver SesionVersion)
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    public Apartamento(Long id, String torre, String apto, String contrasena, String citofono1, String citofono2) {
        this.id = id;
        this.torre = torre;
        this.apto = apto;
        this.contrasena = contrasena;
        this.citofono1 = citofono1;
        this.citofono2 = citofono2;
    }

    public Apartamento(Long id, String torre, String apto, String contrasena) {
        this(id, torre, apto, contrasena, null, null);
    }
}
