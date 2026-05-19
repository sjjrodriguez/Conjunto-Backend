package com.residencial.serviceimpl;

import com.residencial.dto.PagoDTO;
import com.residencial.model.Apartamento;
import com.residencial.model.Pago;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.PagoRepository;
import com.residencial.service.PagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PagoServiceImpl implements PagoService {

    private final PagoRepository repo;
    private final ApartamentoRepository aptoRepo;

    private PagoDTO toDTO(Pago p) {
        return new PagoDTO(p.getId(), p.getSaldoPendiente(),
                p.getEstadoCuenta(), p.getObservaciones(), p.getApartamento().getId());
    }

    @Override
    public PagoDTO consultarPorApartamento(Long apartamentoId) {
        return repo.findByApartamentoId(apartamentoId).map(this::toDTO).orElse(null);
    }

    @Override
    public PagoDTO simularPago(Long apartamentoId) {
        return repo.findByApartamentoId(apartamentoId).map(p -> {
            if (p.getSaldoPendiente() <= 0) {
                throw new RuntimeException("El saldo ya está en cero. No hay pago pendiente.");
            }
            p.setSaldoPendiente(0.0);
            p.setEstadoCuenta("Al Día");
            p.setObservaciones("Pago simulado exitosamente.");
            return toDTO(repo.save(p));
        }).orElseThrow(() -> new RuntimeException("No se encontró cartera para el apartamento."));
    }

    @Override
    public PagoDTO guardar(PagoDTO dto) {
        Apartamento apto = aptoRepo.findById(dto.getApartamentoId())
                .orElseThrow(() -> new RuntimeException("Apartamento no encontrado"));
        Pago p = new Pago(null, dto.getSaldoPendiente(), dto.getEstadoCuenta(),
                dto.getObservaciones(), apto);
        return toDTO(repo.save(p));
    }

    @Override
    public PagoDTO actualizar(Long id, PagoDTO dto) {
        return repo.findById(id).map(p -> {
            p.setSaldoPendiente(dto.getSaldoPendiente());
            p.setEstadoCuenta(dto.getEstadoCuenta());
            p.setObservaciones(dto.getObservaciones());
            return toDTO(repo.save(p));
        }).orElse(null);
    }
}
