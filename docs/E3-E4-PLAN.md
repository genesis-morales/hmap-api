# Plan de implementación — Entregables 3 y 4 (API)

Plan acordado para las dos últimas fases del backend del Hotel Manuel Antonio Park.
Complementa a [plan-entregable.md](plan-entregable.md) y al [contrato de API](api-contrato.md).
Los entregables ya cerrados se documentan en [AUTH.md](modulos/E1-AUTH.md) (E1) y
[E2-RESERVAS.md](modulos/E2-RESERVAS.md) (E2).

> Nota: el texto literal de los RF/RNF vive en el documento de tesis; aquí se
> referencian por el mapeo de `plan-entregable.md`.

---

## Principios transversales (E3 + E4)

### Autorización por rol (RNF-001) — enfoque híbrido

- `SecurityConfig` mantiene solo lo **grueso**: rutas públicas (`/auth/**`, Swagger,
  `GET /rooms/**`) y "todo lo demás requiere autenticación".
- Las reglas **finas por rol** se expresan con `@PreAuthorize` a nivel de método
  (habilitado con `@EnableMethodSecurity`), junto al endpoint:
  - `@PreAuthorize("hasAnyRole('RECEPCIONISTA','ADMINISTRADOR')")` → panel interno (E3).
  - `@PreAuthorize("hasRole('ADMINISTRADOR')")` → panel admin (E4).
- `hasRole('X')` busca la autoridad `ROLE_X`, que ya expone `Auth.getAuthorities()`
  como `"ROLE_" + role.getName()`. No hay que tocar el modelo de roles.
- La comprobación de **propiedad** (que una reserva sea del cliente que la pide)
  sigue en el servicio con `ForbiddenException`. `@PreAuthorize` valida *el rol*;
  el servicio valida *el dueño*.
- Un `AccessDeniedException` se traduce a **403** en `GlobalExceptionHandler`
  (coherente con el FE: 403 no cierra la sesión, 401 sí).

### Paginación (HU-023/024 y HU-034)

Record envoltorio reutilizable `common/dto/PageResponse<T>` en `snake_case`:
`{ content, page, size, total_elements, total_pages }`.

---

## Fase 3 — Panel Recepcionista

**HU-016 a HU-029, HU-037 · RF-011 a RF-014 (+ RF-016) · RNF foco: RNF-004.**
Todas las rutas internas requieren rol `RECEPCIONISTA` o `ADMINISTRADOR`.

### Ciclo de vida de la reserva (decisión de negocio)

```
PENDIENTE  → el cliente crea la reserva (aún no paga)
CONFIRMADA → el recepcionista la marca al llegar y pagar el huésped   (POST /reservations/{id}/confirm)
CHECK_IN   → el huésped ingresa; la habitación pasa a OCUPADA          (POST /reservations/{id}/check-in)
CHECK_OUT  → el huésped se retira; la habitación vuelve a DISPONIBLE   (POST /reservations/{id}/check-out)
CANCELADA  → anulación (cliente dentro de ventana, o recepción con motivo). Nunca borra el registro.
```

- Transiciones del ciclo: **manuales** (las dispara el recepcionista).
- Efectos secundarios: **automáticos** — el check-in/out cambia el estado de la
  habitación; el recepcionista no lo toca a mano (el `PATCH /rooms/{id}/status`
  queda para MANTENIMIENTO).
- **Guardas de transición**: se rechazan saltos ilegales (`409`): no se hace
  check-in de una `PENDIENTE`, ni check-out de algo que no esté en `CHECK_IN`.
- La expiración automática de no-shows (`@Scheduled`) queda **fuera de alcance**.

### Disponibilidad

- `CHECK_IN` se suma a los estados que **bloquean disponibilidad** (un huésped
  hospedado sigue ocupando la habitación). `CANCELADA` y `CHECK_OUT` no bloquean.
- Se distingue de `isActive()` (editable/cancelable por el cliente = `PENDIENTE`/`CONFIRMADA`).

### Endpoints

| Método | Ruta | HU | Rol |
|---|---|---|---|
| GET | `/panel-reception/occupancy` | HU-016 | interno |
| GET | `/reservations/today` | HU-018 | interno |
| GET | `/reservations/calendar?from=&to=` | HU-017 | interno |
| POST | `/reservations/{id}/confirm` | — | interno |
| POST | `/reservations/{id}/check-in` | HU-019 | interno |
| POST | `/reservations/{id}/check-out` | HU-019 | interno |
| GET | `/reservations?search=&from=&to=&status=&page=&size=` | HU-023/024 | interno |
| POST | `/reservations/manual` | HU-020 | interno |
| PUT | `/reservations/{id}` | HU-012/HU-021 | cliente (con ventana) o interno (sin ventana) |
| POST | `/reservations/{id}/cancel` | HU-013/HU-022 | cliente (sin motivo) o interno (`{reason}` obligatorio) |
| POST | `/rooms` | HU-025 | interno |
| PUT | `/rooms/{id}` | HU-026 | interno |
| DELETE | `/rooms/{id}` | HU-027 | interno (409 si tiene reservas activas) |
| PATCH | `/rooms/{id}/status` | HU-028 | interno |

### Reserva manual (HU-020 + HU-037)

- Body: datos de reserva + `guest { name, last_name, email, phone }`.
- Si el email del huésped **no existe**, se crea una cuenta `CLIENTE` con
  contraseña temporal aleatoria y se envía por correo (HU-037) junto con los
  detalles de la reserva. Si ya existe, se asocia la reserva a esa cuenta.
- Reutiliza el motor de solape/bloqueo del E2.

### Diseño de servicio (legibilidad)

`ReservationService` separa las operaciones del cliente (con propiedad + ventana)
de las internas (sin ventana, sobre cualquier reserva). `PUT`/`cancel` comparten
ruta pero el servicio decide el comportamiento según el rol del actor, sin `if`
frágiles: métodos internos dedicados y detección de rol interno con un helper.

### Correo

`MailService.sendManualReservationEmail(...)` (texto plano en español, best-effort).

### Migración

`V4__reception_seed.sql`: siembra un usuario `RECEPCIONISTA` de prueba. Los
estados nuevos no requieren migración (columna VARCHAR).

---

## Fase 4 — Panel Administrador + Cierre

**HU-030 a HU-034 · RF-015 · RNF foco: RNF-001 + verificación final.**
Todas las rutas requieren rol `ADMINISTRADOR`.

### Endpoints

| Método | Ruta | HU |
|---|---|---|
| GET | `/users?role=&active=&search=&page=&size=` | HU-034 |
| POST | `/users` | HU-030/032 |
| PUT | `/users/{id}` | HU-031/032 |
| PATCH | `/users/{id}/active` | HU-033 |

### Reglas

- Crear con rol asignado (RECEPCIONISTA/ADMINISTRADOR) y contraseña inicial.
- Un admin no puede **auto-desactivarse** ni quitarse su propio rol (evita quedarse
  sin acceso) → `409`/`400`.
- `active` ya está en la entidad `Auth` (e `isEnabled()` lo respeta: un usuario
  suspendido no puede loguear). Se expone `active` en el DTO admin.
- Email único al crear → `409`.

### Migración

`V5__seed_admin.sql`: siembra el usuario `ADMINISTRADOR` inicial (contraseña BCrypt,
documentada con recomendación de cambio).

### Cierre transversal

- Revisar `@PreAuthorize` en todos los endpoints internos.
- Checklist RNF: BCrypt (RNF-007 ✓), JWT (RNF-008 ✓), control de acceso (RNF-001,
  se cierra aquí), rendimiento de dashboard/listados (RNF-004), disponibilidad (RNF-005).
- Los RNF de FE (usabilidad, responsive, persistencia de selección) se anotan como
  responsabilidad del frontend.

---

## Orden de implementación

**E3** (rama `feat/recepcionist-v3`): fundación → estados/disponibilidad →
repositorio → servicio/controller de reservas internas → CRUD habitaciones →
correo → migración V4 → tests → `docs/E3-RECEPCION.md` → tag `v3-entregable`.

**E4** (rama `feat/admin-v4`): usuarios admin → seed V5 → revisión transversal +
checklist RNF → tests → `docs/E4-ADMIN.md` → tag `v4-entregable`.
