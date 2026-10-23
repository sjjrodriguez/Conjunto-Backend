package com.residencial.push;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Envía un aviso a los celulares (Firebase Cloud Messaging). Si Firebase no está configurado
 * (FIREBASE_CREDENTIALS vacío) se usa uno que no hace nada: la app funciona igual, sin avisos.
 */
public interface EnviadorPush {

    /**
     * @param datos lo que recibe la app para armar la notificación y abrir su pantalla
     * @return los tokens que Firebase ya no acepta (app desinstalada, sesión vieja), para borrarlos
     */
    Set<String> enviar(List<String> tokens, Map<String, String> datos);
}
