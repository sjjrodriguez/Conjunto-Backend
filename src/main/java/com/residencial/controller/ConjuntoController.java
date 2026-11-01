package com.residencial.controller;

import com.residencial.model.Conjunto;
import com.residencial.repository.ConjuntoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P7: los conjuntos para elegir antes de iniciar sesión (departamento, municipio y conjunto, en
 * menús desplegables). Es público y solo muestra lo que sirve para elegir: nombre y dirección.
 */
@RestController
@RequestMapping("/api/conjuntos")
public class ConjuntoController {

    public record Ubicacion(String departamento, List<String> municipios) {}
    public record ConjuntoDTO(Long id, String nombre, String direccion, String departamento, String municipio) {}

    private final ConjuntoRepository conjuntos;

    public ConjuntoController(ConjuntoRepository conjuntos) {
        this.conjuntos = conjuntos;
    }

    /** Departamentos con sus municipios, solo los que tienen algún conjunto activo. */
    @GetMapping("/ubicaciones")
    public List<Ubicacion> ubicaciones() {
        Map<String, List<String>> porDepartamento = new LinkedHashMap<>();
        for (Conjunto c : conjuntos.findByActivoTrueOrderByDepartamentoAscMunicipioAscNombreAsc()) {
            List<String> municipios = porDepartamento.computeIfAbsent(c.getDepartamento(), d -> new ArrayList<>());
            if (!municipios.contains(c.getMunicipio())) municipios.add(c.getMunicipio());
        }
        return porDepartamento.entrySet().stream().map(e -> new Ubicacion(e.getKey(), e.getValue())).toList();
    }

    /** Los conjuntos activos de un municipio; sin filtros, todos. */
    @GetMapping
    public List<ConjuntoDTO> listar(@RequestParam(required = false) String departamento,
                                    @RequestParam(required = false) String municipio) {
        List<Conjunto> lista = departamento == null || municipio == null
                ? conjuntos.findByActivoTrueOrderByDepartamentoAscMunicipioAscNombreAsc()
                : conjuntos.findByActivoTrueAndDepartamentoIgnoreCaseAndMunicipioIgnoreCaseOrderByNombreAsc(
                        departamento.trim(), municipio.trim());
        return lista.stream()
                .map(c -> new ConjuntoDTO(c.getId(), c.getNombre(), c.getDireccion(), c.getDepartamento(), c.getMunicipio()))
                .toList();
    }
}
