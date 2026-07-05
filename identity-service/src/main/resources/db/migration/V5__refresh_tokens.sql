-- Refresh tokens opacos y rotativos (Fase 1 del plan de seguridad).
--
-- Diseño tipo GoTrue: el token opaco se guarda como SHA-256 (nunca en claro),
-- se rota en cada uso y se encadena vía parent_id para detectar reuso
-- (si un token ya rotado se presenta de nuevo, se revoca toda la cadena).
-- Revocable por usuario (logout, suspensión, cambio de rol, cambio de password).

CREATE TABLE refresh_tokens (
    token_id     UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users(user_id),
    token_hash   CHAR(64) NOT NULL UNIQUE,        -- SHA-256 hex (64 chars), nunca el token en claro
    parent_id    UUID REFERENCES refresh_tokens(token_id), -- cadena de rotación
    issued_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at   TIMESTAMP WITH TIME ZONE,
    user_agent   VARCHAR(512),
    ip_address   VARCHAR(45)
);

CREATE INDEX idx_refresh_tokens_user       ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash       ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
