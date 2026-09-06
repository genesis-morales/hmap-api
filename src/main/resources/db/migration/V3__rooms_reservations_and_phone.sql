-- =====================================================================
-- V3: Habitaciones, reservas y teléfono de usuario (Entregable 2)
--     HU-008 a HU-014
-- =====================================================================

-- 1. Teléfono de contacto del usuario (HU-014)
ALTER TABLE users
    ADD COLUMN phone VARCHAR(30) NULL;

-- 2. Habitaciones
CREATE TABLE rooms (
    id              BIGSERIAL     NOT NULL,
    slug            VARCHAR(80)   NOT NULL,
    name            VARCHAR(150)  NOT NULL,
    description     TEXT          NOT NULL,
    capacity        INT           NOT NULL,
    area            INT           NOT NULL,
    beds_label      VARCHAR(120)  NOT NULL,
    price_per_night DECIMAL(10,2) NOT NULL,
    smoking_policy  VARCHAR(120)  NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'DISPONIBLE',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_rooms PRIMARY KEY (id),
    CONSTRAINT uk_rooms_slug UNIQUE (slug)
);

-- 3. Colecciones de la habitación (con orden estable)
CREATE TABLE room_images (
    room_id  BIGINT       NOT NULL,
    position INT          NOT NULL,
    url      VARCHAR(500) NOT NULL,
    CONSTRAINT pk_room_images PRIMARY KEY (room_id, position),
    CONSTRAINT fk_room_images_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
);

CREATE TABLE room_amenities (
    room_id  BIGINT       NOT NULL,
    position INT          NOT NULL,
    label    VARCHAR(150) NOT NULL,
    CONSTRAINT pk_room_amenities PRIMARY KEY (room_id, position),
    CONSTRAINT fk_room_amenities_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
);

CREATE TABLE room_bathroom (
    room_id  BIGINT       NOT NULL,
    position INT          NOT NULL,
    label    VARCHAR(150) NOT NULL,
    CONSTRAINT pk_room_bathroom PRIMARY KEY (room_id, position),
    CONSTRAINT fk_room_bathroom_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
);

CREATE TABLE room_views (
    room_id  BIGINT       NOT NULL,
    position INT          NOT NULL,
    label    VARCHAR(150) NOT NULL,
    CONSTRAINT pk_room_views PRIMARY KEY (room_id, position),
    CONSTRAINT fk_room_views_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
);

-- 4. Reservas
CREATE TABLE reservations (
    id         BIGSERIAL     NOT NULL,
    user_id    BIGINT        NOT NULL,
    room_id    BIGINT        NOT NULL,
    check_in   DATE          NOT NULL,
    check_out  DATE          NOT NULL,
    guests     INT           NOT NULL,
    total      DECIMAL(10,2) NOT NULL,
    status     VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NULL,
    CONSTRAINT pk_reservations PRIMARY KEY (id),
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reservations_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT ck_reservations_dates CHECK (check_out > check_in)
);

CREATE INDEX ix_reservations_room_dates ON reservations (room_id, status, check_in, check_out);
CREATE INDEX ix_reservations_user ON reservations (user_id, created_at);

-- =====================================================================
-- 5. Seed del catálogo de habitaciones (migrado del frontend rooms.ts)
--    Las imágenes se guardan como rutas relativas de Cloudinary
--    (hmap/rooms/<slug>/<nombre>); la API antepone la base pública
--    definida en IMAGES_BASE_URL. La BD no conoce la cuenta.
-- =====================================================================

INSERT INTO rooms (slug, name, description, capacity, area, beds_label, price_per_night, smoking_policy, status) VALUES
(
    'cuadruple-estandar',
    'Habitación Cuádruple Estándar',
    'Un espacio amplio pensado para grupos y familias que quieren sentir la selva de cerca sin renunciar a la comodidad. Amanece con el canto de las aves a pocos metros del Parque Nacional Manuel Antonio.',
    6, 10, '2 camas dobles', 180.00, 'No se puede fumar', 'DISPONIBLE'
),
(
    'deluxe-cama-grande',
    'Habitación Deluxe con cama extragrande',
    'Una cama extragrande, bañera, vistas al jardín y a la piscina — todo en 20 m² diseñados para que dos personas descansen como se merecen después de explorar Manuel Antonio.',
    2, 20, '1 cama doble grande', 240.00, 'No se puede fumar', 'DISPONIBLE'
),
(
    'cuadruple-deluxe',
    'Habitación Cuádruple Deluxe',
    'Dos camas, balcón con vistas al jardín y a la piscina, y todo el confort que necesitas para llegar del parque y no querer salir. La habitación más completa del hotel, pensada para grupos de hasta cuatro.',
    4, 20, '2 camas dobles', 150.00, 'No se puede fumar', 'DISPONIBLE'
);

-- Imágenes (rutas relativas de Cloudinary: hmap/rooms/<slug>/<nombre>)
INSERT INTO room_images (room_id, position, url) VALUES
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 0, 'hmap/rooms/cuadruple-estandar/outside'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 1, 'hmap/rooms/cuadruple-estandar/bed'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 2, 'hmap/rooms/cuadruple-estandar/bathroom'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 3, 'hmap/rooms/cuadruple-estandar/outside-1'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 0, 'hmap/rooms/deluxe-cama-grande/bed'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 1, 'hmap/rooms/deluxe-cama-grande/bedroom'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 2, 'hmap/rooms/deluxe-cama-grande/bed-1'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 3, 'hmap/rooms/deluxe-cama-grande/bathroom'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 4, 'hmap/rooms/deluxe-cama-grande/bathroom-1'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 5, 'hmap/rooms/deluxe-cama-grande/outside'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 6, 'hmap/rooms/deluxe-cama-grande/outside-1'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 0, 'hmap/rooms/cuadruple-deluxe/bed'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 1, 'hmap/rooms/cuadruple-deluxe/bed-1'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 2, 'hmap/rooms/cuadruple-deluxe/decoration'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 3, 'hmap/rooms/cuadruple-deluxe/bathroom'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 4, 'hmap/rooms/cuadruple-deluxe/bathroom-1'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 5, 'hmap/rooms/cuadruple-deluxe/outside');

-- Comodidades
INSERT INTO room_amenities (room_id, position, label) VALUES
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 0, 'Aire acondicionado'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 1, 'Ropa de cama'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 2, 'Productos de limpieza'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 3, 'TV de pantalla plana'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 4, 'Nevera'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 5, 'Patio'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 6, 'Planta baja'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 0, 'Aire acondicionado'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 1, 'Ropa de cama'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 2, 'Productos de limpieza'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 3, 'TV de pantalla plana'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 4, 'Nevera'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 5, 'Camas extralargas (> 2m)'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 6, 'Planta baja'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 7, 'Accesible en silla de ruedas'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 0, 'Aire acondicionado'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 1, 'Ropa de cama'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 2, 'Productos de limpieza'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 3, 'TV de pantalla plana'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 4, 'Nevera'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 5, 'Camas extralargas (> 2m)'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 6, 'Planta baja'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 7, 'Accesible en silla de ruedas');

-- Baño
INSERT INTO room_bathroom (room_id, position, label) VALUES
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 0, 'Bañera'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 1, 'Ducha'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 2, 'WC'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 3, 'Toallas'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 4, 'Papel higiénico'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 0, 'Bañera'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 1, 'Artículos de aseo gratis'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 2, 'Ducha'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 3, 'WC'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 4, 'Toallas'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 5, 'Secador de pelo'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 6, 'Papel higiénico'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 0, 'Bañera'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 1, 'Artículos de aseo gratis'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 2, 'Ducha'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 3, 'WC'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 4, 'Toallas'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 5, 'Secador de pelo'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 6, 'Papel higiénico');

-- Vistas
INSERT INTO room_views (room_id, position, label) VALUES
((SELECT id FROM rooms WHERE slug = 'cuadruple-estandar'), 0, 'Vistas al jardín'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 0, 'Vistas al jardín'),
((SELECT id FROM rooms WHERE slug = 'deluxe-cama-grande'), 1, 'Vistas a la piscina'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 0, 'Vistas al jardín'),
((SELECT id FROM rooms WHERE slug = 'cuadruple-deluxe'), 1, 'Vistas a la piscina');
