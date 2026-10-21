package com.residencial.model;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.text.Normalizer;
import java.util.List;

/**
 * Valores permitidos para los campos de estado y tipo, que en la base siguen
 * siendo texto. {@link #normalizar} acepta variantes de mayúsculas, tildes y
 * espacios ("aprobada", "Al dia", "en proceso") y devuelve el valor canónico;
 * cualquier otro valor responde 400.
 */
public enum Catalogo {

    ESTADO_RESERVA("El estado de la reserva", List.of("PENDIENTE", "APROBADA", "RECHAZADA")),
    ESTADO_OBRA("El estado de la obra", List.of("EN_PROCESO", "FINALIZADA")),
    TIPO_HABITANTE("El tipo de habitante", List.of("PROPIETARIO", "ARRENDATARIO")),
    TIPO_VEHICULO("El tipo de vehículo", List.of("Carro", "Moto")),
    ESTADO_CUENTA("El estado de cuenta", List.of("Al Día", "En Mora"));

    private final String campo;
    private final List<String> permitidos;

    Catalogo(String campo, List<String> permitidos) {
        this.campo = campo;
        this.permitidos = permitidos;
    }

    public String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, campo + " es obligatorio");
        }
        String clave = clave(valor);
        return permitidos.stream()
                .filter(p -> clave(p).equals(clave))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        campo + " no es válido. Valores permitidos: " + String.join(", ", permitidos)));
    }

    // "Al Día" -> "AL_DIA", "en proceso" -> "EN_PROCESO"
    private static String clave(String valor) {
        String sinTildes = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase().replaceAll("[\\s-]+", "_");
    }
}
