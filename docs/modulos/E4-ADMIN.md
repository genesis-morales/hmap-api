# Entregable 4 — Panel de Administrador (API)

Gestión de cuentas de personal interno y consolidación del control de acceso por
rol. Cubre **HU-030 a HU-034 · RF-015 · RNF-001**.

Complementa a [AUTH.md](E1-AUTH.md) (E1), [E2-RESERVAS.md](E2-RESERVAS.md) (E2) y
[E3-RECEPCION.md](E3-RECEPCION.md) (E3). El plan general está en
[E3-E4-PLAN.md](../E3-E4-PLAN.md).

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

## 3. Contrato de datos (snake_case)

### `AdminUserDTO` (respuesta)

```json
{
  "id": 1,
  "name": "Administrador",
  "last_name": "HMAP",
  "email": "admin@hmap.com",
  "phone": null,
  "role": "ADMINISTRADOR",
  "active": true,
  "created_at": "2026-07-25T18:00:00"
}
```

A diferencia de `UserDTO` (perfil propio en `GET /auth/me`), expone `active` y
`created_at` para la gestión de cuentas.

### `CreateUserRequest` (POST)

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

- `password`: 8–100 caracteres. Se cifra con BCrypt antes de persistir.
- `role`: **RECEPCIONISTA** o **ADMINISTRADOR** (ver reglas). La cuenta nace `active = true`.

### `UpdateUserRequest` (PUT)

```json
{ "name": "Ana María", "last_name": "Pérez", "phone": "70000000", "role": "ADMINISTRADOR" }
```

El **correo** (identidad de acceso) y la **contraseña** no se editan aquí. El
cambio de contraseña usa el flujo propio (`/auth/change-password` o recuperación).

### `UpdateUserActiveRequest` (PATCH)

```json
{ "active": false }
```

---

## 4. Reglas de negocio

| Regla | Resultado |
|-------|-----------|
| Correo ya registrado al crear | **409** `ConflictException` |
| Rol distinto de RECEPCIONISTA/ADMINISTRADOR (ej. CLIENTE) | **400** `BadRequestException` |
| Un admin intenta **quitarse su propio rol** de administrador | **409** `ConflictException` |
| Un admin intenta **desactivar su propia cuenta** | **409** `ConflictException` |
| Usuario objetivo inexistente | **404** `ResourceNotFoundException` |

Las dos reglas de autoprotección (no quitarse el rol, no autodesactivarse) evitan
que un administrador se quede sin acceso al sistema. Reactivar la propia cuenta o
editar otros datos propios sí está permitido.

`active` ya vive en la entidad `Auth` e `isEnabled()` lo respeta: un usuario
suspendido no puede iniciar sesión (Spring Security lo rechaza en el login).

---

## 5. Migración

`V5__seed_admin.sql` siembra el administrador inicial (el rol ya existe desde V1/V2):

| Campo | Valor |
|-------|-------|
| Email | `admin@hmap.com` |
| Contraseña | `admin123` (hash BCrypt, cost 10) |
| Rol | ADMINISTRADOR |

> **Producción:** cambiar esta contraseña tras el primer arranque.

---

## 6. Cómo probar

1. `./mvnw spring-boot:run` → `http://localhost:8080/swagger-ui/index.html`.
2. Login como admin (`admin@hmap.com` / `admin123`) → *Authorize* con el token.
3. Flujo sugerido:
   - `POST /users` con rol `RECEPCIONISTA` → 201, cuenta creada.
   - `POST /users` con el mismo correo → 409.
   - `POST /users` con rol `CLIENTE` → 400.
   - `GET /users?role=RECEPCIONISTA&active=true&search=ana` → nómina filtrada.
   - `PUT /users/{id}` sobre otra cuenta → cambia datos/rol.
   - `PATCH /users/{propio-id}/active` con `{ "active": false }` → 409 (autoprotección).
   - `PATCH /users/{otro-id}/active` con `{ "active": false }` → suspende; ese usuario ya no puede loguear.

---

## 7. Cierre transversal (checklist RNF)

- **RNF-001 (control de acceso):** `@PreAuthorize` revisado en todos los endpoints
  internos — panel recepción (`hasAnyRole('RECEPCIONISTA','ADMINISTRADOR')`) y
  panel admin (`hasRole('ADMINISTRADOR')`). Rutas públicas acotadas en `SecurityConfig`.
- **RNF-007 (BCrypt):** ✓ contraseñas cifradas con `BCryptPasswordEncoder`.
- **RNF-008 (JWT):** ✓ API stateless con token en `Authorization`.
- **RNF-004/005 (rendimiento/disponibilidad):** listados paginados con
  `PageResponse<T>`; disponibilidad cubierta en E2/E3.
- Los RNF de FE (usabilidad, responsive) quedan como responsabilidad del frontend.
