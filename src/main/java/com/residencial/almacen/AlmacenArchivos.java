package com.residencial.almacen;

import java.time.Duration;

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
}
