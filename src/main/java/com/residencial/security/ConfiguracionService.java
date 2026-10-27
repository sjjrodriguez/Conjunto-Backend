package com.residencial.security;

import com.residencial.model.ConfiguracionConjunto;
import com.residencial.repository.ConfiguracionConjuntoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Lectura de los ajustes del conjunto; lo que no está guardado vale falso. */
@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ConfiguracionConjuntoRepository repo;

    public boolean activo(String clave) {
        return repo.findById(clave).map(c -> "true".equalsIgnoreCase(c.getValor())).orElse(false);
    }

    public void fijar(String clave, boolean valor) {
        repo.save(new ConfiguracionConjunto(clave, String.valueOf(valor)));
    }

    public boolean vehiculosAltaSoloAdmin() {
        return activo(ConfiguracionConjunto.VEHICULOS_ALTA_SOLO_ADMIN);
    }
}
