# Entregable 3 — Panel de Recepcionista (API)

Documentación del backend del Entregable 3 del Hotel Manuel Antonio Park.
Cubre **HU-016 a HU-029** (dashboard, calendario, check-in/out, reservas internas
e inventario de habitaciones) y **HU-037** (correo de reserva manual).

> Complementa a [AUTH.md](./AUTH.md) (E1) y [E2-RESERVAS.md](./E2-RESERVAS.md) (E2).
> Plan de las fases 3 y 4: [E3-E4-PLAN.md](./E3-E4-PLAN.md). Contrato general:
> [api-contrato.md](./api-contrato.md).

---

## 1. Lo que se implementó

### Autorización por rol (RNF-001)

- Se habilitó **`@EnableMethodSecurity`**; las rutas internas se protegen con
  `@PreAuthorize("hasAnyRole('RECEPCIONISTA','ADMINISTRADOR')")`.
- Un acceso denegado responde **403** (`GlobalExceptionHandler`), no 401, para que
  el frontend **no cierre la sesión** del usuario.
- La comprobación de propiedad de reservas del cliente sigue en el servicio (403).

### Ciclo de vida de la reserva

```
PENDIENTE  → el cliente crea la reserva (aún no paga)
CONFIRMADA → recepción la marca al llegar y pagar el huésped   POST /reservations/{id}/confirm
CHECK_IN   → el huésped ingresa; la habitación pasa a OCUPADA   POST /reservations/{id}/check-in
CHECK_OUT  → el huésped se retira; la habitación → DISPONIBLE   POST /reservations/{id}/check-out
CANCELADA  → anulación (nunca elimina el registro)
```

- Las transiciones las dispara **recepción** (manual). Los cambios de estado de la
  **habitación** son automáticos (efecto del check-in/out).
- **Guardas de transición** (→ `409`): solo se confirma una `PENDIENTE`; solo se
  hace check-in de una `CONFIRMADA`; solo check-out de una `CHECK_IN`.
- Disponibilidad: `PENDIENTE`, `CONFIRMADA` y **`CHECK_IN`** bloquean la habitación;
  `CHECK_OUT` y `CANCELADA` no.

### Decisiones de diseño

| Decisión | Elección | Razón |
|---|---|---|
| Estados nuevos | `CHECK_IN`/`CHECK_OUT` como VARCHAR | Sin migración de esquema (previsto desde E2). |
| Autorización | `@PreAuthorize` (method security) + reglas gruesas en `SecurityConfig` | Regla junto al endpoint, legible; rutas mixtas cliente/interno sin patrones frágiles. |
| Editar/cancelar (misma ruta) | El servicio decide por rol del actor | Cliente sujeto a ventana de 48 h; recepción sin ventana. |
| Cancelación interna | `reason` obligatorio (`400` si falta) | Trazabilidad (HU-022); el cliente no requiere motivo. |
| Reserva manual sin cuenta | Se crea cuenta `CLIENTE` con contraseña temporal | El huésped puede gestionar la reserva en el portal (HU-037). |
| Estado inicial de la manual | `PENDIENTE` | Consistente con el flujo; recepción la confirma al pagar. |
| Huésped en la reserva | Campo `guest` anidado en `Reservation` | La tabla de recepción muestra a quién pertenece cada reserva. |
| Paginación | `PageResponse` en `snake_case` | Desacopla el contrato del `Page` de Spring. |

---

## 2. Endpoints

Convenciones del E1/E2: `snake_case`, `Authorization: Bearer <JWT>`, fechas `YYYY-MM-DD`,
errores `{ "message": string }` (vía `ProblemDetail.detail`).

### 2.1 Dashboard y operación del día — rol interno

| Método | Ruta | HU | Respuesta |
|--------|------|----|-----------|
| GET | `/dashboard/occupancy` | HU-016 | `{ occupied, available, maintenance, total }` |
| GET | `/reservations/today` | HU-018 | `{ check_ins: Reservation[], check_outs: Reservation[] }` |
| GET | `/reservations/calendar?from=YYYY-MM-DD&to=YYYY-MM-DD` | HU-017 | `Reservation[]` (solape con el rango) |

- `today.check_ins`: reservas con entrada hoy en estado `PENDIENTE`/`CONFIRMADA`.
- `today.check_outs`: reservas con salida hoy en estado `CHECK_IN`.
- `calendar`: `from`/`to` obligatorias; `to < from` → `400`.

### 2.2 Transiciones de estado — rol interno

| Método | Ruta | Efecto | Errores |
|--------|------|--------|---------|
| POST | `/reservations/{id}/confirm` | `PENDIENTE` → `CONFIRMADA` | `409` si no está pendiente |
| POST | `/reservations/{id}/check-in` | `CONFIRMADA` → `CHECK_IN`, habitación → `OCUPADA` | `409` si no está confirmada |
| POST | `/reservations/{id}/check-out` | `CHECK_IN` → `CHECK_OUT`, habitación → `DISPONIBLE` | `409` si no tiene check-in |

Todas devuelven `200` + `Reservation`.

### 2.3 Reservas internas — rol interno

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/reservations?search=&from=&to=&status=&page=0&size=20` | Tabla global paginada (HU-023/024) → `PageResponse<Reservation>` |
| POST | `/reservations/manual` | Reserva a nombre de un cliente (HU-020) → `201` + `Reservation` |

Filtros de la tabla global (todos opcionales):
- `search`: nombre, apellido o correo del huésped; si es numérico o `RSV-000123`, busca por id.
- `from`/`to`: acotan por fecha de entrada/salida. `status`: uno de los estados.
- `page` (0-based) y `size` (default 20).

```jsonc
// POST /reservations/manual — body
{
  "room_id": 1,
  "check_in": "2026-08-01",
  "check_out": "2026-08-04",
  "guests": 2,
  "guest": { "name": "Carlos", "last_name": "Ruiz", "email": "carlos@mail.com", "phone": "88880000" }
}
```

- Si el correo del huésped no existe, se crea una cuenta `CLIENTE` con contraseña
  temporal y se envía por correo (HU-037). Si ya existe, se asocia a esa cuenta.
- Reutiliza el motor de solape/bloqueo del E2 (`409` si el rango se ocupó,
  `400` si excede capacidad, `409` si la habitación está en `MANTENIMIENTO`).

### 2.4 Editar / cancelar (rutas compartidas cliente + interno)

| Método | Ruta | Cliente | Interno |
|--------|------|---------|---------|
| PUT | `/reservations/{id}` | Solo propia y dentro de la ventana (HU-012) | Cualquier reserva activa, sin ventana (HU-021) |
| POST | `/reservations/{id}/cancel` | Sin cuerpo, dentro de la ventana (HU-013) | `{ "reason": "..." }` obligatorio (HU-022) |

```jsonc
// POST /reservations/{id}/cancel — body (solo rol interno)
{ "reason": "El huésped no se presentó" }
```

### 2.5 Inventario de habitaciones — rol interno

| Método | Ruta | Descripción | Errores |
|--------|------|-------------|---------|
| POST | `/rooms` | Crear habitación (HU-025) → `201` + `Room` | `409` slug duplicado |
| PUT | `/rooms/{id}` | Editar (HU-026) → `200` + `Room` | `409` slug de otra; `404` |
| DELETE | `/rooms/{id}` | Eliminar (HU-027) → `204` | `409` si tiene reservas activas |
| PATCH | `/rooms/{id}/status` | Cambiar estado (HU-028) → `200` + `Room` | `400` estado inválido |

```jsonc
// POST/PUT /rooms — body
{
  "slug": "suite-familiar",
  "name": "Suite Familiar",
  "description": "…",
  "capacity": 5,
  "area": 30,
  "beds_label": "2 camas dobles + sofá cama",
  "price_per_night": 320.00,
  "smoking_policy": "No se puede fumar",
  "status": "DISPONIBLE",            // opcional; al crear por defecto DISPONIBLE
  "images": ["hmap/rooms/suite-familiar/bed"],
  "amenities": ["Aire acondicionado"],
  "bathroom": ["Ducha", "WC"],
  "views": ["Vistas al jardín"]
}

// PATCH /rooms/{id}/status — body
{ "status": "MANTENIMIENTO" }   // DISPONIBLE | OCUPADA | MANTENIMIENTO
```

---

## 3. Formas de datos nuevas / actualizadas

```ts
// Reservation ahora incluye el huésped titular (nuevo en E3)
Reservation = {
  id: number
  code: string                 // 'RSV-000123'
  guest: {                     // ← NUEVO: titular de la reserva
    id: number
    name: string
    last_name: string
    email: string
    phone?: string
  }
  room: Room
  check_in: string
  check_out: string
  guests: number
  nights: number
  total: number
  status: 'PENDIENTE' | 'CONFIRMADA' | 'CHECK_IN' | 'CHECK_OUT' | 'CANCELADA'
  can_edit: boolean
  can_cancel: boolean
  created_at: string
}

// Envoltorio de paginación (tabla global)
PageResponse<T> = {
  content: T[]
  page: number                 // 0-based
  size: number
  total_elements: number
  total_pages: number
}

// GET /dashboard/occupancy
Occupancy = { occupied: number, available: number, maintenance: number, total: number }
```

---

## 4. Correos

- **HU-037 (reserva manual):** detalles de la reserva; si la cuenta se creó, incluye
  usuario y contraseña temporal. Texto plano en español, best-effort (un fallo de
  SMTP se registra en el log pero no rompe la operación).
- Se mantienen los de confirmación (HU-035) y cancelación (HU-036) del E2; ahora se
  envían siempre al **huésped titular** de la reserva (relevante para reservas manuales).

---

## 5. Base de datos (migración `V4__reception_seed.sql`)

- No modifica tablas: los estados nuevos son VARCHAR.
- Siembra un usuario **RECEPCIONISTA** de prueba:
  - Correo: `recepcion@hmap.com`
  - Contraseña: `recepcion123` (hash BCrypt). **Cambiar en producción.**

---

## 6. Cómo probar (Swagger)

1. `./mvnw spring-boot:run` → `http://localhost:8080/swagger-ui/index.html`.
2. Login como recepcionista (`recepcion@hmap.com` / `recepcion123`) → *Authorize* con el token.
3. Flujo sugerido:
   - `GET /dashboard/occupancy` → conteo por estado.
   - Como cliente, crear una reserva (`POST /reservations`) → nace `PENDIENTE`.
   - Como recepción: `POST /reservations/{id}/confirm` → `CONFIRMADA`;
     `POST /reservations/{id}/check-in` → `CHECK_IN` y la habitación queda `OCUPADA`;
     `POST /reservations/{id}/check-out` → `CHECK_OUT` y la habitación `DISPONIBLE`.
   - Transiciones ilegales (ej. check-in de una `PENDIENTE`) → `409`.
   - `POST /reservations/manual` con un correo nuevo → `201` y correo con credenciales en Mailtrap.
   - `GET /reservations?search=carlos&status=PENDIENTE&page=0` → `PageResponse`.
   - `GET /reservations/today` y `GET /reservations/calendar?from=&to=`.
   - Inventario: `POST/PUT/PATCH/DELETE /rooms`; `DELETE` con reservas activas → `409`.
   - Como **cliente**, pegar el token de un cliente y llamar una ruta interna
     (ej. `GET /reservations`) → **403** (la sesión no se cierra).

Tests automatizados (unitarios, sin BD):

```bash
./mvnw test -Dtest=ReservationServiceTest,RoomServiceTest,UserServiceTest,AuthServiceTest,DashboardServiceTest
```

| Suite | Tests | Cubre (E3 añadido) |
|---|---|---|
| `ReservationServiceTest` | 32 | confirm/check-in/check-out y guardas, edición/cancelación interna, reserva manual, hoy/calendario/búsqueda |
| `RoomServiceTest` | 14 | crear/editar/eliminar (con reservas activas), cambio de estado |
| `DashboardServiceTest` | 2 | conteo de ocupación agrupado por estado |
| `AuthServiceTest` / `UserServiceTest` | 11 / 3 | E1/E2 |

Total: **62 tests unitarios en verde.**

> El test de contexto (`BackendApplicationTests`) requiere MySQL en ejecución.
> Tras los defaults de correo/JWT, el arranque ya no depende del `.env` (salvo la BD).

---

## 7. Pendientes / fuera de alcance

- **Frontend del panel de recepción**: siguiente paso de la fase.
- **Expiración automática de no-shows** (`@Scheduled`): fuera de alcance por decisión.
- **Gestión de usuarios internos** (HU-030 a HU-034): Entregable 4.
