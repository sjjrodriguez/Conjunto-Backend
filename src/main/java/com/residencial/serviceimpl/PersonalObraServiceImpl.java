package com.residencial.serviceimpl;

import com.residencial.almacen.AlmacenArchivos;
import com.residencial.dto.CuotaDTO;
import com.residencial.dto.PersonalObraDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Obra;
import com.residencial.model.PersonalObra;
import com.residencial.repository.ObraRepository;
import com.residencial.repository.PersonalObraRepository;
import com.residencial.security.AccesoService;
import com.residencial.service.NotificacionService;
import com.residencial.service.PersonalObraService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Personal externo de cada obra. Desde P5 cada trabajador lleva su certificado ARL (archivo)
 * y espera la aprobación de la administración: el que se agrega a una obra ya aprobada queda
 * pendiente; el de una obra pendiente se aprueba con ella.
 */
@Service
@Transactional
public class PersonalObraServiceImpl implements PersonalObraService {

    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
    private static final Duration VIGENCIA_ENLACE = Duration.ofMinutes(10);

    private final PersonalObraRepository repo;
    private final ObraRepository obraRepo;
    private final AccesoService acceso;
    private final NotificacionService notificaciones;
    private final AlmacenArchivos almacen;
    private final ZoneId zonaHoraria;

    public PersonalObraServiceImpl(PersonalObraRepository repo, ObraRepository obraRepo, AccesoService acceso,
                                   NotificacionService notificaciones, AlmacenArchivos almacen,
                                   @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
        this.repo = repo;
        this.obraRepo = obraRepo;
        this.acceso = acceso;
        this.notificaciones = notificaciones;
        this.almacen = almacen;
        this.zonaHoraria = ZoneId.of(zonaHoraria);
    }

    private PersonalObraDTO toDTO(PersonalObra p) {
        String torre = "";
        String apto = "";
        if (p.getObra() != null && p.getObra().getApartamento() != null) {
            torre = p.getObra().getApartamento().getTorre();
            apto = p.getObra().getApartamento().getApto();
        }
        return new PersonalObraDTO(p.getId(), p.getNombreTrabajador(), p.getCedula(), p.getArlEstado(),
                p.getObra().getId(), torre, apto, p.getAprobacion(), p.getMotivoRechazo(),
                p.getArlRuta() != null, p.getArlSubidoEn());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonalObraDTO> listarTodos() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonalObraDTO> listarPorObra(Long obraId) {
        obraRepo.findById(obraId).ifPresent(o -> acceso.verificarApartamento(o.apartamentoId()));
        return repo.findByObraId(obraId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public PersonalObraDTO guardar(PersonalObraDTO dto) {
        // Con su apartamento: toDTO necesita torre y apto
        Obra obra = obraRepo.findConApartamentoById(dto.getObraId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra no encontrada: " + dto.getObraId()));
        acceso.verificarApartamento(obra.apartamentoId());
        validar(dto);
        PersonalObra p = new PersonalObra(null, dto.getNombreTrabajador().trim(), dto.getCedula().trim(),
                Boolean.TRUE.equals(dto.getArlEstado()), obra);
        boolean admin = acceso.esAdminActual() || acceso.tokenActual().isEmpty();
        // El residente espera la revisión de la ARL; la administración lo deja aprobado
        p.setAprobacion(admin ? PersonalObra.APROBADO : PersonalObra.PENDIENTE);
        PersonalObra guardado = repo.save(p);
        // De una obra ya aprobada, se revisa aparte; si la obra está pendiente, se revisa con ella
        if (!admin && Obra.APROBADA.equals(obra.getAprobacion())) notificaciones.avisarPersonalNuevo(guardado);
        return toDTO(guardado);
    }

    @Override
    public PersonalObraDTO actualizar(Long id, PersonalObraDTO dto) {
        PersonalObra p = buscar(id);
        Obra obra = obraRepo.findConApartamentoById(p.getObra().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra no encontrada"));
        acceso.verificarApartamento(obra.apartamentoId());
        validar(dto);
        p.setNombreTrabajador(dto.getNombreTrabajador().trim());
        p.setCedula(dto.getCedula().trim());
        p.setArlEstado(Boolean.TRUE.equals(dto.getArlEstado()));
        p.setObra(obra);
        return toDTO(repo.save(p));
    }

    @Override
    public void eliminar(Long id) {
        PersonalObra p = buscar(id);
        obraRepo.findById(p.getObra().getId()).ifPresent(o -> acceso.verificarApartamento(o.apartamentoId()));
        String ruta = p.getArlRuta();
        repo.delete(p);
        if (ruta != null) borrarSinFallar(ruta);
    }

    /** Foto o PDF del certificado ARL; si estaba rechazado, vuelve a revisión. */
    @Override
    public PersonalObraDTO subirCertificado(Long id, MultipartFile archivo) {
        PersonalObra p = buscar(id);
        Obra obra = obraRepo.findConApartamentoById(p.getObra().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra no encontrada"));
        acceso.verificarApartamento(obra.apartamentoId());
        if (archivo == null || archivo.isEmpty()) throw invalido("Adjunta el certificado ARL");
        if (archivo.getSize() > TAMANO_MAXIMO) throw invalido("El certificado no puede pasar de 5 MB");
        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw invalido("No se pudo leer el certificado");
        }
        String[] tipo = tipoReal(datos);
        if (tipo == null) throw invalido("El certificado debe ser una foto (JPG, PNG) o un PDF");
        String anterior = p.getArlRuta();
        String ruta = "arl/obra-" + obra.getId() + "/trabajador-" + p.getId() + "-" + System.currentTimeMillis() + "." + tipo[1];
        almacen.guardar(ruta, datos, tipo[0]);
        p.setArlRuta(ruta);
        p.setArlTipo(tipo[0]);
        p.setArlSubidoEn(LocalDateTime.now(zonaHoraria));
        p.setArlEstado(true);
        if (PersonalObra.RECHAZADO.equals(p.getAprobacion())) {
            p.setAprobacion(PersonalObra.PENDIENTE);
            p.setMotivoRechazo(null);
        }
        p.setObra(obra);
        PersonalObra guardado = repo.save(p);
        if (anterior != null) borrarSinFallar(anterior);
        return toDTO(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public CuotaDTO.Enlace enlaceCertificado(Long id) {
        PersonalObra p = buscar(id);
        obraRepo.findById(p.getObra().getId()).ifPresent(o -> acceso.verificarApartamento(o.apartamentoId()));
        if (p.getArlRuta() == null) throw new RecursoNoEncontradoException("Este trabajador no tiene certificado ARL");
        return new CuotaDTO.Enlace(almacen.enlace(p.getArlRuta(), VIGENCIA_ENLACE), p.getArlTipo(),
                LocalDateTime.now(zonaHoraria).plus(VIGENCIA_ENLACE));
    }

    /** Administración: aprueba o rechaza al trabajador (con motivo); al apartamento le llega el aviso. */
    @Override
    public PersonalObraDTO revisar(Long id, boolean aprobado, String motivo) {
        PersonalObra p = buscar(id);
        Obra obra = obraRepo.findConApartamentoById(p.getObra().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra no encontrada"));
        p.setAprobacion(aprobado ? PersonalObra.APROBADO : PersonalObra.RECHAZADO);
        p.setMotivoRechazo(aprobado || motivo == null || motivo.isBlank() ? null : motivo.trim());
        p.setObra(obra);
        PersonalObra guardado = repo.save(p);
        if (obra.getApartamento() != null) notificaciones.avisarPersonalRevisado(guardado);
        return toDTO(guardado);
    }

    private static void validar(PersonalObraDTO dto) {
        if (dto.getNombreTrabajador() == null || dto.getNombreTrabajador().isBlank()) throw invalido("El nombre es obligatorio");
        if (dto.getCedula() == null || dto.getCedula().isBlank()) throw invalido("La cédula es obligatoria");
    }

    private PersonalObra buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Trabajador no encontrado: " + id));
    }

    private void borrarSinFallar(String ruta) {
        try {
            almacen.borrar(ruta);
        } catch (RuntimeException ignorado) {
            // Si no se pudo borrar el archivo anterior, queda huérfano; no afecta al registro
        }
    }

    // {mime, extensión} por el contenido
    private static String[] tipoReal(byte[] d) {
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return new String[]{"image/jpeg", "jpg"};
        if (d.length > 4 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return new String[]{"image/png", "png"};
        if (d.length > 4 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F') return new String[]{"application/pdf", "pdf"};
        return null;
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
