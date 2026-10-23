package com.residencial.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Envío real con Firebase Cloud Messaging. Mensajes solo de datos y prioridad alta: la app
 * arma la notificación ella misma (mismo aspecto con la app abierta o cerrada) y sabe a qué
 * pantalla llevar al tocarla.
 */
@Slf4j
public class FirebaseEnviadorPush implements EnviadorPush {

    // Firebase acepta hasta 500 tokens por envío
    private static final int MAXIMO_POR_ENVIO = 500;

    private final FirebaseMessaging mensajeria;

    /** @param credenciales JSON de la cuenta de servicio de Firebase, tal cual o en Base64 */
    public FirebaseEnviadorPush(String credenciales) throws IOException {
        String json = credenciales.trim().startsWith("{")
                ? credenciales
                : new String(Base64.getDecoder().decode(credenciales.trim()), StandardCharsets.UTF_8);
        FirebaseOptions opciones = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))))
                .build();
        this.mensajeria = FirebaseMessaging.getInstance(FirebaseApp.initializeApp(opciones, "miconjunto"));
    }

    @Override
    public Set<String> enviar(List<String> tokens, Map<String, String> datos) {
        Set<String> invalidos = new HashSet<>();
        for (int desde = 0; desde < tokens.size(); desde += MAXIMO_POR_ENVIO) {
            List<String> lote = tokens.subList(desde, Math.min(desde + MAXIMO_POR_ENVIO, tokens.size()));
            MulticastMessage mensaje = MulticastMessage.builder()
                    .addAllTokens(lote)
                    .putAllData(datos)
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                    .build();
            try {
                BatchResponse respuesta = mensajeria.sendEachForMulticast(mensaje);
                List<SendResponse> resultados = respuesta.getResponses();
                for (int i = 0; i < resultados.size(); i++) {
                    if (!resultados.get(i).isSuccessful() && esTokenInvalido(resultados.get(i).getException())) {
                        invalidos.add(lote.get(i));
                    }
                }
            } catch (FirebaseMessagingException e) {
                // Un aviso que no sale no debe romper nada: la notificación ya está guardada
                log.warn("No se pudo enviar el aviso push: {}", e.getMessage());
            }
        }
        return invalidos;
    }

    private static boolean esTokenInvalido(FirebaseMessagingException e) {
        return e != null && (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT);
    }
}
