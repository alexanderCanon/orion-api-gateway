-- Fase 2 (C4): Protección contra fuerza bruta — lockout por cuenta.
-- Las columnas viven en users para que el estado de lockout sobreviva
-- reinicios y se comparta entre réplicas del servicio.
ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMPTZ;
