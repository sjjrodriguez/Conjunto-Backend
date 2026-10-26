package com.residencial.service;

import com.residencial.dto.RegistroDTO;
import com.residencial.model.Residente;

/** Registro y acceso de los titulares desde la app de residentes (P5 bloque 1). */
public interface RegistroService {
    RegistroDTO.Verificado verificar(RegistroDTO.Verificar solicitud);
    RegistroDTO.Recibido registrar(RegistroDTO.Solicitud solicitud);

    /**
     * El titular de ese apartamento y rol con esa contraseña; null si no coincide. Si coincide
     * pero aún no lo aprueban, responde 403 con el motivo (ya demostró que es él).
     */
    Residente login(String torre, String apto, String rol, String contrasena);
}
