package com.residencial.service;

import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;

public interface AuthService {
    Administrador loginAdmin(String usuario, String contrasena);
    Apartamento loginApartamento(String torre, String apto, String contrasena);

    /**
     * Cambia la contraseña del dueño de la sesión ("admin:1" o "apartamento:7", el sujeto del token).
     * @return false si la contraseña actual no coincide
     */
    boolean cambiarContrasena(String sujeto, String actual, String nueva);
}
