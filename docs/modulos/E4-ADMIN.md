# Entregable 4 — Panel de Administrador (API)

Gestión de cuentas de personal interno y consolidación del control de acceso por
rol. Cubre **HU-030 a HU-034 · RF-015 · RNF-001**.

Complementa a [AUTH.md](E1-AUTH.md) (E1), [E2-RESERVAS.md](E2-RESERVAS.md) (E2) y
[E3-RECEPCION.md](E3-RECEPCION.md) (E3). El plan general está en
[E3-E4-PLAN.md](../E3-E4-PLAN.md).

> **Estado:** API ✅ completada · Frontend ⏳ pendiente. Este documento es también la
> **guía de integración para el FE**: contrato, tipos TS, JSON de ejemplo por endpoint
> y catálogo de errores. Ver §4 a §8.

---

## 1. Alcance

El administrador gestiona las cuentas del personal interno (recepcionistas y
otros administradores): crearlas, editarlas, reasignar su rol y activarlas o
suspenderlas. Todas las rutas requieren rol **ADMINISTRADOR**.

| Método | Ruta | HU | Descripción |
|--------|------|----|-------------|
| GET | `/users?role=&active=&search=&page=&size=` | HU-034 | Nómina paginada con filtros |
| POST | `/users` | HU-030/032 | Crear cuenta interna con rol y contraseña inicial |
| PUT | `/users/{id}` | HU-031/032 | Editar datos y reasignar rol |
| PATCH | `/users/{id}/active` | HU-033 | Activar o suspender la cuenta |

> `PUT /users/me` (editar perfil propio, HU-014) sigue en `UserController` y es
> accesible por cualquier usuario autenticado. La regex `{id:\d+}` de las rutas
> admin evita que `me` colisione con `{id}`.

---

## 2. Autorización (RNF-001)

`AdminUserController` lleva `@PreAuthorize("hasRole('ADMINISTRADOR')")` a nivel de
clase. `hasRole('ADMINISTRADOR')` busca la autoridad `ROLE_ADMINISTRADOR`, que
`Auth.getAuthorities()` ya expone como `"ROLE_" + role.getName()`.

- Sin token o token inválido → **401** (el FE limpia sesión).
- Autenticado pero sin rol admin → **403** (`AccessDeniedException` mapeado en
  `GlobalExceptionHandler`; el FE **no** cierra sesión).

---

## 3. Convenciones (igual que E1–E3)

- **Base URL:** `env.apiUrl` (dev: `http://localhost:8080`). El FE la adjunta vía axios.
- **Auth:** header `Authorization: Bearer <JWT>` en todas las rutas de este módulo.
- **Nombres de campos:** `snake_case` (`last_name`, `created_at`, `total_elements`).
- **Timestamps:** ISO 8601 sin zona (`2026-07-25T18:00:00`).
- **Errores:** `ProblemDetail` → el mensaje viaja en `detail`; los de validación de
  campos además en `errors` (mapa `campo → mensaje`). El helper `getErrorMessage`
  del FE ya lee `message` y `detail`.
- **Timeout del FE:** 15 s. Todos estos endpoints responden muy por debajo.

---

## 4. Contrato de datos (JSON para el frontend)

### 4.1 Tipos TypeScript

Listos para pegar en `src/features/admin/types.ts`:

```ts
export type RoleName = 'ADMINISTRADOR' | 'RECEPCIONISTA' | 'CLIENTE'

/** Roles asignables al crear/editar. El backend rechaza CLIENTE con 400. */
export type AssignableRole = 'ADMINISTRADOR' | 'RECEPCIONISTA'

export type AdminUser = {
  id: number
  name: string
  last_name: string
  email: string
  phone: string | null
  role: RoleName
  active: boolean
  created_at: string          // ISO datetime
}

export type CreateUserRequest = {
  name: string                // 1–120
  last_name: string           // 1–120
  email: string               // formato email, máx. 150, único
  phone?: string | null       // opcional, máx. 30
  password: string            // 8–100
  role: AssignableRole
}

export type UpdateUserRequest = {
  name: string
  last_name: string
  phone?: string | null
  role: AssignableRole
}

export type UpdateUserActiveRequest = { active: boolean }

/** Envoltorio de paginación compartido con la tabla de reservas del E3. */
export type PageResponse<T> = {
  content: T[]
  page: number                // 0-based
  size: number
  total_elements: number
  total_pages: number
}
```

`AdminUser` se diferencia de `UserDTO` (perfil propio en `GET /auth/me`) en que
expone `active` y `created_at`, necesarios para la gestión de cuentas.

---

### 4.2 `GET /users` — nómina paginada (HU-034)

Parámetros de query, todos opcionales:

| Parámetro | Tipo | Default | Notas |
|---|---|---|---|
| `role` | `ADMINISTRADOR` \| `RECEPCIONISTA` \| `CLIENTE` | — | El **listado** sí admite CLIENTE (para consultar huéspedes); la **asignación** no. |
| `active` | `true` \| `false` | — | Filtra por cuentas activas o suspendidas. |
| `search` | texto | — | Coincidencia parcial, sin distinguir mayúsculas, en nombre, apellido **o** correo. Se ignora si viene vacío. |
| `page` | entero ≥ 0 | `0` | 0-based. |
| `size` | entero 1–100 | `20` | Fuera de rango → `400`. |

Orden fijo: **nombre ascendente**.

```
GET /users?role=RECEPCIONISTA&active=true&search=ana&page=0&size=20
Authorization: Bearer <JWT de admin>
```

```json
{
  "content": [
    {
      "id": 5,
      "name": "Ana",
      "last_name": "Pérez",
      "email": "ana@hmap.com",
      "phone": "88880000",
      "role": "RECEPCIONISTA",
      "active": true,
      "created_at": "2026-07-25T18:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 1,
  "total_pages": 1
}
```

Sin resultados: `content: []` con `total_elements: 0` (no es un 404).

---

### 4.3 `POST /users` — crear cuenta interna (HU-030/032)

```
POST /users
Authorization: Bearer <JWT de admin>
Content-Type: application/json
```

```json
{
  "name": "Ana",
  "last_name": "Pérez",
  "email": "ana@hmap.com",
  "phone": "88880000",
  "password": "secret123",
  "role": "RECEPCIONISTA"
}
```

**`201 Created`** con el `AdminUser` creado:

```json
{
  "id": 5,
  "name": "Ana",
  "last_name": "Pérez",
  "email": "ana@hmap.com",
  "phone": "88880000",
  "role": "RECEPCIONISTA",
  "active": true,
  "created_at": "2026-08-28T16:04:00"
}
```

- La contraseña se cifra con BCrypt antes de persistir y **nunca** vuelve en la respuesta.
- La cuenta nace `active: true` y puede iniciar sesión de inmediato.
- `phone` es opcional; omitirlo o enviar `null` es válido.

---

### 4.4 `PUT /users/{id}` — editar datos y rol (HU-031/032)

```
PUT /users/5
Authorization: Bearer <JWT de admin>
```

```json
{ "name": "Ana María", "last_name": "Pérez", "phone": "70000000", "role": "ADMINISTRADOR" }
```

**`200 OK`** con el `AdminUser` actualizado.

> El **correo** (identidad de acceso) y la **contraseña** no se editan aquí. Para la
> contraseña, el usuario usa `POST /auth/change-password` o el flujo de recuperación.

Es un `PUT` completo: enviar siempre los cuatro campos, no solo los que cambiaron.
Omitir `name`, `last_name` o `role` produce `400`.

---

### 4.5 `PATCH /users/{id}/active` — activar o suspender (HU-033)

```
PATCH /users/5/active
Authorization: Bearer <JWT de admin>
```

```json
{ "active": false }
```

**`200 OK`** con el `AdminUser` actualizado (`"active": false`).

Un usuario suspendido no puede iniciar sesión: `Auth.isEnabled()` devuelve `active`,
así que Spring Security lo rechaza en el login. **No se borran cuentas** — suspender
es la baja lógica y preserva el histórico de reservas asociadas.

---

## 5. Reglas de negocio

| Regla | Resultado |
|-------|-----------|
| Correo ya registrado al crear | **409** `ConflictException` |
| Rol distinto de RECEPCIONISTA/ADMINISTRADOR (ej. CLIENTE) | **400** `BadRequestException` |
| Un admin intenta **quitarse su propio rol** de administrador | **409** `ConflictException` |
| Un admin intenta **desactivar su propia cuenta** | **409** `ConflictException` |
| Usuario objetivo inexistente | **404** `ResourceNotFoundException` |
| `page` negativo o `size` fuera de `1..100` | **400** `BadRequestException` |

Las dos reglas de autoprotección (no quitarse el rol, no autodesactivarse) evitan
que un administrador se quede sin acceso al sistema. Reactivar la propia cuenta o
editar otros datos propios sí está permitido.

`active` ya vive en la entidad `Auth` e `isEnabled()` lo respeta: un usuario
suspendido no puede iniciar sesión (Spring Security lo rechaza en el login).

### Decisiones de diseño

| Decisión | Elección | Razón |
|---|---|---|
| Ruta del módulo | `/users` (no `/admin/users`) | El recurso es el mismo que el del perfil propio; el rol lo decide `@PreAuthorize`, no el prefijo. La regex `{id:\d+}` evita el choque con `/users/me`. |
| Servicio separado | `AdminUserService` aparte de `UserService` | Distinto actor y distintas reglas (autoprotección, roles asignables). Mantiene ambas clases pequeñas y testeables. |
| Baja de cuentas | Suspensión lógica (`active`), sin `DELETE` | Un usuario con reservas o check-ins históricos no puede desaparecer sin romper la trazabilidad. |
| Contraseña en `PUT` | No editable por el admin | El admin no debe conocer la contraseña de otro; existe el flujo de recuperación. |
| Rol `CLIENTE` | Filtrable pero no asignable | Las cuentas de huésped nacen del registro público o de la reserva manual (HU-020), no del panel. |
| Tope de `size` | 100 | Evita que un `size=100000` degrade el listado (RNF-004). |

---

## 6. Migración

`V5__seed_admin.sql` siembra el administrador inicial (el rol ya existe desde V1/V2):

| Campo | Valor |
|-------|-------|
| Email | `admin@hmap.com` |
| Contraseña | `admin123` (hash BCrypt, cost 10) |
| Rol | ADMINISTRADOR |

> **Producción:** cambiar esta contraseña tras el primer arranque.

Usuarios sembrados en total (útiles para el FE y los tests):

| Rol | Correo | Contraseña | Migración |
|---|---|---|---|
| ADMINISTRADOR | `admin@hmap.com` | `admin123` | V5 |
| RECEPCIONISTA | `recepcion@hmap.com` | `recepcion123` | V4 |

Las cuentas CLIENTE se crean con el registro público o con la reserva manual (HU-020).

---

## 7. Errores para el frontend

Todos los cuerpos de error son `ProblemDetail`. El mensaje legible viaja en `detail`
y ya está en español, apto para mostrarse tal cual en un toast.

| Caso | Código | Cuerpo |
|------|--------|--------|
| Validación de campos | 400 | `{ "detail": "Error de validación", "errors": { "email": "El correo no tiene un formato válido" } }` |
| Rol no asignable (ej. CLIENTE) | 400 | `{ "detail": "El rol debe ser RECEPCIONISTA o ADMINISTRADOR" }` |
| `page`/`size` inválidos | 400 | `{ "detail": "El tamaño de página debe estar entre 1 y 100" }` |
| Sin token o token expirado | 401 | *(cuerpo vacío)* — el FE limpia la sesión |
| Autenticado sin rol admin | 403 | `{ "detail": "No tienes permiso para esta acción" }` — **no** cerrar sesión |
| Usuario inexistente | 404 | `{ "detail": "Usuario no encontrado" }` |
| Correo ya registrado | 409 | `{ "detail": "Ya existe una cuenta con ese correo" }` |
| Admin se quita su propio rol | 409 | `{ "detail": "No puedes quitarte tu propio rol de administrador" }` |
| Admin se desactiva a sí mismo | 409 | `{ "detail": "No puedes desactivar tu propia cuenta" }` |

Los errores de validación de campos vienen además en `errors` (mapa `campo → mensaje`),
útil para pintar el mensaje junto a cada input. El FE ya tiene el helper `getFieldErrors`.

**Nota de UX (autoprotección):** deshabilita en la UI el botón de *suspender* y el
cambio de rol sobre la **propia** cuenta del admin logueado (compara el `id` con el
de `GET /auth/me`). Así el 409 queda como red de seguridad del backend, no como un
error que el usuario ve.

---

## 8. Cómo probar

### 8.1 Manual, con Swagger

1. `./mvnw spring-boot:run` → `http://localhost:8080/swagger-ui/index.html`.
2. Login como admin (`admin@hmap.com` / `admin123`) → *Authorize* con el token.
3. Flujo sugerido:
   - `POST /users` con rol `RECEPCIONISTA` → 201, cuenta creada.
   - `POST /users` con el mismo correo → 409.
   - `POST /users` con rol `CLIENTE` → 400.
   - `GET /users?role=RECEPCIONISTA&active=true&search=ana` → nómina filtrada.
   - `GET /users?size=0` → 400 (validación de paginación).
   - `PUT /users/{id}` sobre otra cuenta → cambia datos/rol.
   - `PATCH /users/{propio-id}/active` con `{ "active": false }` → 409 (autoprotección).
   - `PATCH /users/{otro-id}/active` con `{ "active": false }` → suspende; ese usuario ya no puede loguear.
   - Con un token de **CLIENTE**, llamar `GET /users` → **403** (la sesión no se cierra).

### 8.2 Tests unitarios del backend

```bash
./mvnw test -Dtest=AdminUserServiceTest
```

| Suite | Tests | Cubre |
|---|---|---|
| `AdminUserServiceTest` | 12 | crear (rol asignable, correo duplicado, rol no asignable), editar (autoprotección de rol, otro usuario, 404), activar/suspender (autodesactivación, otro usuario, reactivación propia), paginación (página negativa, tamaño fuera de rango, búsqueda en blanco) |

Suite completa del proyecto: **74 tests unitarios** (`./mvnw test -Dtest='!BackendApplicationTests'`).
El test de contexto `BackendApplicationTests` requiere MySQL en ejecución.

### 8.3 Pruebas automatizadas desde el frontend

Reutiliza el cliente `tests/api/client.ts` de
[FE-API-TESTING.md](../testing/FE-API-TESTING.md). Archivo sugerido
`tests/api/admin-users.test.ts`:

```ts
import { describe, it, expect, beforeAll } from 'vitest'
import { api, login } from './client'

const ADMIN = { email: 'admin@hmap.com', password: 'admin123' }

describe('panel admin — gestión de usuarios', () => {
  let token: string

  beforeAll(async () => {
    token = await login(ADMIN.email, ADMIN.password)
  })

  it('crea un recepcionista y rechaza el correo duplicado', async () => {
    const email = `recep_${Date.now()}@hmap.com`
    const body = {
      name: 'Test', last_name: 'Recep', email,
      password: 'secret123', role: 'RECEPCIONISTA',
    }

    const created = await api<{ id: number; role: string; active: boolean }>('/users', {
      method: 'POST', token, body,
    })
    expect(created.status).toBe(201)
    expect(created.data.role).toBe('RECEPCIONISTA')
    expect(created.data.active).toBe(true)

    // mismo correo → 409
    const dup = await api('/users', { method: 'POST', token, body })
    expect(dup.status).toBe(409)
  })

  it('rechaza rol no asignable (CLIENTE) con 400', async () => {
    const res = await api('/users', {
      method: 'POST', token,
      body: {
        name: 'X', last_name: 'Y', email: `x_${Date.now()}@hmap.com`,
        password: 'secret123', role: 'CLIENTE',
      },
    })
    expect(res.status).toBe(400)
  })

  it('lista con filtros y devuelve el envoltorio paginado', async () => {
    const { status, data } = await api<{
      content: unknown[]; total_elements: number; total_pages: number
    }>('/users?role=ADMINISTRADOR&active=true&page=0&size=10', { token })

    expect(status).toBe(200)
    expect(Array.isArray(data.content)).toBe(true)
    expect(data.total_elements).toBeGreaterThanOrEqual(1) // al menos el admin del seed
  })

  it('impide que el admin se desactive a sí mismo (409)', async () => {
    const me = await api<{ id: number }>('/auth/me', { token })
    const res = await api(`/users/${me.data.id}/active`, {
      method: 'PATCH', token, body: { active: false },
    })
    expect(res.status).toBe(409)
  })
})
```

Con el backend arriba y la migración V5 aplicada: `npm run test:api`.

---

## 9. Notas para el frontend

- **Guardas de ruta por rol** (cierre de RNF-001): consolidar los tres niveles
  `CLIENTE` / `RECEPCIONISTA` / `ADMINISTRADOR` reutilizando el componente
  `RequireAuth` y el layout interno del panel de recepción (E3).
- Sugerencia de rutas, coherente con `panel` (E2) y `panel-reception` (E3):
  `panel-admin` como índice de la nómina y `panel-admin/usuarios/nuevo` para el alta.
- El **listado** admite filtrar por cualquier rol (incluido CLIENTE); la
  **asignación** en crear/editar se limita a RECEPCIONISTA/ADMINISTRADOR.
- La paginación es 0-based y el envoltorio `PageResponse<T>` es el mismo de la tabla
  de reservas del E3: el componente de paginación se reutiliza tal cual.
- `e2e/admin.spec.ts` ya tiene los escenarios de gestión de usuarios comentados;
  descomentarlos al crear las rutas.

---

## 10. Cierre transversal (checklist RNF)

Revisión de autorización endpoint por endpoint, tal como está en el código:

| Ruta | Acceso |
|---|---|
| `/auth/login`, `/register`, `/forgot-password`, `/reset-password` | público (`SecurityConfig`) |
| `/v3/api-docs/**`, `/swagger-ui/**` | público (`SecurityConfig`) |
| `GET /rooms/**` | público — catálogo del portal |
| `POST/PUT/DELETE/PATCH /rooms/**` | `hasAnyRole('RECEPCIONISTA','ADMINISTRADOR')` |
| `GET /auth/me`, `POST /auth/change-password`, `PUT /users/me` | autenticado (cualquier rol) |
| `POST /reservations`, `GET /reservations/me`, `GET/PUT /reservations/{id}`, `POST /reservations/{id}/cancel` | autenticado + **propiedad** verificada en el servicio (403 si es ajena) |
| `GET /reservations`, `/today`, `/calendar`, `POST /reservations/manual`, `/confirm`, `/check-in`, `/check-out` | `hasAnyRole('RECEPCIONISTA','ADMINISTRADOR')` |
| `GET /panel-reception/occupancy` | `hasAnyRole('RECEPCIONISTA','ADMINISTRADOR')` |
| `GET/POST /users`, `PUT /users/{id}`, `PATCH /users/{id}/active` | `hasRole('ADMINISTRADOR')` |

`anyRequest().authenticated()` cierra la cadena: cualquier ruta no listada exige token.

- **RNF-001 (control de acceso):** ✓ cerrado aquí. Reglas gruesas en `SecurityConfig`,
  finas con `@PreAuthorize` junto a cada endpoint. `@PreAuthorize` valida el **rol**;
  el servicio valida el **dueño** del recurso. 401 solo para token ausente/inválido,
  403 para permiso insuficiente.
- **RNF-004 (rendimiento):** ✓ listados paginados con `PageResponse<T>` y tope de
  `size` en 100 (`PageRequests`); consulta de ocupación en una sola query agrupada;
  índices de solape y de listado en `V3`.
- **RNF-005 (disponibilidad):** ✓ motor de solape con bloqueo pesimista (E2/E3);
  las reservas nunca se borran, se cancelan.
- **RNF-007 (BCrypt):** ✓ `BCryptPasswordEncoder` en registro, alta interna,
  contraseña temporal de reserva manual y cambio/recuperación.
- **RNF-008 (JWT):** ✓ API stateless, token en `Authorization`, sin sesión de servidor.
- **RNF-002/003/006 (usabilidad, responsive, persistencia de selección):**
  responsabilidad del frontend.

### Pendientes al cerrar el entregable

- Frontend del panel admin (ver §9) — único frente abierto.
- Tag `v4-entregable` y merge de `fea/admin-v4` → `main`.
- Fuera de alcance por decisión: expiración automática de no-shows (`@Scheduled`)
  y envío de correo asíncrono (hoy es síncrono y best-effort).
