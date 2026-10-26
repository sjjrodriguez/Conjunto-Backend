package com.residencial.almacen;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dónde se guardan los comprobantes de pago. En producción, Supabase Storage (bucket privado);
 * sin configurarlo (demo, pruebas), una carpeta local. Los archivos nunca son públicos: se
 * ven con un enlace firmado que vence.
 */
public interface AlmacenArchivos {

    void guardar(String ruta, byte[] datos, String tipo);

    /** Enlace para descargar [ruta] durante [vigencia]. */
    String enlace(String ruta, Duration vigencia);

    void borrar(String ruta);

    /** Enlaces de varias rutas (ruta → enlace). Supabase los firma en una sola petición. */
    default Map<String, String> enlaces(Collection<String> rutas, Duration vigencia) {
        Map<String, String> r = new LinkedHashMap<>();
        for (String ruta : rutas) r.put(ruta, enlace(ruta, vigencia));
        return r;
    }
}
