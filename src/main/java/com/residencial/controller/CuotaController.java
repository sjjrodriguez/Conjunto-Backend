package com.residencial.controller;

import com.residencial.dto.CuotaDTO;
import com.residencial.service.CuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/cuotas")
@RequiredArgsConstructor
public class CuotaController {

    private final CuotaService service;

    /** Contador o administrador: las cuotas de un mes y/o en un estado. */
    @GetMapping
    public List<CuotaDTO> listar(@RequestParam(required = false) String periodo,
                                 @RequestParam(required = false) String estado) {
        return service.listar(periodo, estado);
    }

    @GetMapping("/apartamento/{apartamentoId}")
    public List<CuotaDTO> listarPorApartamento(@PathVariable Long apartamentoId) {
        return service.listarPorApartamento(apartamentoId);
    }

    /** La cuota del mes para todos los apartamentos que aún no la tienen. */
    @PostMapping("/generar")
    public CuotaDTO.Generadas generar(@RequestBody CuotaDTO.Generar solicitud) {
        return service.generar(solicitud);
    }

    /** El residente sube la foto o el PDF del pago (campo "archivo"). */
    @PostMapping(value = "/{id}/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CuotaDTO subirComprobante(@PathVariable Long id, @RequestPart("archivo") MultipartFile archivo) {
        return service.subirComprobante(id, archivo);
    }

    /** Enlace temporal (10 minutos) para ver el comprobante. */
    @GetMapping("/{id}/comprobante")
    public CuotaDTO.Enlace enlaceComprobante(@PathVariable Long id) {
        return service.enlaceComprobante(id);
    }

    @PutMapping("/{id}/estado")
    public CuotaDTO revisar(@PathVariable Long id, @RequestBody CuotaDTO.Revision revision) {
        return service.revisar(id, revision.estado(), revision.motivo());
    }
}
