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
    CuotaDTO actualizar(Long id, CuotaDTO.Cambio cambio);
    void eliminar(Long id);
    /** Recalcula el resumen de cartera (tabla pago) del apartamento con sus cuotas. */
    void recalcularCartera(Long apartamentoId);

    // P5 bloque 7: lo que manda el contador espera al administrador; estadísticas de la cartera
    List<CuotaDTO.Solicitud> listarSolicitudes(String estado);
    CuotaDTO.Solicitud revisarSolicitud(Long id, boolean aprobada, String motivo);
    CuotaDTO.Estadisticas estadisticas(String periodo);
}
