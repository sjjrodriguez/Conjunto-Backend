package com.residencial.service;

import com.residencial.dto.CuotaDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Facturación mensual (P4 bloque 7): cuotas, comprobantes y la cartera que resulta de ellas. */
public interface CuotaService {
    List<CuotaDTO> listar(String periodo, String estado);
    List<CuotaDTO> listarPorApartamento(Long apartamentoId);
    CuotaDTO.Generadas generar(CuotaDTO.Generar solicitud);
    CuotaDTO subirComprobante(Long id, MultipartFile archivo);
    CuotaDTO.Enlace enlaceComprobante(Long id);
    CuotaDTO revisar(Long id, String estado, String motivo);
    /** Recalcula el resumen de cartera (tabla pago) del apartamento con sus cuotas. */
    void recalcularCartera(Long apartamentoId);
}
