package com.residencial.almacen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.file.Path;

/** Supabase Storage si están SUPABASE_URL y SUPABASE_SERVICE_KEY; si no, una carpeta local. */
@Configuration
public class ConfiguracionAlmacen {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionAlmacen.class);

    @Bean
    @Primary
    public AlmacenArchivos almacenArchivos(@Value("${app.storage.supabase.url:}") String url,
                                           @Value("${app.storage.supabase.service-key:}") String clave,
                                           @Value("${app.storage.bucket:comprobantes}") String bucket,
                                           @Value("${app.storage.local.directorio:}") String directorio) {
        if (!url.isBlank() && !clave.isBlank()) {
            log.info("Comprobantes en Supabase Storage (bucket {})", bucket);
            return new AlmacenSupabase(url, clave, bucket);
        }
        Path carpeta = directorio.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "miconjunto-comprobantes")
                : Path.of(directorio);
        log.warn("Comprobantes en la carpeta local {} (sin SUPABASE_URL/SUPABASE_SERVICE_KEY)", carpeta);
        return new AlmacenLocal(carpeta);
    }

    /**
     * Imágenes y archivos del feed (P5): bucket privado "publicaciones". En local es la misma
     * carpeta (las rutas empiezan por publicaciones/) para que los enlaces firmados sirvan igual.
     */
    @Bean
    public AlmacenArchivos almacenPublicaciones(AlmacenArchivos almacenArchivos,
                                                @Value("${app.storage.supabase.url:}") String url,
                                                @Value("${app.storage.supabase.service-key:}") String clave,
                                                @Value("${app.storage.bucket-publicaciones:publicaciones}") String bucket) {
        if (!url.isBlank() && !clave.isBlank()) return new AlmacenSupabase(url, clave, bucket);
        return almacenArchivos;
    }
}
