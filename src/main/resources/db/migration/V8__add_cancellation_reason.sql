-- V8: Añadir columna cancellation_reason a reservations
-- Permite almacenar el motivo cuando se cancela una reserva
-- (obligatorio para roles internos HU-022, opcional para clientes HU-013)

ALTER TABLE reservations
ADD COLUMN cancellation_reason TEXT NULL;
