-- Fase 3 (C2): Tokens de un solo uso para verificación de email y
-- recuperación de contraseña. Patrón GoTrue: tabla única con tipo de token.
-- El token se persiste como SHA-256 (nunca en claro).
CREATE TABLE one_time_tokens (
    token_id    UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(user_id),
    token_hash  VARCHAR(64) NOT NULL,               -- SHA-256, nunca en claro
    token_type  VARCHAR(32) NOT NULL,               -- 'EMAIL_VERIFICATION' | 'PASSWORD_RECOVERY'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    UNIQUE (user_id, token_type, token_hash)
);
CREATE INDEX idx_ott_hash ON one_time_tokens(token_hash, token_type);
CREATE INDEX idx_ott_user_type ON one_time_tokens(user_id, token_type);
