package com.residencial.controller;

import com.residencial.dto.ChatDTO;
import com.residencial.serviceimpl.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Chat administración ⇄ residente (P5 bloque 8). Quién ve qué lo decide ChatService con la sesión. */
@RestController
@RequestMapping("/api/chat/conversaciones")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService service;

    @GetMapping
    public List<ChatDTO.Conversacion> listar() {
        return service.listar();
    }

    @PostMapping
    public ChatDTO.Conversacion abrir(@RequestBody(required = false) ChatDTO.Abrir pedido) {
        return service.abrir(pedido);
    }

    @GetMapping("/{id}/mensajes")
    public List<ChatDTO.Mensaje> mensajes(@PathVariable Long id, @RequestParam(required = false) Long despuesDe) {
        return service.mensajes(id, despuesDe);
    }

    @PostMapping("/{id}/mensajes")
    public ChatDTO.Mensaje enviar(@PathVariable Long id, @RequestBody ChatDTO.Nuevo nuevo) {
        return service.enviar(id, nuevo.texto());
    }

    @PutMapping("/{id}/leido")
    public ChatDTO.Conversacion marcarLeido(@PathVariable Long id) {
        return service.marcarLeido(id);
    }
}
