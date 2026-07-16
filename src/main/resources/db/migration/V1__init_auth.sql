-- =====================================================================
-- V1: Esquema inicial de Autenticación y Seguridad (HU-003 a HU-006)
-- =====================================================================

-- Roles del sistema
CREATE TABLE roles (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    name VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
);

INSERT INTO roles (name) VALUES
    ('ADMIN'),
    ('CLIENTE'),
    ('RECEPCIONISTA');

-- Usuarios / cuentas
CREATE TABLE users (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    name       VARCHAR(120)  NOT NULL,
    email      VARCHAR(150)  NOT NULL,
    password   VARCHAR(255)  NOT NULL,
    role_id    BIGINT        NOT NULL,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- Tokens de recuperación de contraseña
CREATE TABLE password_reset_tokens (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    token      VARCHAR(255)  NOT NULL,
    user_id    BIGINT        NOT NULL,
    expires_at DATETIME      NOT NULL,
    used       BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_token UNIQUE (token),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);
