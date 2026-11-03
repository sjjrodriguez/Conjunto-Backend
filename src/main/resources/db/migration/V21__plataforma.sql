-- P7 bloque 3: operadores de la plataforma (crean, editan y suspenden conjuntos) y el registro de
-- lo que hace cada uno. No son de ningún conjunto. El primer operador lo crea el servidor al
-- arrancar con PLATAFORMA_USUARIO y PLATAFORMA_CONTRASENA. Idempotente.

CREATE TABLE IF NOT EXISTS operador_plataforma (
    id              BIGSERIAL PRIMARY KEY,
    usuario         VARCHAR(60)  NOT NULL,
    contrasena      VARCHAR(255) NOT NULL,
    nombre_completo VARCHAR(150),
    token_version   INTEGER      NOT NULL DEFAULT 0,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_operador_plataforma_usuario UNIQUE (usuario)
);

CREATE TABLE IF NOT EXISTS auditoria_plataforma (
    id               BIGSERIAL PRIMARY KEY,
    operador_id      BIGINT       NOT NULL,
    operador_usuario VARCHAR(60)  NOT NULL,
    accion           VARCHAR(30)  NOT NULL,
    conjunto_id      BIGINT,
    detalle          VARCHAR(500),
    fecha            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_auditoria_plataforma_conjunto ON auditoria_plataforma (conjunto_id);
