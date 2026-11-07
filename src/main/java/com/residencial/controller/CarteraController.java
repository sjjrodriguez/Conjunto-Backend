package com.residencial.controller;

import com.residencial.dto.CuotaDTO;
import com.residencial.service.ContabilidadService;
import com.residencial.service.CuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Cartera de la administración (P5 bloque 7) y, desde P11, del contador: estadísticas, el panel,
 * la cartera de cada apartamento y las solicitudes (con versiones e historial). Revisar es solo
 * del administrador; corregir o editar una solicitud, solo del contador (SecurityConfig).
 */
@RestController
@RequestMapping("/api/cartera")
@RequiredArgsConstructor
public class CarteraController {

    private final CuotaService service;
    private final ContabilidadService contabilidad;

    /** Sin periodo, el mes actual. */
    @GetMapping("/estadisticas")
    public CuotaDTO.Estadisticas estadisticas(@RequestParam(required = false) String periodo) {
        return service.estadisticas(periodo);
    }

    /** P11: el inicio del contador (el mes, los morosos, sus solicitudes y el recaudo de 6 meses). */
    @GetMapping("/panel")
    public ContabilidadService.Panel panel() {
        return contabilidad.panel();
    }

    /** P11: la cartera de cada apartamento, de una torre o de todas. */
    @GetMapping("/apartamentos")
    public List<ContabilidadService.AptoCartera> apartamentos(@RequestParam(required = false) String torre) {
        return contabilidad.aptos(torre);
    }

    @GetMapping("/apartamentos/{id}")
    public ContabilidadService.CarteraApartamento apartamento(@PathVariable Long id) {
        return contabilidad.apartamentoCartera(id);
    }

    @PutMapping("/apartamentos/{id}/coeficiente")
    public ContabilidadService.AptoCartera coeficiente(@PathVariable Long id, @RequestBody ContabilidadService.Coeficiente cuerpo) {
        return contabilidad.ponerCoeficiente(id, cuerpo.coeficiente());
    }

    /** P11: cuánto le toca a cada apartamento antes de mandar la facturación. */
    @PostMapping("/facturacion/vista-previa")
    public ContabilidadService.VistaPrevia vistaPrevia(@RequestBody ContabilidadService.Facturacion f) {
        return contabilidad.vistaPrevia(f, null);
    }

    /** estado: PENDIENTE, APROBADA o RECHAZADA (DEVUELTA); sin estado, todas (las más recientes primero). */
    @GetMapping("/solicitudes")
    public List<ContabilidadService.Solicitud> solicitudes(@RequestParam(required = false) String estado) {
        return contabilidad.listar(estado);
    }

    @GetMapping("/solicitudes/{id}")
    public ContabilidadService.Detalle detalle(@PathVariable Long id) {
        return contabilidad.detalle(id);
    }

    /** P11: el contador manda algo a aprobar (facturación, cuota nueva, cambio, anular o pago). */
    @PostMapping("/solicitudes")
    public ResponseEntity<ContabilidadService.Solicitud> enviar(@RequestBody ContabilidadService.Envio cuerpo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contabilidad.enviar(cuerpo));
    }

    /** P11: el contador la corrige (pendiente o devuelta) o edita lo ya aprobado: vuelve a aprobación. */
    @PutMapping("/solicitudes/{id}/propuesta")
    public ContabilidadService.Solicitud proponer(@PathVariable Long id, @RequestBody ContabilidadService.Propuesta cuerpo) {
        return contabilidad.proponer(id, cuerpo);
    }

    /** El administrador aprueba o la devuelve con el motivo. */
    @PutMapping("/solicitudes/{id}")
    public ContabilidadService.Solicitud revisar(@PathVariable Long id, @RequestBody CuotaDTO.RevisionSolicitud revision) {
        return contabilidad.revisar(id, revision.aprobada(), revision.motivo());
    }
}
