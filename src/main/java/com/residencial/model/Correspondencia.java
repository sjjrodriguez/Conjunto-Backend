package com.residencial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * P9 bloque 3: un paquete, carta, documento o recibo público que llegó a la portería para un
 * apartamento. Al recogerlo queda ENTREGADA con el nombre de quien lo recogió, la hora y el guardia.
 */
@Entity
@Table(name = "correspondencia", indexes = {
        @Index(name = "idx_correspondencia_conjunto_estado", columnList = "conjunto_id, estado, recibida_en"),
        @Index(name = "idx_correspondencia_apartamento", columnList = "apartamento_id, recibida_en")})
@Getter
@Setter
@NoArgsConstructor
public class Correspondencia extends EntidadConjunto {

    public static final String PAQUETE = "PAQUETE";
    public static final String RECIBO = "RECIBO";
    public static final Set<String> TIPOS = Set.of(PAQUETE, "CARTA", "DOCUMENTO", RECIBO);

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENTREGADA = "ENTREGADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apartamento_id", nullable = false, foreignKey = @ForeignKey(name = "fk_correspondencia_apartamento"))
    private Apartamento apartamento;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(length = 150)
    private String remitente;

    @Column(length = 300)
    private String nota;

    /** Los recibos que se registraron juntos para todos los apartamentos. */
    @Column(length = 40)
    private String lote;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(name = "recibida_en", nullable = false)
    private LocalDateTime recibidaEn;

    @Column(name = "guardia_nombre", length = 150)
    private String guardiaNombre;

    @Column(name = "registrada_por", length = 60)
    private String registradaPor;

    @Column(name = "entregada_en")
    private LocalDateTime entregadaEn;

    @Column(name = "entregada_a", length = 150)
    private String entregadaA;

    @Column(name = "entregada_por", length = 150)
    private String entregadaPor;

    // P10 bloque 7: foto del paquete que tomó la portería (ruta en el bucket privado)
    @Column(name = "foto_ruta", length = 200)
    private String fotoRuta;

    /** P11: el apartamento la quitó de su lista (solo lo entregado). */
    @Column(name = "oculta_residente", nullable = false)
    private boolean ocultaResidente;

    /** P11: la portería la quitó de su lista de entregados. */
    @Column(name = "oculta_porteria", nullable = false)
    private boolean ocultaPorteria;
}
