package com.residencial.repository;

import com.residencial.model.Parqueadero;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParqueaderoRepository extends JpaRepository<Parqueadero, Long> {

    // Con el vehículo y su apartamento: la lista del administrador los muestra
    @EntityGraph(attributePaths = {"vehiculo", "vehiculo.apartamento"})
    @Query("select p from Parqueadero p")
    List<Parqueadero> todos();

    Optional<Parqueadero> findByVehiculoId(Long vehiculoId);

    List<Parqueadero> findByVehiculoIdIn(Collection<Long> vehiculoIds);

    boolean existsByNumero(String numero);

    @Query("select p.numero from Parqueadero p where p.numero like concat(:prefijo, '-%')")
    List<String> numerosConPrefijo(String prefijo);
}
