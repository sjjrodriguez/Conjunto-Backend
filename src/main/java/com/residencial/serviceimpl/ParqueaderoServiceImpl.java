package com.residencial.serviceimpl;

import com.residencial.dto.ParqueaderoDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Catalogo;
import com.residencial.model.Parqueadero;
import com.residencial.model.Vehiculo;
import com.residencial.repository.ParqueaderoRepository;
import com.residencial.repository.VehiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parqueaderos del conjunto (P5 bloque 4): el administrador los crea por categoría (y los de
 * invitados), los asigna al aprobar un vehículo y luego puede reasignarlos o liberarlos.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ParqueaderoServiceImpl {

    public static final String LIBRE = "LIBRE";
    public static final String ASIGNADO = "ASIGNADO";
    private static final int MAXIMO_POR_LOTE = 200;
    private static final Pattern PREFIJO = Pattern.compile("^[A-Za-z0-9]{1,6}$");

    private final ParqueaderoRepository repo;
    private final VehiculoRepository vehiculos;

    @Transactional(readOnly = true)
    public List<ParqueaderoDTO> listar(String tipo, String estado) {
        String t = tipo == null || tipo.isBlank() ? null : normalizarTipo(tipo);
        String e = estado == null || estado.isBlank() ? null : estado.trim().toUpperCase(Locale.ROOT);
        return repo.todos().stream()
                .filter(p -> t == null || t.equals(p.getTipo()))
                .filter(p -> e == null || e.equals(p.libre() ? LIBRE : ASIGNADO))
                .sorted(Comparator.comparing(Parqueadero::getTipo).thenComparing(Parqueadero::getNumero, ParqueaderoServiceImpl::natural))
                .map(ParqueaderoServiceImpl::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public ParqueaderoDTO.Resumen resumen() {
        List<Parqueadero> todos = repo.findAll();
        Map<String, List<Parqueadero>> porTipo = todos.stream().collect(Collectors.groupingBy(Parqueadero::getTipo, TreeMap::new, Collectors.toList()));
        List<ParqueaderoDTO.PorTipo> lista = porTipo.entrySet().stream().map(en -> {
            long asignados = en.getValue().stream().filter(p -> !p.libre()).count();
            return new ParqueaderoDTO.PorTipo(en.getKey(), en.getValue().size(), asignados, en.getValue().size() - asignados);
        }).toList();
        List<Parqueadero> deVehiculos = todos.stream().filter(p -> !Parqueadero.INVITADOS.equals(p.getTipo())).toList();
        long asignados = deVehiculos.stream().filter(p -> !p.libre()).count();
        return new ParqueaderoDTO.Resumen(deVehiculos.size(), asignados, deVehiculos.size() - asignados,
                todos.size() - deVehiculos.size(), lista);
    }

    /** Crea [cantidad] parqueaderos de un tipo, siguiendo la numeración que ya tenga ese prefijo. */
    public List<ParqueaderoDTO> generar(ParqueaderoDTO.Generar g) {
        if (g == null || g.tipo() == null) throw invalido("Indica el tipo de parqueadero");
        String tipo = normalizarTipo(g.tipo());
        int cantidad = g.cantidad() == null ? 0 : g.cantidad();
        if (cantidad < 1 || cantidad > MAXIMO_POR_LOTE) throw invalido("La cantidad debe estar entre 1 y " + MAXIMO_POR_LOTE);
        String prefijo = g.prefijo() == null || g.prefijo().isBlank() ? prefijoDe(tipo) : g.prefijo().trim().toUpperCase(Locale.ROOT);
        if (!PREFIJO.matcher(prefijo).matches()) throw invalido("El prefijo admite hasta 6 letras o números");
        int siguiente = repo.numerosConPrefijo(prefijo).stream()
                .map(n -> n.substring(prefijo.length() + 1))
                .filter(n -> n.matches("\\d+")).mapToInt(Integer::parseInt).max().orElse(0) + 1;
        List<Parqueadero> nuevos = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            String numero = String.format("%s-%02d", prefijo, siguiente + i);
            if (repo.existsByNumero(numero)) throw invalido("Ya existe el parqueadero " + numero);
            nuevos.add(new Parqueadero(null, numero, tipo, null));
        }
        return repo.saveAll(nuevos).stream().map(ParqueaderoServiceImpl::toDTO).toList();
    }

    /** Asigna el parqueadero a un vehículo aprobado de su misma categoría; null lo libera. */
    public ParqueaderoDTO asignar(Long id, Long vehiculoId) {
        Parqueadero p = buscar(id);
        if (vehiculoId == null) {
            p.setVehiculo(null);
            return toDTO(repo.save(p));
        }
        if (Parqueadero.INVITADOS.equals(p.getTipo())) throw invalido("Los parqueaderos de invitados no se asignan a un apartamento");
        Vehiculo v = vehiculos.findById(vehiculoId).orElseThrow(() -> new RecursoNoEncontradoException("Vehículo no encontrado: " + vehiculoId));
        asignar(p, v);
        return toDTO(repo.save(p));
    }

    /** Al aprobar un vehículo: el parqueadero elegido (si lo hay). Libera el que tuviera antes. */
    public String asignarAlAprobar(Vehiculo v, Long parqueaderoId) {
        if (parqueaderoId == null) return repo.findByVehiculoId(v.getId()).map(Parqueadero::getNumero).orElse(null);
        Parqueadero p = buscar(parqueaderoId);
        asignar(p, v);
        return repo.save(p).getNumero();
    }

    public void liberarDe(Long vehiculoId) {
        repo.findByVehiculoId(vehiculoId).ifPresent(p -> {
            p.setVehiculo(null);
            repo.saveAndFlush(p);
        });
    }

    public void eliminar(Long id) {
        Parqueadero p = buscar(id);
        if (!p.libre()) throw invalido("Primero libera el parqueadero " + p.getNumero());
        repo.delete(p);
    }

    /** Número del parqueadero de cada vehículo (una consulta para toda la lista). */
    @Transactional(readOnly = true)
    public Map<Long, Parqueadero> deVehiculos(Collection<Long> vehiculoIds) {
        if (vehiculoIds.isEmpty()) return Map.of();
        return repo.findByVehiculoIdIn(vehiculoIds).stream().collect(Collectors.toMap(p -> p.getVehiculo().getId(), p -> p));
    }

    private void asignar(Parqueadero p, Vehiculo v) {
        if (!Vehiculo.APROBADO.equals(v.getEstado()) && !Vehiculo.PENDIENTE.equals(v.getEstado())) {
            throw invalido("El vehículo fue rechazado");
        }
        if (!p.getTipo().equals(v.getTipo())) {
            throw invalido("El parqueadero " + p.getNumero() + " es de " + p.getTipo().toLowerCase(Locale.ROOT)
                    + " y el vehículo es " + v.getTipo().toLowerCase(Locale.ROOT));
        }
        if (!p.libre() && !p.getVehiculo().getId().equals(v.getId())) {
            throw invalido("El parqueadero " + p.getNumero() + " ya está asignado; libéralo primero");
        }
        // Un vehículo usa un solo parqueadero: si tenía otro, queda libre
        repo.findByVehiculoId(v.getId()).filter(otro -> !otro.getId().equals(p.getId())).ifPresent(otro -> {
            otro.setVehiculo(null);
            repo.saveAndFlush(otro);
        });
        p.setVehiculo(v);
    }

    private Parqueadero buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Parqueadero no encontrado: " + id));
    }

    static String normalizarTipo(String tipo) {
        return Parqueadero.INVITADOS.equalsIgnoreCase(tipo.trim()) ? Parqueadero.INVITADOS : Catalogo.TIPO_VEHICULO.normalizar(tipo);
    }

    // C (carro), M (moto), B (bicicleta), P (patineta), PE (eléctrica), O (otro), INV (invitados)
    private static String prefijoDe(String tipo) {
        return switch (tipo) {
            case "Carro" -> "C";
            case "Moto" -> "M";
            case "Bicicleta" -> "B";
            case "Patineta" -> "P";
            case "Patineta eléctrica" -> "PE";
            case Parqueadero.INVITADOS -> "INV";
            default -> "O";
        };
    }

    private static ParqueaderoDTO toDTO(Parqueadero p) {
        Vehiculo v = p.getVehiculo();
        Apartamento a = v == null ? null : v.getApartamento();
        String descripcion = v == null ? null : String.join(" · ",
                java.util.stream.Stream.of(v.getTipo(), v.getMarca(), v.getColor()).filter(x -> x != null && !x.isBlank()).toList());
        return new ParqueaderoDTO(p.getId(), p.getNumero(), p.getTipo(), p.libre() ? LIBRE : ASIGNADO,
                v == null ? null : v.getId(), v == null ? null : v.getPlaca(), descripcion,
                a == null ? null : a.getId(), a == null ? null : a.getTorre(), a == null ? null : a.getApto());
    }

    // "C-2" antes que "C-10"
    private static int natural(String a, String b) {
        String na = a.replaceAll("\\D", ""), nb = b.replaceAll("\\D", "");
        String pa = a.replaceAll("\\d", ""), pb = b.replaceAll("\\d", "");
        int c = pa.compareToIgnoreCase(pb);
        if (c != 0 || na.isEmpty() || nb.isEmpty()) return c != 0 ? c : a.compareToIgnoreCase(b);
        return Long.compare(Long.parseLong(na), Long.parseLong(nb));
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
