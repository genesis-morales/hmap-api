-- =====================================================================
-- V2: Apellido de usuario y normalización del rol de administrador
-- =====================================================================

-- Columna de apellido en usuarios (requerida por GET /auth/me).
-- DEFAULT '' permite poblar filas existentes sin violar NOT NULL.
ALTER TABLE users
    ADD COLUMN last_name VARCHAR(120) NOT NULL DEFAULT '' AFTER name;

-- El frontend usa el literal "ADMINISTRADOR"; alineamos el rol sembrado en V1.
UPDATE roles SET name = 'ADMINISTRADOR' WHERE name = 'ADMIN';
