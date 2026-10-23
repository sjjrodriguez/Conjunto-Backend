package com.residencial.serviceimpl;

import com.residencial.model.Dispositivo;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.DispositivoRepository;
import com.residencial.security.AccesoService;
import com.residencial.security.TokenService;
import com.residencial.service.DispositivoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class DispositivoServiceImpl implements DispositivoService {

    private final DispositivoRepository repo;
    private final ApartamentoRepository apartamentos;
    private final AccesoService acceso;
    private final ZoneId zonaHoraria;

    public DispositivoServiceImpl(DispositivoRepository repo,
                                  ApartamentoRepository apartamentos,
                                  AccesoService acceso,
                                  @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
        this.repo = repo;
        this.apartamentos = apartamentos;
        this.acceso = acceso;
        this.zonaHoraria = ZoneId.of(zonaHoraria);
    }

    @Override
    @Transactional
    public void registrar(String token) {
        // Hace falta saber de quién es el celular: aun en modo transición, exige sesión
        Jwt sesion = acceso.tokenActual()
                .orElseThrow(() -> new AccessDeniedException("Debes iniciar sesión"));
        String rol = sesion.getClaimAsString(TokenService.CLAIM_ROL);
        // El mismo celular pudo tener otra sesión antes (otro apartamento, el admin): se reemplaza
        Dispositivo d = repo.findByToken(token).orElseGet(Dispositivo::new);
        d.setToken(token);
        d.setRol(rol);
        d.setApartamento(TokenService.ROL_ADMIN.equals(rol) ? null
                : sesion.getClaim(TokenService.CLAIM_APARTAMENTO) instanceof Number id
                        ? apartamentos.getReferenceById(id.longValue()) : null);
        d.setActualizado(LocalDateTime.now(zonaHoraria));
        repo.save(d);
    }

    @Override
    @Transactional
    public void darDeBaja(String token) {
        // Al cerrar sesión basta con conocer el token: es un dato que solo tiene ese celular
        repo.borrarTokens(List.of(token));
    }
}
