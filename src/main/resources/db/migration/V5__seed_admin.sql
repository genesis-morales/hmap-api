-- =====================================================================
-- V5: Usuario ADMINISTRADOR inicial (Entregable 4)
--     El rol ADMINISTRADOR se sembró en V1 y se normalizó en V2.
-- =====================================================================

-- Administrador inicial del sistema.
-- Contraseña: admin123  (hash BCrypt, cost 10). CAMBIAR en producción.
INSERT INTO users (name, last_name, email, phone, password, role_id, active)
SELECT 'Administrador', 'HMAP', 'admin@hmap.com', NULL,
       '$2a$10$h7dsHBuxN86nx4wcwwFOWOZBKqB/KkcIgBLpIEaBtssjy/HQDUvDm',
       r.id, TRUE
FROM roles r
WHERE r.name = 'ADMINISTRADOR';
