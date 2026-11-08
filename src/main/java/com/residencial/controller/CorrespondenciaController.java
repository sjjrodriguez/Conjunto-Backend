package com.residencial.controller;

import com.residencial.service.CorrespondenciaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * P9 bloque 3: correspondencia. Registrar, listar y entregar: portería y administración (regla de
 * /api/porteria/** en SecurityConfig). "mia": los residentes, la de su apartamento.
 */
@RestController
@RequestMapping("/api/porteria/correspondencia")
public class CorrespondenciaController {

    private final CorrespondenciaService servicio;

    public CorrespondenciaController(CorrespondenciaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<CorrespondenciaService.Item> listar(@RequestParam(required = false) String estado,
                                                   @RequestParam(required = false) String q,
                                                   @RequestParam(required = false) Integer dias) {
        return servicio.listar(estado, q, dias);
    }

    @PostMapping
    public ResponseEntity<CorrespondenciaService.Item> registrar(@RequestBody CorrespondenciaService.Nueva cuerpo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(cuerpo));
    }

    @PostMapping("/recibos")
    public ResponseEntity<CorrespondenciaService.Lote> reciboParaTodos(@RequestBody CorrespondenciaService.NuevoReciboParaTodos cuerpo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.reciboParaTodos(cuerpo));
    }

    /** P12 bloque 3: portería: pedir al apartamento que autorice entregarla a quien la recoge. */
    @PostMapping("/{id}/solicitud")
    public CorrespondenciaService.Item pedirEntrega(@PathVariable Long id, @RequestBody CorrespondenciaService.PedirEntrega cuerpo) {
        return servicio.pedirEntrega(id, cuerpo);
    }

    /** P12 bloque 3: residentes: las entregas que esperan su autorización. */
    @GetMapping("/solicitudes")
    public List<CorrespondenciaService.Item> solicitudes() {
        return servicio.solicitudesDelApartamento();
    }

    /** P12 bloque 3: el residente (o la portería, si contestó por llamada): AUTORIZAR o RECHAZAR. */
    @PatchMapping("/{id}/solicitud/respuesta")
    public CorrespondenciaService.Item responder(@PathVariable Long id, @RequestBody CorrespondenciaService.Respuesta cuerpo) {
        return servicio.responderEntrega(id, cuerpo == null ? null : cuerpo.decision());
    }

    @GetMapping("/{id}/solicitud/foto")
    public com.residencial.service.FotosPorteria.Enlace fotoDeQuienRecoge(@PathVariable Long id) {
        return servicio.fotoDeQuienRecoge(id);
    }

    /** P12 bloque 3: chat temporal si el residente rechaza la entrega. */
    @GetMapping("/{id}/chat")
    public CorrespondenciaService.Chat chat(@PathVariable Long id) {
        return servicio.chat(id);
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<CorrespondenciaService.MensajeChat> escribir(@PathVariable Long id, @RequestBody CorrespondenciaService.NuevoMensaje cuerpo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.escribir(id, cuerpo));
    }

    /** Solo si el apartamento autorizó la entrega (P12); el nombre es opcional: por defecto, quien autorizó. */
    @PostMapping("/{id}/entrega")
    public CorrespondenciaService.Item entregar(@PathVariable Long id, @RequestBody CorrespondenciaService.Entrega cuerpo) {
        return servicio.entregar(id, cuerpo);
    }

    /** P10 bloque 7: la foto del paquete (portería o el apartamento al que llegó; regla en SecurityConfig). */
    @GetMapping("/{id}/foto")
    public com.residencial.service.FotosPorteria.Enlace foto(@PathVariable Long id) {
        return servicio.foto(id);
    }

    @GetMapping("/mia")
    public List<CorrespondenciaService.Item> mia() {
        return servicio.delApartamento();
    }

    /** P11: el residente quita de su lista algo ya recogido. */
    @DeleteMapping("/mia/{id}")
    public ResponseEntity<Void> quitarMia(@PathVariable Long id) {
        servicio.ocultarDelApartamento(id);
        return ResponseEntity.noContent().build();
    }

    /** P11: la portería quita de su lista algo ya entregado (el registro se conserva). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> quitar(@PathVariable Long id) {
        servicio.ocultarDeLaPorteria(id);
        return ResponseEntity.noContent().build();
    }
}
