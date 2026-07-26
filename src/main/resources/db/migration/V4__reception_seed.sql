-- =====================================================================
-- V4: Usuario RECEPCIONISTA de prueba (Entregable 3)
--     Los estados de reserva CHECK_IN/CHECK_OUT no requieren migración:
--     la columna reservations.status es VARCHAR.
-- =====================================================================

-- Recepcionista de prueba para operar el panel interno.
-- Contraseña: recepcion123  (hash BCrypt, cost 10). Cambiar en producción.
INSERT INTO users (name, last_name, email, phone, password, role_id, active)
SELECT 'Recepción', 'HMAP', 'recepcion@hmap.com', NULL,
       '$2b$10$qe8L4uXDxvVaUOjMPvS/Le6m7hmCWXOD2VAc3YJwkU2DNY81hPxxW',
       r.id, TRUE
FROM roles r
WHERE r.name = 'RECEPCIONISTA';
