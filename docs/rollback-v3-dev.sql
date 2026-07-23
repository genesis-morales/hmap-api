-- Revertir V3 para re-aplicarla con el seed corregido (solo desarrollo)
USE hmap_db;

DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS room_images;
DROP TABLE IF EXISTS room_amenities;
DROP TABLE IF EXISTS room_bathroom;
DROP TABLE IF EXISTS room_views;
DROP TABLE IF EXISTS rooms;
ALTER TABLE users DROP COLUMN phone;

DELETE FROM flyway_schema_history WHERE version = '3';
