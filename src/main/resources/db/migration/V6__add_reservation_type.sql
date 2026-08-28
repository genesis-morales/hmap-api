-- Agrega el tipo de reserva (ONLINE vs MANUAL) para distinguir el origen en el panel.

ALTER TABLE reservations
    ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'ONLINE';

-- Las reservas existentes se marcan como ONLINE (fueron creadas por clientes desde
-- el portal público). Las nuevas reservas manuales (HU-020) se marcarán como MANUAL.
