package com.residencial.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "conjunto")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 250)
    private String direccion;

    @Column(length = 100)
    private String ciudad;

    @JsonManagedReference
    @OneToMany(mappedBy = "conjunto", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Torre> torres;
}