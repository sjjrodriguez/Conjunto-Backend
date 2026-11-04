package com.residencial.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** P7 bloque 3: lo que maneja la app de plataforma. */
public final class PlataformaDTO {

    private PlataformaDTO() {}

    public record Login(String usuario, String contrasena) {}

    public record DatosConjunto(
            @NotBlank(message = "El nombre del conjunto es obligatorio")
            @Size(max = 120, message = "El nombre puede tener hasta 120 caracteres") String nombre,
            @NotBlank(message = "El departamento es obligatorio") String departamento,
            @NotBlank(message = "El municipio es obligatorio")
            @Size(max = 80, message = "El municipio puede tener hasta 80 caracteres") String municipio,
            @Size(max = 160, message = "La dirección puede tener hasta 160 caracteres") String direccion,
            // P7 bloque 5: celulares con sesión a la vez por cuenta; null deja el que tenía (3 si es nuevo)
            @Min(value = 1, message = "Cada cuenta puede tener de 1 a 10 celulares")
            @Max(value = 10, message = "Cada cuenta puede tener de 1 a 10 celulares") Integer maxDispositivos) {}

    /** El conjunto y su administrador principal; la contraseña la genera el servidor. */
    public record NuevoConjunto(
            @NotBlank(message = "El nombre del conjunto es obligatorio")
            @Size(max = 120, message = "El nombre puede tener hasta 120 caracteres") String nombre,
            @NotBlank(message = "El departamento es obligatorio") String departamento,
            @NotBlank(message = "El municipio es obligatorio")
            @Size(max = 80, message = "El municipio puede tener hasta 80 caracteres") String municipio,
            @Size(max = 160, message = "La dirección puede tener hasta 160 caracteres") String direccion,
            @NotBlank(message = "El usuario del administrador es obligatorio")
            @Pattern(regexp = "[A-Za-z0-9._-]{3,60}", message = "El usuario lleva de 3 a 60 letras, números, punto, guion o guion bajo")
            String usuarioPrincipal,
            @NotBlank(message = "El nombre del administrador es obligatorio")
            @Size(max = 150, message = "El nombre puede tener hasta 150 caracteres") String nombrePrincipal,
            @Email(message = "El correo no es válido")
            @Size(max = 150, message = "El correo puede tener hasta 150 caracteres") String correoPrincipal,
            @Min(value = 1, message = "Cada cuenta puede tener de 1 a 10 celulares")
            @Max(value = 10, message = "Cada cuenta puede tener de 1 a 10 celulares") Integer maxDispositivos) {

        public DatosConjunto datos() {
            return new DatosConjunto(nombre, departamento, municipio, direccion, maxDispositivos);
        }
    }

    public record CambioEstado(
            boolean activo,
            @NotBlank(message = "Escribe el motivo")
            @Size(max = 300, message = "El motivo puede tener hasta 300 caracteres") String motivo) {}

    public record ConjuntoResumen(Long id, String nombre, String departamento, String municipio, String direccion,
                                  boolean activo, LocalDateTime creadoEn, long apartamentos,
                                  long cuentasAdministracion, String principal, int maxDispositivos) {}

    public record CuentaAdministracion(Long id, String usuario, String nombreCompleto, String rol,
                                       boolean principal, boolean activo) {}

    /** La contraseña temporal solo se muestra esta vez: el administrador la cambia al entrar. */
    public record Acceso(Long conjuntoId, String usuario, String contrasenaTemporal) {}

    public record Evento(Long id, String operador, String accion, Long conjuntoId, String detalle, LocalDateTime fecha) {}
}
