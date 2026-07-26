# Entregable 2 — Portal del Cliente + Motor de Reservas (API)

Documentación del backend del Entregable 2 del Hotel Manuel Antonio Park.
Cubre las historias **HU-008** (disponibilidad), **HU-009** a **HU-013** (reservas),
**HU-014** (perfil), **HU-015** (cambio de contraseña), **HU-035** y **HU-036**
(correos de confirmación y cancelación).

> Complementa a [AUTH.md](E1-AUTH.md) (Entregable 1). Contrato de referencia:
> [api-contrato.md](../api-contrato.md).

---

## 1. Lo que se implementó

### Decisiones de diseño

| Decisión | Elección | Razón |
|---|---|---|
| Estado inicial de reserva | `PENDIENTE` | La confirmación llega como flujo posterior (E3). PENDIENTE y CONFIRMADA cuentan como *activas* para disponibilidad. |
| Imágenes de habitaciones | Rutas **relativas** en BD (`hmap/rooms/<slug>/<nombre>`) + base del CDN en `IMAGES_BASE_URL` | La BD no conoce la cuenta de Cloudinary; cambiar de cuenta/CDN es cambiar una variable de entorno. `ImageUrlResolver` antepone la base al servir los DTOs. |
| Ventana de edición/cancelación | **Configurable**: `app.reservations.edit-window-hours` (default **48 h** antes del check-in) | Requisito del negocio; se cambia sin tocar código. |
| `code` de reserva (`RSV-000123`) | Derivado del id en el DTO, **no persistido** | Puramente presentacional; cero riesgo de desincronización. |
| `total` | **Persistido** (snapshot del precio al reservar) | Si el precio de la habitación cambia luego, la reserva conserva su total. |
| `nights` | Derivado (`check_out - check_in`), no persistido | Evita estado redundante. |
| Concurrencia (doble reserva) | Bloqueo pesimista sobre la habitación (`SELECT ... FOR UPDATE`) + re-chequeo de solape en la transacción | Serializa reservas por habitación en InnoDB; contención mínima. |
| Estados como `VARCHAR` (+ `@JdbcTypeCode(SqlTypes.VARCHAR)`) | En vez del tipo `ENUM` nativo de MySQL | E3 agrega `CHECK_IN`/`CHECK_OUT` sin migración de esquema. |
| Colecciones de Room | 4 tablas hijas (`@ElementCollection` con columna `position`) | Orden estable de listas; compatible con `ddl-auto=validate` y con el CRUD de habitaciones del E3. |
| `403` vs `401` | `ForbiddenException` → 403 para "no es tu recurso" | El frontend cierra la sesión ante cualquier 401; 403 no la cierra. |

### Endpoints

#### Habitaciones (`/rooms`) — lectura pública, sin auth

| Método | Ruta | Descripción | Respuesta |
|--------|------|-------------|-----------|
| GET | `/rooms` | Catálogo completo | `200` + `Room[]` |
| GET | `/rooms/{id}` | Detalle de una habitación | `200` + `Room` · `404` |
| GET | `/rooms/availability?check_in=YYYY-MM-DD&check_out=YYYY-MM-DD&guests=N` | Libres en el rango con capacidad ≥ `guests` | `200` + `Room[]` · `400` |

Reglas de disponibilidad:
- Solape: bloquea una reserva activa con `check_in < :check_out && check_out > :check_in`.
- Habitaciones en `MANTENIMIENTO` se excluyen.
- Validaciones: `check_in >= hoy`, `check_out > check_in`, `guests >= 1` → `400` con mensaje en español.
- Resultado ordenado por precio ascendente.

#### Reservas (`/reservations`) — requieren JWT

| Método | Ruta | Descripción | Respuesta |
|--------|------|-------------|-----------|
| POST | `/reservations` | Crear reserva (nace `PENDIENTE`) | `201` + `Reservation` · `400` / `404` / `409` |
| GET | `/reservations/me` | Mis reservas, más recientes primero | `200` + `Reservation[]` |
| GET | `/reservations/{id}` | Detalle (solo propia) | `200` · `403` si es ajena · `404` |
| PUT | `/reservations/{id}` | Editar fechas/huéspedes (si `can_edit`) | `200` · `403` / `409` |
| POST | `/reservations/{id}/cancel` | Cancelar (si `can_cancel`) | `200` + `Reservation` · `403` / `409` |

Cuerpos:

```jsonc
// POST /reservations
{ "room_id": 1, "check_in": "2026-08-01", "check_out": "2026-08-04", "guests": 2 }

// PUT /reservations/{id}
{ "check_in": "2026-08-10", "check_out": "2026-08-12", "guests": 2 }
```

Reglas de negocio (viven en la API; el FE solo refleja los flags):
- **Crear:** re-valida disponibilidad bajo bloqueo → `409` si el rango se ocupó en una carrera. Valida capacidad (`400`) y estado de la habitación (`409` si `MANTENIMIENTO`). `total = noches × price_per_night`.
- **Flags:** `can_edit = can_cancel = estado activo && ahora < check_in − 48 h` (ventana configurable).
- **Editar:** solo dentro de la ventana (`409` si no); re-valida solape **excluyendo la propia reserva**; recalcula el total con el precio vigente.
- **Cancelar:** solo dentro de la ventana; estado → `CANCELADA`.

#### Perfil

| Método | Ruta | Descripción | Respuesta |
|--------|------|-------------|-----------|
| PUT | `/users/me` | Actualizar `name`, `last_name`, `phone` | `200` + `User` |
| POST | `/auth/change-password` | `{ current_password, new_password }` | `204` · `400` si la actual no coincide |

- El email no se edita (identidad de la cuenta).
- `phone` es opcional (`null` permitido, máx. 30 caracteres) y ahora viaja en `GET /auth/me`.

### Estados

```
RoomStatus:        DISPONIBLE | OCUPADA | MANTENIMIENTO
ReservationStatus: PENDIENTE | CONFIRMADA | CANCELADA     (E3 sumará CHECK_IN | CHECK_OUT)
```

`PENDIENTE` y `CONFIRMADA` son *activas*: bloquean disponibilidad y admiten edición/cancelación.

### Correos (HU-035, HU-036)

- **Confirmación** al crear la reserva: código, habitación, fechas, huéspedes, noches y total.
- **Cancelación** al cancelarla: código, habitación y fechas.
- Texto plano en español vía SMTP (Mailtrap en desarrollo), patrón de `sendPasswordResetEmail`.
- **Best-effort:** un fallo del SMTP se registra en el log pero no rompe la operación.

### Manejo de errores (nuevo en E2)

| Excepción | HTTP | Uso |
|---|---|---|
| `ConflictException` | `409` | Solape de fechas, habitación en mantenimiento, fuera de ventana |
| `ForbiddenException` | `403` | Reserva de otro usuario (no cierra la sesión del FE) |
| `MethodArgumentTypeMismatch` / `MissingServletRequestParameter` | `400` | Query params malformados en `/rooms/availability` |

### Base de datos (migración `V3__rooms_reservations_and_phone.sql`)

- `users`: columna `phone VARCHAR(30) NULL`.
- `rooms` + 4 tablas de colección (`room_images`, `room_amenities`, `room_bathroom`, `room_views`) con PK `(room_id, position)` y `ON DELETE CASCADE`.
- `reservations` con FKs a `users`/`rooms`, `CHECK (check_out > check_in)` e índices:
  - `ix_reservations_room_dates (room_id, status, check_in, check_out)` — consulta de solape.
  - `ix_reservations_user (user_id, created_at)` — listado "mis reservas".
- **Seed** de las 3 habitaciones migradas del frontend (`rooms.ts`): `cuadruple-estandar` ($180, cap. 6), `deluxe-cama-grande` ($240, cap. 2), `cuadruple-deluxe` ($150, cap. 4), con sus comodidades, baño, vistas e imágenes (placeholders).

### Estructura de archivos nueva

```
src/main/java/com/hmap/backend/
├── room/
│   ├── controller/RoomController.java
│   ├── dto/RoomDTO.java
│   ├── entity/Room.java
│   ├── enums/RoomStatus.java
│   ├── repository/RoomRepository.java        // findWithLockById + findAvailable
│   └── service/RoomService.java
│   └── support/ImageUrlResolver.java         // base CDN + ruta relativa → URL absoluta
├── reservation/
│   ├── controller/ReservationController.java
│   ├── dto/{CreateReservationRequest, UpdateReservationRequest, ReservationDTO}.java
│   ├── entity/Reservation.java
│   ├── enums/ReservationStatus.java
│   ├── repository/ReservationRepository.java // existsOverlapping(excludeId)
│   ├── service/ReservationService.java       // reglas de negocio del motor
│   └── support/StayDates.java                // validación de fechas compartida
├── user/
│   ├── controller/UserController.java        // PUT /users/me
│   ├── dto/UpdateProfileRequest.java
│   └── service/UserService.java
├── auth/dto/ChangePasswordRequest.java       // + changePassword en AuthService
└── exception/{ConflictException, ForbiddenException}.java

src/main/resources/db/migration/V3__rooms_reservations_and_phone.sql
```

Modificados: `Auth` (+`phone`), `UserDTO` (+`phone`), `AuthController` (+`/auth/change-password`),
`MailService` (+2 correos), `GlobalExceptionHandler` (+403/409/400 params),
`SecurityConfig` (`GET /rooms/**` público), `application.properties`.

---

## 2. Configuración

### Variables de entorno nuevas

| Variable | Por defecto | Uso |
|----------|-------------|-----|
| `RESERVATION_EDIT_WINDOW_HOURS` | `48` | Horas antes del check-in hasta las que se puede editar/cancelar |
| `IMAGES_BASE_URL` | *(vacío)* | Base pública del CDN de imágenes. Ej: `https://res.cloudinary.com/tu_cloud/image/upload`. Vacío → la API devuelve las rutas relativas tal cual. |

(Las del E1 —JWT, SMTP, etc.— siguen igual; ver [AUTH.md](E1-AUTH.md).)

### Imágenes en Cloudinary

La BD guarda **rutas relativas** (`hmap/rooms/<slug>/<nombre>`, sin extensión) que
coinciden con los *public IDs* de la Media Library de Cloudinary:

```
hmap/rooms/
├── cuadruple-estandar/   outside · bed · bathroom · outside-1
├── deluxe-cama-grande/   bed · bedroom · bed-1 · bathroom · bathroom-1 · outside · outside-1
└── cuadruple-deluxe/     bed · bed-1 · decoration · bathroom · bathroom-1 · outside
```

`ImageUrlResolver` antepone `IMAGES_BASE_URL` al servir cualquier DTO con imágenes.
Cloudinary resuelve el formato sin extensión (los assets están en WebP), y el FE
puede insertar transformaciones (`w_800,q_auto,f_auto`) entre `upload/` y la ruta.

---

## 3. Cómo probar

1. `./mvnw spring-boot:run`
2. Swagger: `http://localhost:8080/swagger-ui/index.html`
3. Flujo sugerido:
   - `GET /rooms` (sin token) → 3 habitaciones en snake_case.
   - `GET /rooms/availability?check_in=<hoy+7>&check_out=<hoy+10>&guests=2` → las 3;
     con `guests=5` → solo la cuádruple estándar; con fechas inválidas → `400` en español.
   - Login → *Authorize* → `POST /reservations` → `201`, estado `PENDIENTE`,
     `code` `RSV-000001`, `total` = noches × precio. Correo de confirmación en **Mailtrap**.
   - Repetir la misma reserva (mismo rango/habitación) → `409`.
   - `GET /reservations/me` → lista con flags; `GET /reservations/{id}` con **otro** usuario → `403`.
   - `PUT /reservations/{id}` con fechas nuevas → recalcula `total`.
   - `POST /reservations/{id}/cancel` → `CANCELADA` + correo de cancelación.
   - Reserva con `check_in` a menos de 48 h → `can_edit`/`can_cancel` en `false`
     y `409` al intentar editar/cancelar.
   - `PUT /users/me` con `phone` → `User` actualizado; `POST /auth/change-password` → `204`
     y el login con la nueva clave funciona.

Tests automatizados (40 en total, todos en verde):

```bash
./mvnw test
```

| Suite | Tests | Cubre |
|---|---|---|
| `ReservationServiceTest` | 19 | Creación, solape/carrera, capacidad, mantenimiento, 403, flags ±48 h, edición, cancelación, correos (incl. fallo SMTP) |
| `AuthServiceTest` | 11 | E1 + cambio de contraseña (OK / actual incorrecta) |
| `RoomServiceTest` | 6 | Mapeo DTO, 404, validaciones de disponibilidad |
| `UserServiceTest` | 3 | Actualización de perfil, phone nulo, 404 |
| `BackendApplicationTests` | 1 | Arranque del contexto + validación de esquema |

---

## 4. Pendientes / fuera de alcance actual

- **Frontend del E2** (panel cliente, buscador, flujo de reserva): siguiente paso de la fase.
- **Transición `PENDIENTE` → `CONFIRMADA`:** llega con el panel del recepcionista (E3).
- **Correo de reserva manual** (HU-037): E3.
- El envío de correo es síncrono dentro de la operación (best-effort). Si se quisiera
  estricta separación transaccional: `ApplicationEvent` + `@TransactionalEventListener(AFTER_COMMIT)`.
- `GET /rooms/{slug}`: el FE hoy navega por slug en el portal público; si lo necesita
  contra la API, agregar la ruta (el repo ya tiene `findBySlug`).
