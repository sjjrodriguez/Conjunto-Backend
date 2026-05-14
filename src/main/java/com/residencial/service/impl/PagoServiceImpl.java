package com.residencial.service.impl;

import com.residencial.dto.CarteraResponse;
import com.residencial.dto.PagoRequest;
import com.residencial.dto.PagoResponse;
import com.residencial.model.Apartamento;
import com.residencial.model.Pago;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.PagoRepository;
import com.residencial.service.PagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PagoServiceImpl implements PagoService {

    private final PagoRepository pagoRepository;
    private final ApartamentoRepository apartamentoRepository;

    @Override
    @Transactional
    public PagoResponse registrarPago(PagoRequest request) {
        Apartamento apartamento = apartamentoRepository.findById(request.getIdApartamento())
                .orElseThrow(() -> new NoSuchElementException("Apartamento no encontrado: " + request.getIdApartamento()));

        if (pagoRepository.existsByApartamentoIdAndMesAndAnio(
                request.getIdApartamento(), request.getMes(), request.getAnio())) {
            throw new IllegalStateException(
                    "Ya existe un pago registrado para el mes " + request.getMes() + "/" + request.getAnio());
        }

        Pago pago = Pago.builder()
                .apartamento(apartamento)
                .mes(request.getMes())
                .anio(request.getAnio())
                .valor(request.getValor())
                .estado(Pago.EstadoPago.PAGADO)
                .fechaPago(LocalDateTime.now())
                .observaciones(request.getObservaciones())
                .build();

        Pago saved = pagoRepository.save(pago);
        return toPagoResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CarteraResponse obtenerCartera(long idApartamento) {
        Apartamento apartamento = apartamentoRepository.findById(idApartamento)
                .orElseThrow(() -> new NoSuchElementException("Apartamento no encontrado: " + idApartamento));

        List<Pago> pendientes = pagoRepository.findByApartamentoIdAndEstado(
                idApartamento, Pago.EstadoPago.PENDIENTE);

        pendientes.addAll(pagoRepository.findByApartamentoIdAndEstado(
                idApartamento, Pago.EstadoPago.VENCIDO));

        BigDecimal saldo = pendientes.stream()
                .map(Pago::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PagoResponse> pendientesDto = pendientes.stream()
                .map(this::toPagoResponse)
                .collect(Collectors.toList());

        return CarteraResponse.builder()
                .idApartamento(apartamento.getId())
                .numeroApartamento(apartamento.getNumero())
                .saldoPendiente(saldo)
                .cuotasPendientes(pendientes.size())
                .estadoCartera(pendientes.isEmpty() ? "AL DIA" : "EN MORA")
                .pagosPendientes(pendientesDto)
                .build();
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

    private PagoResponse toPagoResponse(Pago p) {
        return PagoResponse.builder()
                .id(p.getId())
                .mes(p.getMes())
                .anio(p.getAnio())
                .valor(p.getValor())
                .estado(p.getEstado() != null ? p.getEstado().name() : null)
                .fechaPago(p.getFechaPago())
                .observaciones(p.getObservaciones())
                .idApartamento(p.getApartamento().getId())
                .numeroApartamento(p.getApartamento().getNumero())
                .build();
    }
}
