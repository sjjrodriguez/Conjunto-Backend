package com.residencial.serviceimpl;

import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.repository.AdministradorRepository;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AdministradorRepository adminRepo;
    private final ApartamentoRepository apartamentoRepo;

    @Override
    public Administrador loginAdmin(String usuario, String contrasena) {
        return adminRepo.findByUsuarioAndContrasena(usuario, contrasena)
                .orElse(null);
    }

    @Override
    public Apartamento loginApartamento(String torre, String apto, String contrasena) {
        return apartamentoRepo.findByTorreAndAptoAndContrasena(torre, apto, contrasena)
                .orElse(null);
    }
}
