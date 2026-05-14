package com.residencial.service;

import com.residencial.dto.CarteraResponse;
import com.residencial.dto.PagoRequest;
import com.residencial.dto.PagoResponse;

public interface PagoService {
    PagoResponse registrarPago(PagoRequest request);
    CarteraResponse obtenerCartera(long idApartamento);
}
