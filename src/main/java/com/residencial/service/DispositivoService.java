package com.residencial.service;

public interface DispositivoService {
    /** Asocia el celular al usuario actual (si ya estaba, lo pasa a este usuario). */
    void registrar(String token, String tipoApp, String applicationId);

    /** El celular deja de recibir avisos (cierre de sesión). */
    void darDeBaja(String token);
}
