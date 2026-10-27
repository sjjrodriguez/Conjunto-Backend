package com.residencial.serviceimpl;

import com.residencial.dto.ChatDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Conversacion;
import com.residencial.model.MensajeChat;
import com.residencial.model.Residente;
import com.residencial.push.AvisosPush;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.ConversacionRepository;
import com.residencial.repository.MensajeChatRepository;
import com.residencial.repository.ResidenteRepository;
import com.residencial.security.AccesoService;
import com.residencial.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Chat entre la administración (principal y asistentes; no el contador) y el propietario o el
 * arrendatario de cada apartamento. La app consulta los nuevos cada pocos segundos mientras el
 * chat está abierto; al otro lado le llega un aviso push sin el texto del mensaje.
 */
@Service
@Transactional
public class ChatService {

    public static final String PROPIETARIO = "PROPIETARIO";
    public static final String ARRENDATARIO = "ARRENDATARIO";
    private static final int MAXIMO_TEXTO = 2000;
    private static final int ULTIMOS = 200;

    private final ConversacionRepository conversaciones;
    private final MensajeChatRepository mensajes;
    private final ApartamentoRepository aptoRepo;
    private final ResidenteRepository residenteRepo;
    private final AccesoService acceso;
    private final ApplicationEventPublisher eventos;
    private final ZoneId zonaHoraria;

    public ChatService(ConversacionRepository conversaciones, MensajeChatRepository mensajes, ApartamentoRepository aptoRepo,
                       ResidenteRepository residenteRepo, AccesoService acceso, ApplicationEventPublisher eventos,
                       @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
        this.conversaciones = conversaciones;
        this.mensajes = mensajes;
        this.aptoRepo = aptoRepo;
        this.residenteRepo = residenteRepo;
        this.acceso = acceso;
        this.eventos = eventos;
        this.zonaHoraria = ZoneId.of(zonaHoraria);
    }

    /** La administración: todas las que tienen mensajes. El residente: la suya (si ya existe). */
    @Transactional(readOnly = true)
    public List<ChatDTO.Conversacion> listar() {
        Sesion s = sesion();
        if (s.administracion()) {
            return conversaciones.findByUltimoMensajeEnIsNotNullOrderByUltimoMensajeEnDesc().stream()
                    .map(c -> toDTO(c, true)).toList();
        }
        return conversaciones.findByApartamentoIdAndParticipante(s.apartamentoId(), s.participante())
                .map(c -> List.of(toDTO(c, false))).orElse(List.of());
    }

    /** Abre el chat (lo crea si no existe). */
    public ChatDTO.Conversacion abrir(ChatDTO.Abrir pedido) {
        Sesion s = sesion();
        Long aptoId;
        String participante;
        if (s.administracion()) {
            if (pedido == null || pedido.apartamentoId() == null) throw invalido("Indica el apartamento");
            aptoId = pedido.apartamentoId();
            participante = pedido.participante() == null ? PROPIETARIO : pedido.participante().trim().toUpperCase();
            if (!PROPIETARIO.equals(participante) && !ARRENDATARIO.equals(participante)) {
                throw invalido("El chat es con el propietario o con el arrendatario");
            }
        } else {
            aptoId = s.apartamentoId();
            participante = s.participante();
        }
        Optional<Conversacion> existente = conversaciones.findByApartamentoIdAndParticipante(aptoId, participante);
        if (existente.isPresent()) return toDTO(existente.get(), s.administracion());
        Apartamento a = aptoRepo.findById(aptoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Apartamento no encontrado: " + aptoId));
        return toDTO(conversaciones.save(new Conversacion(a, participante)), s.administracion());
    }

    /** Sin [despuesDe], los últimos 200; con él, solo los más nuevos. */
    @Transactional(readOnly = true)
    public List<ChatDTO.Mensaje> mensajes(Long conversacionId, Long despuesDe) {
        Sesion s = sesion();
        Conversacion c = buscar(conversacionId, s);
        List<MensajeChat> lista;
        if (despuesDe != null) {
            lista = mensajes.findByConversacionIdAndIdGreaterThanOrderByIdAsc(c.getId(), despuesDe);
        } else {
            lista = new ArrayList<>(mensajes.findByConversacionIdOrderByIdDesc(c.getId(), PageRequest.of(0, ULTIMOS)));
            Collections.reverse(lista);
        }
        String propio = s.administracion() ? MensajeChat.ADMINISTRACION : MensajeChat.RESIDENTE;
        return lista.stream().map(m -> new ChatDTO.Mensaje(m.getId(), m.getAutor(), m.getAutorNombre(), m.getTexto(),
                m.getEnviadoEn(), propio.equals(m.getAutor()))).toList();
    }

    public ChatDTO.Mensaje enviar(Long conversacionId, String texto) {
        Sesion s = sesion();
        Conversacion c = buscar(conversacionId, s);
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.isEmpty()) throw invalido("Escribe un mensaje");
        if (limpio.length() > MAXIMO_TEXTO) throw invalido("El mensaje no puede pasar de " + MAXIMO_TEXTO + " caracteres");
        LocalDateTime ahora = LocalDateTime.now(zonaHoraria);
        MensajeChat m = new MensajeChat();
        m.setConversacion(c);
        m.setAutor(s.administracion() ? MensajeChat.ADMINISTRACION : MensajeChat.RESIDENTE);
        m.setAutorNombre(s.administracion() ? "Administración" : nombreResidente(s, c.getApartamento()));
        m.setTexto(limpio);
        m.setEnviadoEn(ahora);
        MensajeChat guardado = mensajes.save(m);
        c.setUltimoMensaje(limpio.length() > 120 ? limpio.substring(0, 117) + "…" : limpio);
        c.setUltimoMensajeEn(ahora);
        if (s.administracion()) {
            c.setNoLeidosResidente(c.getNoLeidosResidente() + 1);
        } else {
            c.setNoLeidosAdmin(c.getNoLeidosAdmin() + 1);
        }
        conversaciones.save(c);
        // Sin el texto: el celular del apartamento puede verlo otra persona del hogar
        Apartamento a = c.getApartamento();
        eventos.publishEvent(new AvisosPush.MensajeChatNuevo(c.getId(), s.administracion() ? a.getId() : null,
                c.getParticipante(), s.administracion() ? "Nuevo mensaje de la administración"
                : "Nuevo mensaje · Torre " + a.getTorre() + " Apto " + a.getApto()));
        return new ChatDTO.Mensaje(guardado.getId(), guardado.getAutor(), guardado.getAutorNombre(), guardado.getTexto(),
                guardado.getEnviadoEn(), true);
    }

    /** Quien consulta ya vio los mensajes del otro lado. */
    public ChatDTO.Conversacion marcarLeido(Long conversacionId) {
        Sesion s = sesion();
        Conversacion c = buscar(conversacionId, s);
        if (s.administracion()) c.setNoLeidosAdmin(0); else c.setNoLeidosResidente(0);
        return toDTO(conversaciones.save(c), s.administracion());
    }

    private ChatDTO.Conversacion toDTO(Conversacion c, boolean administracion) {
        Apartamento a = c.getApartamento();
        return new ChatDTO.Conversacion(c.getId(), a.getId(), a.getTorre(), a.getApto(), c.getParticipante(),
                c.getUltimoMensaje(), c.getUltimoMensajeEn(), administracion ? c.getNoLeidosAdmin() : c.getNoLeidosResidente());
    }

    private Conversacion buscar(Long id, Sesion s) {
        Conversacion c = conversaciones.conApartamento(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Conversación no encontrada: " + id));
        if (!s.administracion() && (!c.getApartamento().getId().equals(s.apartamentoId())
                || !c.getParticipante().equals(s.participante()))) {
            throw new AccessDeniedException("Esta conversación no es tuya");
        }
        return c;
    }

    private String nombreResidente(Sesion s, Apartamento a) {
        if (s.residenteId() != null) {
            Optional<Residente> r = residenteRepo.findById(s.residenteId());
            if (r.isPresent() && r.get().getNombreCompleto() != null) return r.get().getNombreCompleto();
        }
        return (ARRENDATARIO.equals(s.participante()) ? "Arrendatario" : "Propietario")
                + " · Torre " + a.getTorre() + " Apto " + a.getApto();
    }

    /** Quién escribe. Sin token (modo transición) cuenta como la administración. */
    private record Sesion(boolean administracion, Long apartamentoId, String participante, Long residenteId) {}

    private Sesion sesion() {
        Optional<Jwt> token = acceso.tokenActual();
        if (token.isEmpty()) return new Sesion(true, null, null, null);
        Jwt t = token.get();
        String rol = t.getClaimAsString(TokenService.CLAIM_ROL);
        if (TokenService.ROL_ADMIN.equals(rol)) return new Sesion(true, null, null, null);
        if (TokenService.ROL_CONTADOR.equals(rol)) throw new AccessDeniedException("El chat es de la administración");
        Object apto = t.getClaim(TokenService.CLAIM_APARTAMENTO);
        Object residente = t.getClaim(TokenService.CLAIM_RESIDENTE);
        if (!(apto instanceof Number n)) throw new AccessDeniedException("Inicia sesión con tu apartamento");
        return new Sesion(false, n.longValue(), ARRENDATARIO.equals(rol) ? ARRENDATARIO : PROPIETARIO,
                residente instanceof Number r ? r.longValue() : null);
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
