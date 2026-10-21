package com.residencial.serviceimpl;

import com.residencial.model.Administrador;
import com.residencial.model.Apartamento;
import com.residencial.repository.AdministradorRepository;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Pattern HASH_BCRYPT = Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$.{53}$");

    private final AdministradorRepository adminRepo;
    private final ApartamentoRepository apartamentoRepo;
    private final PasswordEncoder passwordEncoder;
    // Se compara contra este hash cuando el usuario no existe, para que la
    // respuesta tarde lo mismo y no revele qué usuarios existen
    private final String hashFicticio;

    public AuthServiceImpl(AdministradorRepository adminRepo,
                           ApartamentoRepository apartamentoRepo,
                           PasswordEncoder passwordEncoder) {
        this.adminRepo = adminRepo;
        this.apartamentoRepo = apartamentoRepo;
        this.passwordEncoder = passwordEncoder;
        this.hashFicticio = passwordEncoder.encode("contrasena-ficticia");
    }

    @Override
    public Administrador loginAdmin(String usuario, String contrasena) {
        if (vacio(usuario) || vacio(contrasena)) return null;
        Optional<Administrador> encontrado = adminRepo.findByUsuario(usuario);
        if (encontrado.isEmpty()) {
            passwordEncoder.matches(contrasena, hashFicticio);
            return null;
        }
        Administrador admin = encontrado.get();
        String guardada = admin.getContrasena();
        if (!verificar(contrasena, guardada, admin::setContrasena)) return null;
        if (!guardada.equals(admin.getContrasena())) adminRepo.save(admin);
        return admin;
    }

    @Override
    public Apartamento loginApartamento(String torre, String apto, String contrasena) {
        if (vacio(torre) || vacio(apto) || vacio(contrasena)) return null;
        for (Apartamento a : apartamentoRepo.findByTorreAndApto(torre, apto)) {
            String guardada = a.getContrasena();
            if (verificar(contrasena, guardada, a::setContrasena)) {
                if (!guardada.equals(a.getContrasena())) apartamentoRepo.save(a);
                return a;
            }
        }
        passwordEncoder.matches(contrasena, hashFicticio);
        return null;
    }

    /**
     * Compara la contraseña recibida con la guardada. Si la guardada aún está en
     * texto plano (datos anteriores a BCrypt) y coincide, se migra a BCrypt
     * mediante {@code actualizarHash}.
     */
    private boolean verificar(String recibida, String guardada, Consumer<String> actualizarHash) {
        if (guardada == null) return false;
        if (HASH_BCRYPT.matcher(guardada).matches()) {
            return passwordEncoder.matches(recibida, guardada);
        }
        boolean coincide = MessageDigest.isEqual(
                recibida.getBytes(StandardCharsets.UTF_8),
                guardada.getBytes(StandardCharsets.UTF_8));
        if (coincide) actualizarHash.accept(passwordEncoder.encode(recibida));
        return coincide;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
