package com.residencial.service.impl;

import com.residencial.dto.ApartamentoDetalleResponse;
import com.residencial.dto.ApartamentoResumenResponse;
import com.residencial.dto.PropietarioResponse;
import com.residencial.dto.ResidenteResponse;
import com.residencial.model.Apartamento;
import com.residencial.model.Propietario;
import com.residencial.model.Residente;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.service.ApartamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApartamentoServiceImpl implements ApartamentoService {

    private final ApartamentoRepository apartamentoRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ApartamentoResumenResponse> listarPorTorre(long idTorre) {
        return apartamentoRepository.findByTorreId(idTorre).stream()
                .map(this::toResumen)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ApartamentoDetalleResponse obtenerDetalle(long idApartamento) {
        Apartamento ap = apartamentoRepository.findById(idApartamento)
                .orElseThrow(() -> new NoSuchElementException("Apartamento no encontrado: " + idApartamento));

        return ApartamentoDetalleResponse.builder()
                .id(ap.getId())
                .numero(ap.getNumero())
                .piso(ap.getPiso())
                .torre(ap.getTorre().getNombre())
                .conjunto(ap.getTorre().getConjunto().getNombre())
                .propietario(ap.getPropietario() != null ? toPropietarioResponse(ap.getPropietario()) : null)
                .residentes(ap.getResidentes().stream()
                        .map(this::toResidenteResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private ApartamentoResumenResponse toResumen(Apartamento ap) {
        return ApartamentoResumenResponse.builder()
                .id(ap.getId())
                .numero(ap.getNumero())
                .piso(ap.getPiso())
                .idTorre(ap.getTorre().getId())
                .nombreTorre(ap.getTorre().getNombre())
                .build();
    }

    private PropietarioResponse toPropietarioResponse(Propietario p) {
        return PropietarioResponse.builder()
                .id(p.getId())
                .nombreCompleto(p.getNombreCompleto())
                .documento(p.getDocumento())
                .telefono(p.getTelefono())
                .email(p.getEmail())
                .build();
    }

    private ResidenteResponse toResidenteResponse(Residente r) {
        return ResidenteResponse.builder()
                .id(r.getId())
                .nombreCompleto(r.getNombreCompleto())
                .documento(r.getDocumento())
                .telefono(r.getTelefono())
                .email(r.getEmail())
                .tipoResidente(r.getTipoResidente() != null ? r.getTipoResidente().name() : null)
                .build();
    }
}
