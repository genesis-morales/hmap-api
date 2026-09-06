-- V7: número de habitación (segunda llave natural de rooms) y motivo de
-- desactivación de una cuenta. Ver docs/CAMBIOS-MODELO-V7.md.

-- 1. rooms.room_number
--    Se agrega en tres pasos porque es NOT NULL sobre una tabla con datos:
--    primero nullable, luego se numeran las filas existentes, y al final se
--    aplican NOT NULL y UNIQUE.

ALTER TABLE rooms
    ADD COLUMN room_number VARCHAR(10) NULL;

UPDATE rooms SET room_number = '101' WHERE slug = 'cuadruple-estandar';
UPDATE rooms SET room_number = '102' WHERE slug = 'deluxe-cama-grande';
UPDATE rooms SET room_number = '103' WHERE slug = 'cuadruple-deluxe';

ALTER TABLE rooms
    ALTER COLUMN room_number SET NOT NULL,
    ADD CONSTRAINT uk_rooms_room_number UNIQUE (room_number);

-- 2. users.deactivation_reason
--    Nulable por definición: solo tiene valor mientras la cuenta está
--    desactivada. Al reactivar se limpia a NULL.

ALTER TABLE users
    ADD COLUMN deactivation_reason VARCHAR(300) NULL;
