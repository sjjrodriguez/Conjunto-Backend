package com.residencial.serviceimpl;

import com.residencial.almacen.AlmacenArchivos;
import com.residencial.dto.PublicacionDTO;
import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.Apartamento;
import com.residencial.model.Publicacion;
import com.residencial.model.PublicacionAdjunto;
import com.residencial.repository.ApartamentoRepository;
import com.residencial.repository.PublicacionRepository;
import com.residencial.security.AccesoService;
import com.residencial.service.NotificacionService;
import com.residencial.service.PublicacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * La administración publica texto, imágenes y archivos; todos los residentes lo ven en Inicio
 * y les llega el aviso. Los archivos quedan en un bucket privado y se ven con enlaces firmados.
 */
@Service
@Transactional
public class PublicacionServiceImpl implements PublicacionService {

    private static final Logger log = LoggerFactory.getLogger(PublicacionServiceImpl.class);
    static final int POR_PAGINA = 20;
    static final int MAXIMO_ADJUNTOS = 6;
    static final long TAMANO_MAXIMO = 10L * 1024 * 1024;
    static final int LARGO_MAXIMO = 4000;
    private static final Duration VIGENCIA = Duration.ofHours(1);
    private static final Map<String, String> OFFICE = Map.of(
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PublicacionRepository repo;
    private final ApartamentoRepository aptoRepo;
    private final AccesoService acceso;
    private final NotificacionService notificaciones;
    private final AlmacenArchivos almacen;
    private final ZoneId zonaHoraria;

    public PublicacionServiceImpl(PublicacionRepository repo, ApartamentoRepository aptoRepo,
                                  AccesoService acceso, NotificacionService notificaciones,
                                  @Qualifier("almacenPublicaciones") AlmacenArchivos almacen,
                                  @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
        this.repo = repo;
        this.aptoRepo = aptoRepo;
        this.acceso = acceso;
        this.notificaciones = notificaciones;
        this.almacen = almacen;
        this.zonaHoraria = ZoneId.of(zonaHoraria);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicacionDTO.Pagina listar(int pagina) {
        Page<Publicacion> page = repo.findAllByOrderByCreadaEnDescIdDesc(PageRequest.of(Math.max(pagina, 0), POR_PAGINA));
        List<Long> ids = page.getContent().stream().map(Publicacion::getId).toList();
        Map<Long, List<PublicacionAdjunto>> adjuntos = ids.isEmpty() ? Map.of()
                : repo.adjuntosDe(ids).stream().collect(Collectors.groupingBy(a -> a.getPublicacion().getId()));
        // Todos los enlaces de la página en una sola petición al almacenamiento
        Map<String, String> enlaces = almacen.enlaces(adjuntos.values().stream().flatMap(List::stream)
                .map(PublicacionAdjunto::getRuta).toList(), VIGENCIA);
        List<PublicacionDTO> lista = page.getContent().stream()
                .map(p -> toDTO(p, adjuntos.getOrDefault(p.getId(), List.of()), enlaces)).toList();
        return new PublicacionDTO.Pagina(lista, page.hasNext());
    }

    @Override
    public PublicacionDTO publicar(String texto, List<MultipartFile> archivos) {
        List<MultipartFile> lista = archivos == null ? List.of() : archivos.stream().filter(a -> a != null && !a.isEmpty()).toList();
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty() && lista.isEmpty()) throw invalido("Escribe algo o adjunta una imagen o un archivo");
        if (limpio.length() > LARGO_MAXIMO) throw invalido("El texto puede tener máximo " + LARGO_MAXIMO + " caracteres");
        if (lista.size() > MAXIMO_ADJUNTOS) throw invalido("Máximo " + MAXIMO_ADJUNTOS + " adjuntos por publicación");

        Publicacion p = new Publicacion();
        p.setTexto(limpio);
        p.setCreadaEn(LocalDateTime.now(zonaHoraria));
        p.setAutorId(idAdministrador());
        // Hasta que las cuentas tengan nombre (P5 bloque 7), firma la administración
        p.setAutorNombre("Administración");
        p = repo.save(p);

        List<String> subidas = new ArrayList<>();
        try {
            int orden = 0;
            for (MultipartFile archivo : lista) {
                if (archivo.getSize() > TAMANO_MAXIMO) throw invalido("Cada archivo puede pesar máximo 10 MB");
                byte[] datos = archivo.getBytes();
                String nombre = nombreSeguro(archivo.getOriginalFilename());
                String[] tipo = tipoReal(datos, nombre);
                if (tipo == null) throw invalido("Solo se pueden adjuntar imágenes (JPG, PNG, WEBP), PDF, Word o Excel");
                String ruta = "publicaciones/" + p.getId() + "/" + orden + "-" + UUID.randomUUID() + "." + tipo[2];
                almacen.guardar(ruta, datos, tipo[1]);
                subidas.add(ruta);
                p.getAdjuntos().add(new PublicacionAdjunto(null, p, tipo[0], ruta, nombre, tipo[1], datos.length, orden++));
            }
        } catch (IOException e) {
            borrarSinFallar(subidas);
            throw invalido("No se pudo leer un archivo");
        } catch (RuntimeException e) {
            // La transacción se deshace: que tampoco queden archivos sueltos
            borrarSinFallar(subidas);
            throw e;
        }
        Publicacion guardada = repo.save(p);
        notificaciones.avisarPublicacion(guardada, aptoRepo.findAll().stream().map(Apartamento::getId).toList());
        Map<String, String> enlaces = almacen.enlaces(subidas, VIGENCIA);
        return toDTO(guardada, guardada.getAdjuntos(), enlaces);
    }

    @Override
    public PublicacionDTO editar(Long id, String texto) {
        Publicacion p = buscar(id);
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty() && p.getAdjuntos().isEmpty()) throw invalido("La publicación no puede quedar vacía");
        if (limpio.length() > LARGO_MAXIMO) throw invalido("El texto puede tener máximo " + LARGO_MAXIMO + " caracteres");
        p.setTexto(limpio);
        p.setEditadaEn(LocalDateTime.now(zonaHoraria));
        Publicacion guardada = repo.save(p);
        Map<String, String> enlaces = almacen.enlaces(guardada.getAdjuntos().stream().map(PublicacionAdjunto::getRuta).toList(), VIGENCIA);
        return toDTO(guardada, guardada.getAdjuntos(), enlaces);
    }

    @Override
    public void eliminar(Long id) {
        Publicacion p = buscar(id);
        List<String> rutas = p.getAdjuntos().stream().map(PublicacionAdjunto::getRuta).toList();
        repo.delete(p);
        borrarSinFallar(rutas);
    }

    private PublicacionDTO toDTO(Publicacion p, List<PublicacionAdjunto> adjuntos, Map<String, String> enlaces) {
        return new PublicacionDTO(p.getId(), p.getTexto(), p.getAutorNombre(), p.getCreadaEn(), p.getEditadaEn(),
                adjuntos.stream().map(a -> new PublicacionDTO.Adjunto(a.getId(), a.getTipo(), a.getNombre(), a.getMime(),
                        a.getTamano(), enlaces.get(a.getRuta()))).toList());
    }

    private Publicacion buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Publicación no encontrada: " + id));
    }

    private Long idAdministrador() {
        return acceso.tokenActual().map(t -> t.getSubject())
                .filter(s -> s != null && s.startsWith("admin:"))
                .map(s -> Long.valueOf(s.substring("admin:".length()))).orElse(null);
    }

    private void borrarSinFallar(Collection<String> rutas) {
        for (String r : rutas) {
            try {
                almacen.borrar(r);
            } catch (RuntimeException e) {
                log.warn("No se pudo borrar el adjunto {}", r);
            }
        }
    }

    /** {tipo, mime, extensión} por el contenido (y la extensión para Word/Excel); null si no se acepta. */
    private static String[] tipoReal(byte[] d, String nombre) {
        if (empieza(d, 0xFF, 0xD8, 0xFF)) return new String[]{PublicacionAdjunto.IMAGEN, "image/jpeg", "jpg"};
        if (empieza(d, 0x89, 'P', 'N', 'G')) return new String[]{PublicacionAdjunto.IMAGEN, "image/png", "png"};
        if (empieza(d, 'R', 'I', 'F', 'F') && d.length > 12 && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return new String[]{PublicacionAdjunto.IMAGEN, "image/webp", "webp"};
        }
        if (empieza(d, '%', 'P', 'D', 'F')) return new String[]{PublicacionAdjunto.ARCHIVO, "application/pdf", "pdf"};
        String ext = nombre.contains(".") ? nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (empieza(d, 'P', 'K', 3, 4) && OFFICE.containsKey(ext)) return new String[]{PublicacionAdjunto.ARCHIVO, OFFICE.get(ext), ext};
        return null;
    }

    private static boolean empieza(byte[] d, int... firma) {
        if (d.length < firma.length) return false;
        for (int i = 0; i < firma.length; i++) if ((d[i] & 0xFF) != firma[i]) return false;
        return true;
    }

    // Lo que se muestra al descargar: sin rutas ni caracteres raros
    private static String nombreSeguro(String original) {
        String n = original == null ? "archivo" : original.replaceAll(".*[/\\\\]", "").replaceAll("[^\\p{L}\\p{N} ._()-]", "_").trim();
        if (n.isEmpty()) n = "archivo";
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
