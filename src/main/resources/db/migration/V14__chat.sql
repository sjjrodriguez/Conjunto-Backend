-- P5 bloque 8: chat entre la administración y cada residente. Una conversación por apartamento
-- y por participante (el propietario y el arrendatario tienen la suya, por privacidad).

CREATE TABLE conversacion (
    id                  BIGSERIAL PRIMARY KEY,
    apartamento_id      BIGINT      NOT NULL REFERENCES apartamento(id) ON DELETE CASCADE,
    participante        VARCHAR(20) NOT NULL,
    ultimo_mensaje      TEXT,
    ultimo_mensaje_en   TIMESTAMP,
    no_leidos_admin     INTEGER     NOT NULL DEFAULT 0,
    no_leidos_residente INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uk_conversacion UNIQUE (apartamento_id, participante),
    CONSTRAINT ck_conversacion_participante CHECK (participante IN ('PROPIETARIO', 'ARRENDATARIO'))
);

CREATE TABLE mensaje_chat (
    id              BIGSERIAL PRIMARY KEY,
    conversacion_id BIGINT       NOT NULL REFERENCES conversacion(id) ON DELETE CASCADE,
    autor           VARCHAR(20)  NOT NULL,
    autor_nombre    VARCHAR(150) NOT NULL,
    texto           TEXT         NOT NULL,
    enviado_en      TIMESTAMP    NOT NULL,
    CONSTRAINT ck_mensaje_autor CHECK (autor IN ('ADMINISTRACION', 'RESIDENTE'))
);
CREATE INDEX idx_mensaje_conversacion ON mensaje_chat (conversacion_id, id);
