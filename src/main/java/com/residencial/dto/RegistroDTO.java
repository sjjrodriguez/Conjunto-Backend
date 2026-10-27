package com.residencial.dto;

import java.util.List;

/** Registro de un titular desde la app de residentes (P5 bloque 1). */
public final class RegistroDTO {

    private RegistroDTO() {}

    /** Paso 1: torre y apto. */
    public record Verificar(String torre, String apto) {}

    /** Con qué rol quedaría: PROPIETARIO si el apartamento aún no tiene uno, si no ARRENDATARIO. */
    public record Verificado(String rol) {}

    public record Persona(String nombreCompleto, String identificacion, String telefono) {}

    /** Todo el registro: datos del titular, personas del hogar y visitantes frecuentes (los dos opcionales). */
    public record Solicitud(String torre, String apto, String nombres, String apellidos, String cedula,
                            String telefono, String correo, String contrasena,
                            boolean aceptaDatos, boolean aceptaTerminos,
                            List<Persona> personas, List<Persona> visitantes) {}

    /** Quedó pendiente: de la administración y, si es arrendatario, también del propietario. */
    public record Recibido(String rol, String estado, boolean requierePropietario) {}
}
