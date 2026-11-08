package com.residencial.service;

import com.residencial.almacen.AlmacenArchivos;
import com.residencial.conjunto.ConjuntoContexto;
import com.residencial.model.Correspondencia;
import com.residencial.model.VisitaPorteria;
import com.residencial.repository.CorrespondenciaRepository;
import com.residencial.repository.VisitaPorteriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * P12: tareas de la portería que corren solas, en todos los conjuntos.
 *
 * - Recordatorio: si un visitante autorizado lleva [horasRecordatorio] adentro sin salida, se le
 *   pregunta al apartamento si sigue adentro (y luego cada [horasRecordatorio] mientras siga, hasta
 *   que pasen las 24 h en que la portería lo cuenta como dentro). De noche no se envía.
 * - Retención: las fotos de visitantes, de paquetes y de quien recoge se borran a los
 *   [diasFotos] días; el registro (nombre, horas) se conserva sin la foto.
 */
@Service
public class TareasPorteria {

    private static final Logger log = LoggerFactory.getLogger(TareasPorteria.class);
    private static final int LOTE = 200;

    private final VisitaPorteriaRepository visitas;
    private final CorrespondenciaRepository correspondencia;
    private final NotificacionService notificaciones;
    private final AlmacenArchivos almacen;
    private final TransactionTemplate tx;
    private final ZoneId zona;
    private final int horasRecordatorio;
    private final int diasFotos;
    private final int horaSilencioDesde;
    private final int horaSilencioHasta;

    public TareasPorteria(VisitaPorteriaRepository visitas, CorrespondenciaRepository correspondencia,
                          NotificacionService notificaciones, AlmacenArchivos almacenArchivos, TransactionTemplate tx,
                          @Value("${app.zona-horaria:America/Bogota}") String zona,
                          @Value("${app.porteria.recordatorio-horas:3}") int horasRecordatorio,
                          @Value("${app.porteria.retencion-fotos-dias:90}") int diasFotos,
                          @Value("${app.porteria.silencio-desde:22}") int horaSilencioDesde,
                          @Value("${app.porteria.silencio-hasta:6}") int horaSilencioHasta) {
        this.visitas = visitas;
        this.correspondencia = correspondencia;
        this.notificaciones = notificaciones;
        this.almacen = almacenArchivos;
        this.tx = tx;
        this.zona = ZoneId.of(zona);
        this.horasRecordatorio = horasRecordatorio;
        this.diasFotos = diasFotos;
        this.horaSilencioDesde = horaSilencioDesde;
        this.horaSilencioHasta = horaSilencioHasta;
    }

    /** Cada 5 minutos: "¿tu visitante sigue adentro?". */
    @Scheduled(fixedDelayString = "${app.porteria.recordatorio-ms:300000}", initialDelayString = "${app.porteria.recordatorio-inicio-ms:60000}")
    public void recordarVisitasDentro() {
        LocalDateTime ahora = LocalDateTime.now(zona);
        if (enSilencio(ahora.getHour())) return;
        recordar(ahora);
    }

    /** Envía los recordatorios que tocan a [ahora]; devuelve cuántos. */
    public int recordar(LocalDateTime ahora) {
        if (horasRecordatorio <= 0) return 0;
        LocalDateTime ventana = ahora.minusHours(PorteriaTableroService.HORAS_DENTRO);
        LocalDateTime limite = ahora.minusHours(horasRecordatorio);
        // Se buscan como raíz (todos los conjuntos); la notificación se crea dentro de su conjunto
        List<long[]> pendientes = ConjuntoContexto.comoRaiz(() -> tx.execute(s -> visitas.paraRecordar(ventana, limite)
                .stream().map(v -> new long[]{v.getId(), v.getConjuntoId()}).toList()));
        int enviados = 0;
        for (long[] p : pendientes) {
            try {
                Boolean ok = ConjuntoContexto.en(p[1], () -> tx.execute(s -> {
                    VisitaPorteria v = visitas.findById(p[0]).orElse(null);
                    if (v == null || v.getSalidaEn() != null || !VisitaPorteria.AUTORIZADA.equals(v.getEstado())) return false;
                    LocalDateTime desde = v.getRespondidaEn() != null ? v.getRespondidaEn() : v.getCreadaEn();
                    v.setRecordatorioEn(ahora);
                    notificaciones.avisarSigueDentro(v, Math.max(0, Duration.between(desde, ahora).toMinutes()));
                    return true;
                }));
                if (Boolean.TRUE.equals(ok)) enviados++;
            } catch (RuntimeException e) {
                log.warn("No se pudo enviar el recordatorio de la visita {}: {}", p[0], e.getMessage());
            }
        }
        return enviados;
    }

    /** Todos los días a las 3:30 a. m.: borra las fotos que pasaron el tiempo de retención. */
    @Scheduled(cron = "${app.porteria.retencion-cron:0 30 3 * * *}", zone = "${app.zona-horaria:America/Bogota}")
    public void borrarFotosViejas() {
        borrarFotos(LocalDateTime.now(zona));
    }

    /** Borra las fotos anteriores a [ahora] menos los días de retención; devuelve cuántas. */
    public int borrarFotos(LocalDateTime ahora) {
        if (diasFotos <= 0) return 0;
        LocalDateTime limite = ahora.minusDays(diasFotos);
        int total = 0;
        total += borrarLotes(() -> visitas.conFotoAntesDe(limite, PageRequest.of(0, LOTE)),
                VisitaPorteria::getFotoRuta, v -> v.setFotoRuta(null), visitas::saveAll);
        total += borrarLotes(() -> correspondencia.conFotoAntesDe(limite, PageRequest.of(0, LOTE)),
                Correspondencia::getFotoRuta, c -> c.setFotoRuta(null), correspondencia::saveAll);
        total += borrarLotes(() -> correspondencia.conFotoRecogeAntesDe(limite, PageRequest.of(0, LOTE)),
                Correspondencia::getSolicitudFotoRuta, c -> c.setSolicitudFotoRuta(null), correspondencia::saveAll);
        if (total > 0) log.info("Retención de fotos: se borraron {} fotos de antes de {}", total, limite.toLocalDate());
        return total;
    }

    /**
     * Borra el archivo de cada registro y deja la ruta en NULL, por lotes, como raíz (las rutas ya
     * llevan su conjunto). Si un archivo no se pudo borrar se deja para la próxima vez y se para
     * (para no repetir el mismo lote sin fin).
     */
    private <T> int borrarLotes(java.util.function.Supplier<List<T>> buscar, Function<T, String> ruta,
                                Consumer<T> limpiar, Consumer<List<T>> guardar) {
        int total = 0;
        while (true) {
            // [encontradas, borradas]
            int[] r = ConjuntoContexto.comoRaiz(() -> tx.execute(s -> {
                List<T> lote = buscar.get();
                List<T> listas = new java.util.ArrayList<>();
                for (T e : lote) {
                    try {
                        almacen.borrar(ruta.apply(e));
                        limpiar.accept(e);
                        listas.add(e);
                    } catch (RuntimeException ex) {
                        log.warn("No se pudo borrar la foto {}: {}", ruta.apply(e), ex.getMessage());
                    }
                }
                guardar.accept(listas);
                return new int[]{lote.size(), listas.size()};
            }));
            if (r == null) return total;
            total += r[1];
            if (r[0] < LOTE || r[1] < r[0]) return total;
        }
    }

    private boolean enSilencio(int hora) {
        if (horaSilencioDesde == horaSilencioHasta) return false;
        return horaSilencioDesde > horaSilencioHasta
                ? hora >= horaSilencioDesde || hora < horaSilencioHasta
                : hora >= horaSilencioDesde && hora < horaSilencioHasta;
    }
}
