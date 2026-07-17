# Contrato de API — Propuesta
**Proyecto HMAP** · Complemento de [plan-entregables.md](./plan-entregables.md)

Contrato acordado entre frontend y backend. Convenciones ya establecidas en el Entregable 1:

- Base URL: `env.apiUrl` (FE la adjunta vía axios en `src/shared/api/client.ts`).
- Auth: header `Authorization: Bearer <JWT>` en rutas protegidas.
- Errores: cuerpo `{ "message": string }` con el código HTTP correspondiente (el FE ya lo extrae con `getErrorMessage`).
- Nombres de campos en `snake_case` (consistente con `last_name` del E1).
- Fechas: `YYYY-MM-DD` (fechas de estancia) e ISO 8601 con hora para timestamps.

## Cómo trabaja el frontend (contexto para el backend)

Comportamientos ya implementados en el FE que la API debe tener en cuenta:

- **Token:** el JWT se guarda en `localStorage` y se adjunta automáticamente como
  `Authorization: Bearer <token>` en **todas** las peticiones (interceptor axios).
  No se usan cookies ni refresh tokens: un solo token de vida completa de sesión.
- **401:** ante cualquier respuesta `401`, el FE borra el token y considera la
  sesión cerrada. Por eso: usar `401` solo para token inválido/expirado;
  para "no tienes permiso sobre este recurso" usar `403` (no cierra la sesión).
- **Errores:** el FE muestra al usuario el campo `message` del cuerpo de error.
  Debe venir en español y ser apto para mostrarse tal cual
  (ej. `{ "message": "La habitación ya no está disponible en esas fechas." }`).
- **Timeout:** el FE corta peticiones a los **15 segundos**; los endpoints deben
  responder dentro de ese margen (relevante para disponibilidad y dashboard).
- **CORS:** en desarrollo el FE corre en Vite (`http://localhost:5173`);
  la API debe permitir ese origen con el header `Authorization`.
- **Sesión al cargar:** al abrir la app, si hay token el FE llama `GET /auth/me`
  para restaurar la sesión; ese endpoint debe ser rápido y devolver el `User` completo.
- **Roles:** el FE oculta rutas/vistas según `user.role`, pero eso es solo UX —
  la **autorización real es responsabilidad de la API** en cada endpoint (RNF-001).

---

## Entregable 1 — Existente ✅

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/register` | Registro → `{ token, user }` |
| POST | `/auth/login` | Login → `{ token, user }` |
| GET | `/auth/me` | Usuario del token vigente → `User` |
| POST | `/auth/forgot-password` | Envía enlace de recuperación |
| POST | `/auth/reset-password` | `{ token, password }` |

```ts
User = {
  id: string | number
  name: string
  last_name: string
  email: string
  role: 'CLIENTE' | 'RECEPCIONISTA' | 'ADMINISTRADOR'
  phone?: string        // ← NUEVO en E2 (HU-014: datos de contacto)
  active?: boolean      // ← NUEVO en E4 (HU-033)
}
```

---

## Entregable 2 — Portal Cliente + Reservas

### Perfil (HU-014, HU-015)

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| PUT | `/users/me` | `{ name, last_name, phone }` | `User` actualizado |
| POST | `/auth/change-password` | `{ current_password, new_password }` | `204` |

Notas:
- El email no se edita desde el perfil (identidad de la cuenta).
- `change-password` valida la contraseña actual; error `400` con `message` si no coincide.

### Habitaciones (lectura pública)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/rooms` | Catálogo completo (sin auth — lo consume también el portal público) |
| GET | `/rooms/:id` | Detalle de una habitación |
| GET | `/rooms/availability?check_in=YYYY-MM-DD&check_out=YYYY-MM-DD&guests=N` | Habitaciones libres en el rango con capacidad ≥ `guests` |

```ts
Room = {
  id: number
  slug: string              // p. ej. 'deluxe-cama-grande' (URLs amigables)
  name: string
  description: string
  capacity: number
  area: number              // m²
  beds_label: string        // '2 camas dobles'
  price_per_night: number   // USD
  images: string[]          // URLs
  amenities: string[]
  bathroom: string[]
  views: string[]
  smoking_policy: string
  status: 'DISPONIBLE' | 'OCUPADA' | 'MANTENIMIENTO'   // usado desde E3
}
```

> Migración: el catálogo hoy vive hardcodeado en el FE
> (`src/features/home/data/rooms.ts`). En E2 pasa a la BD y el FE lo consume
> de `/rooms`. Ese archivo sirve como datos semilla (seed) para la BD.

Notas de disponibilidad:
- Solapamiento: una habitación está ocupada si existe reserva activa con `check_in < :check_out && check_out > :check_in`.
- Habitaciones en `MANTENIMIENTO` se excluyen del resultado.
- Validaciones: `check_in >= hoy`, `check_out > check_in` → `400` si no.

### Reservas del cliente (HU-009 → HU-013)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/reservations` | Crear reserva del usuario autenticado |
| GET | `/reservations/me` | Mis reservas (más recientes primero) |
| GET | `/reservations/:id` | Detalle (solo propia; `403` si no) |
| PUT | `/reservations/:id` | Editar fechas/huéspedes (si `can_edit`) |
| POST | `/reservations/:id/cancel` | Cancelar (si `can_cancel`) |

```ts
// POST /reservations — body
{ room_id: number, check_in: string, check_out: string, guests: number }

Reservation = {
  id: number
  code: string              // identificador legible, p. ej. 'RSV-000123'
  room: Room                // anidada para no pedir aparte
  check_in: string
  check_out: string
  guests: number
  nights: number
  total: number             // nights * price_per_night, calculado por la API
  status: 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA'
        // E3 agrega: 'CHECK_IN' | 'CHECK_OUT'
  can_edit: boolean         // la API aplica la política de plazos (HU-012)
  can_cancel: boolean       // ídem (HU-013)
  created_at: string        // ISO 8601
}
```

Reglas de negocio (viven en la API; el FE solo refleja los flags):
- Al crear: re-validar disponibilidad (protección contra carrera) → `409` si el rango se ocupó.
- Política de plazos propuesta (a confirmar con el hotel): editar/cancelar permitido hasta **48 h antes** del `check_in` y solo en estado `PENDIENTE`/`CONFIRMADA`.
- Editar re-valida disponibilidad del nuevo rango.

### Correos (HU-035, HU-036)
- Confirmación de reserva al crear (`POST /reservations`).
- Notificación al cancelar (`POST /reservations/:id/cancel`).
- Disparados por la API; sin endpoint propio.

---

## Entregable 3 — Panel Recepcionista

Todas las rutas requieren rol `RECEPCIONISTA` o `ADMINISTRADOR`.

### Dashboard y operación (HU-016 → HU-019)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/dashboard/occupancy` | `{ occupied, available, maintenance, total }` |
| GET | `/reservations/today` | `{ check_ins: Reservation[], check_outs: Reservation[] }` |
| POST | `/reservations/:id/check-in` | Estado → `CHECK_IN`; habitación → `OCUPADA` |
| POST | `/reservations/:id/check-out` | Estado → `CHECK_OUT`; habitación → `DISPONIBLE` |

### Reservas internas (HU-020 → HU-024)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/reservations?search=&from=&to=&status=&page=` | Tabla global con filtros y paginación (HU-023/024) |
| POST | `/reservations/manual` | Reserva a nombre de un cliente (`{ ...datos reserva, guest: { name, last_name, email, phone } }`); dispara correo HU-037 |
| PUT | `/reservations/:id` | Misma ruta del E2; el rol interno no está sujeto a los plazos del cliente (HU-021) |
| POST | `/reservations/:id/cancel` | Ídem, con `{ reason }` obligatorio para el rol interno (HU-022) |

### Inventario de habitaciones (HU-025 → HU-029)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/rooms` | Crear habitación |
| PUT | `/rooms/:id` | Editar |
| DELETE | `/rooms/:id` | Solo sin reservas activas → `409` si tiene |
| PATCH | `/rooms/:id/status` | `{ status }` (HU-028) |

---

## Entregable 4 — Panel Administrador

Todas las rutas requieren rol `ADMINISTRADOR`.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/users?role=&active=&search=&page=` | Listado de usuarios (HU-034) |
| POST | `/users` | Crear usuario interno con rol (HU-030/032) |
| PUT | `/users/:id` | Editar datos y/o rol (HU-031/032) |
| PATCH | `/users/:id/active` | `{ active: boolean }` (HU-033) |

Cierre transversal: revisión de autorización por rol en **todos** los endpoints (RNF-001).

---

## Resumen por entregable (para el backend)

| Entregable | Endpoints nuevos | Núcleo |
|---|---|---|
| E2 | 10 | Modelo de habitaciones en BD + motor de disponibilidad/reservas + 2 correos |
| E3 | 10 | Estados operativos (check-in/out), reservas manuales, CRUD habitaciones, métricas |
| E4 | 4 | Gestión de usuarios y autorización por rol consolidada |
