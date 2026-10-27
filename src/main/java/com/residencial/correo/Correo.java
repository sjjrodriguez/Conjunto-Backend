package com.residencial.correo;

/**
 * Envío de correos (P5): confirmación del registro, aprobación o rechazo. Con BREVO_API_KEY
 * se envían por Brevo; sin ella solo quedan en el log (demo, pruebas). Nunca bloquea la
 * petición ni la hace fallar: si el correo no sale, se registra el error.
 */
public interface Correo {
    void enviar(String para, String nombre, String asunto, String html);
}
