package com.residencial.controller;

import com.residencial.dto.CuotaDTO;
import com.residencial.service.CuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Cartera de la administración (P5 bloque 7): estadísticas del mes y lo que el contador mandó a
 * aprobar (facturación del mes y pagos sin comprobante). Revisar es solo del administrador.
 */
@RestController
@RequestMapping("/api/cartera")
@RequiredArgsConstructor
public class CarteraController {

    private final CuotaService service;

    /** Sin periodo, el mes actual. */
    @GetMapping("/estadisticas")
    public CuotaDTO.Estadisticas estadisticas(@RequestParam(required = false) String periodo) {
        return service.estadisticas(periodo);
    }

    /** estado: PENDIENTE, APROBADA o RECHAZADA; sin estado, todas (las más recientes primero). */
    @GetMapping("/solicitudes")
    public List<CuotaDTO.Solicitud> solicitudes(@RequestParam(required = false) String estado) {
        return service.listarSolicitudes(estado);
    }

    @PutMapping("/solicitudes/{id}")
    public CuotaDTO.Solicitud revisar(@PathVariable Long id, @RequestBody CuotaDTO.RevisionSolicitud revision) {
        return service.revisarSolicitud(id, revision.aprobada(), revision.motivo());
    }
}
