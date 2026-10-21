package com.residencial.serviceimpl;

import com.residencial.dto.ApartamentoDTO;
import com.residencial.dto.ApartamentoRequest;
import com.residencial.model.Apartamento;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.security.AccesoService;
import com.residencial.service.ApartamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApartamentoServiceImpl implements ApartamentoService {

    private static final int MIN_CONTRASENA = 6;

    private final ApartamentoRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final AccesoService acceso;

    private ApartamentoDTO toDTO(Apartamento a) {
        return new ApartamentoDTO(a.getId(), a.getTorre(), a.getApto());
    }

    private String hashValidado(String contrasena) {
        if (contrasena == null || contrasena.isBlank() || contrasena.length() < MIN_CONTRASENA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener al menos " + MIN_CONTRASENA + " caracteres");
        }
        return passwordEncoder.encode(contrasena);
    }

    @Override
    public List<ApartamentoDTO> listarTodos() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public ApartamentoDTO buscarPorId(Long id) {
        acceso.verificarApartamento(id);
        return repo.findById(id).map(this::toDTO).orElse(null);
    }

    @Override
    public ApartamentoDTO guardar(ApartamentoRequest data) {
        Apartamento a = new Apartamento(null, data.getTorre(), data.getApto(), hashValidado(data.getContrasena()));
        return toDTO(repo.save(a));
    }

    @Override
    public ApartamentoDTO actualizar(Long id, ApartamentoRequest data) {
        return repo.findById(id).map(a -> {
            a.setTorre(data.getTorre());
            a.setApto(data.getApto());
            // Si no se envía contraseña se conserva la actual
            if (data.getContrasena() != null && !data.getContrasena().isBlank()) {
                a.setContrasena(hashValidado(data.getContrasena()));
            }
            return toDTO(repo.save(a));
        }).orElse(null);
    }

    @Override
    public boolean cambiarContrasena(Long id, String nuevaContrasena) {
        return repo.findById(id).map(a -> {
            a.setContrasena(hashValidado(nuevaContrasena));
            repo.save(a);
            return true;
        }).orElse(false);
    }

    @Override
    public void eliminar(Long id) {
        repo.deleteById(id);
    }
}
