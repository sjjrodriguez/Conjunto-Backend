-- P6 bloque 4: credencial opaca por celular para la biometría, textos legales con consentimientos,
-- solicitudes de datos personales (Ley 1581) y la app que registró cada celular de avisos.
-- Incremental e idempotente.

-- Qué app registró el celular: ADMIN o RESIDENTE (null = versión anterior de la app)
ALTER TABLE dispositivo ADD COLUMN IF NOT EXISTS tipo_app       VARCHAR(20);
ALTER TABLE dispositivo ADD COLUMN IF NOT EXISTS application_id VARCHAR(100);

-- La huella nunca guarda la contraseña: guarda esta credencial aleatoria (aquí solo su SHA-256)
CREATE TABLE IF NOT EXISTS credenciales_dispositivo (
    id             BIGSERIAL PRIMARY KEY,
    sujeto         VARCHAR(60)  NOT NULL,
    dispositivo_id VARCHAR(64)  NOT NULL,
    tipo_app       VARCHAR(20)  NOT NULL,
    hash_credencial VARCHAR(64) NOT NULL,
    creada_en      TIMESTAMP    NOT NULL,
    ultimo_uso     TIMESTAMP,
    CONSTRAINT uk_credencial_dispositivo UNIQUE (sujeto, dispositivo_id, tipo_app)
);
CREATE INDEX IF NOT EXISTS idx_credencial_hash ON credenciales_dispositivo (hash_credencial);

CREATE TABLE IF NOT EXISTS textos_legales (
    id            BIGSERIAL PRIMARY KEY,
    tipo          VARCHAR(30) NOT NULL,
    version       INTEGER     NOT NULL,
    titulo        VARCHAR(150) NOT NULL,
    contenido     TEXT        NOT NULL,
    vigente_desde TIMESTAMP   NOT NULL,
    CONSTRAINT uk_texto_legal UNIQUE (tipo, version),
    CONSTRAINT ck_texto_legal_tipo CHECK (tipo IN ('POLITICA_PRIVACIDAD', 'TERMINOS'))
);

CREATE TABLE IF NOT EXISTS consentimientos (
    id          BIGSERIAL PRIMARY KEY,
    sujeto      VARCHAR(60) NOT NULL,
    texto_id    BIGINT      NOT NULL,
    aceptado_en TIMESTAMP   NOT NULL,
    ip          VARCHAR(64),
    CONSTRAINT uk_consentimiento UNIQUE (sujeto, texto_id),
    CONSTRAINT fk_consentimiento_texto FOREIGN KEY (texto_id) REFERENCES textos_legales (id)
);

CREATE TABLE IF NOT EXISTS solicitudes_datos (
    id            BIGSERIAL PRIMARY KEY,
    sujeto        VARCHAR(60)  NOT NULL,
    nombre        VARCHAR(255),
    tipo          VARCHAR(20)  NOT NULL,
    estado        VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    detalle       TEXT,
    respuesta     TEXT,
    creada_en     TIMESTAMP    NOT NULL,
    resuelta_en   TIMESTAMP,
    resuelta_por  VARCHAR(60),
    CONSTRAINT ck_solicitud_datos_tipo CHECK (tipo IN ('CONSULTA', 'SUPRESION')),
    CONSTRAINT ck_solicitud_datos_estado CHECK (estado IN ('PENDIENTE', 'ATENDIDA', 'RECHAZADA'))
);
CREATE INDEX IF NOT EXISTS idx_solicitud_datos_estado ON solicitudes_datos (estado, creada_en);

INSERT INTO textos_legales (tipo, version, titulo, contenido, vigente_desde)
SELECT 'POLITICA_PRIVACIDAD', 1, 'Política de tratamiento de datos personales',
       'MiConjunto trata tus datos personales (nombre, documento, teléfono, apartamento, vehículos y pagos) solo para administrar la copropiedad: comunicados, cartera, reservas, obras y seguridad. No los vendemos ni los compartimos con terceros ajenos a la administración, salvo el proveedor de pagos cuando decides pagar en línea. Conforme a la Ley 1581 de 2012 puedes conocer, actualizar, rectificar y solicitar la supresión de tus datos desde Cuenta → Mis datos personales.',
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM textos_legales WHERE tipo = 'POLITICA_PRIVACIDAD' AND version = 1);

INSERT INTO textos_legales (tipo, version, titulo, contenido, vigente_desde)
SELECT 'TERMINOS', 1, 'Términos y condiciones de uso',
       'Al usar MiConjunto aceptas usar la aplicación solo para asuntos de tu apartamento y de la copropiedad, mantener en reserva tu contraseña y respetar el reglamento de propiedad horizontal. La administración puede suspender cuentas que hagan un uso indebido. La información publicada por la administración es de carácter oficial para los residentes.',
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM textos_legales WHERE tipo = 'TERMINOS' AND version = 1);

-- Reverso (manual):
-- DROP TABLE IF EXISTS solicitudes_datos;
-- DROP TABLE IF EXISTS consentimientos;
-- DROP TABLE IF EXISTS textos_legales;
-- DROP TABLE IF EXISTS credenciales_dispositivo;
-- ALTER TABLE dispositivo DROP COLUMN IF EXISTS application_id, DROP COLUMN IF EXISTS tipo_app;
