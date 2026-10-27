-- P5 bloque 3: feed de publicaciones de la administración (Inicio de las dos apps).
-- Los archivos van al bucket privado "publicaciones" de Supabase Storage; aquí solo su ruta.
CREATE TABLE publicacion (
    id          BIGSERIAL PRIMARY KEY,
    texto       TEXT         NOT NULL,
    autor_id    BIGINT,
    autor_nombre VARCHAR(255) NOT NULL,
    creada_en   TIMESTAMP    NOT NULL,
    editada_en  TIMESTAMP
);
CREATE INDEX idx_publicacion_creada ON publicacion (creada_en);

CREATE TABLE publicacion_adjunto (
    id             BIGSERIAL PRIMARY KEY,
    publicacion_id BIGINT       NOT NULL,
    tipo           VARCHAR(20)  NOT NULL,
    ruta           VARCHAR(500) NOT NULL,
    nombre         VARCHAR(255) NOT NULL,
    mime           VARCHAR(150) NOT NULL,
    tamano         BIGINT       NOT NULL,
    orden          INTEGER      NOT NULL,
    CONSTRAINT fk_adjunto_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion (id) ON DELETE CASCADE,
    CONSTRAINT ck_adjunto_tipo CHECK (tipo IN ('IMAGEN', 'ARCHIVO'))
);
CREATE INDEX idx_adjunto_publicacion ON publicacion_adjunto (publicacion_id);
