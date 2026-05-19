package com.residencial.service;

import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;

public interface AuthService {
    Administrador loginAdmin(String usuario, String contrasena);
    Apartamento loginApartamento(String torre, String apto, String contrasena);
}
