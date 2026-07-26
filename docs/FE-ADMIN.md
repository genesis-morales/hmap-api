# Panel de Administrador — Guía de integración Frontend (E4)

Contrato de la API del panel admin (HU-030 a HU-034) tal como lo consume el
**frontend** (React + TS), más tipos, ejemplos y pruebas automatizadas.

Complementa a [FE-API-TESTING.md](./FE-API-TESTING.md) (setup de Vitest + cliente
HTTP) y a [E4-ADMIN.md](./E4-ADMIN.md) (documentación de negocio/backend).

> Todas las rutas requieren rol **ADMINISTRADOR**. El JSON viaja en **snake_case**.

---

## 1. Autorización

Enviar el JWT en cada petición: `Authorization: Bearer <token>`.

| Situación | Código | Qué hace el FE |
|-----------|--------|----------------|
| Sin token o token inválido | **401** | Limpiar sesión y redirigir a login |
| Autenticado pero sin rol admin | **403** | Mostrar "sin permiso"; **no** cerrar sesión |

En el router, proteger `/admin/**` con una guarda que exija `role === "ADMINISTRADOR"`
(el rol viene de `GET /auth/me`).

---

## 2. Contrato de la API

| Método | Ruta | HU | Descripción |
|--------|------|----|-------------|
| GET | `/users?role=&active=&search=&page=&size=` | HU-034 | Nómina paginada con filtros |
| POST | `/users` | HU-030/032 | Crear cuenta interna |
| PUT | `/users/{id}` | HU-031/032 | Editar datos y reasignar rol |
| PATCH | `/users/{id}/active` | HU-033 | Activar / suspender |

Parámetros de `GET /users` (todos opcionales):

- `role`: `ADMINISTRADOR` | `RECEPCIONISTA` | `CLIENTE` (filtra por rol; el listado sí incluye clientes)
- `active`: `true` | `false`
- `search`: texto libre (busca en nombre, apellido y correo)
- `page` (default `0`), `size` (default `20`)

---

## 3. Tipos TypeScript

```ts
export type RoleName = "ADMINISTRADOR" | "RECEPCIONISTA" | "CLIENTE";

// Roles que el admin puede asignar al crear/editar (el backend rechaza CLIENTE con 400)
export type AssignableRole = "ADMINISTRADOR" | "RECEPCIONISTA";

export type AdminUser = {
  id: number;
  name: string;
  last_name: string;
  email: string;
  phone: string | null;
  role: RoleName;
  active: boolean;
  created_at: string; // ISO datetime
};

export type CreateUserRequest = {
  name: string;
  last_name: string;
  email: string;
  phone?: string | null;
  password: string;      // 8–100 caracteres
  role: AssignableRole;
};

export type UpdateUserRequest = {
  name: string;
  last_name: string;
  phone?: string | null;
  role: AssignableRole;
};

export type UpdateUserActiveRequest = { active: boolean };

// Envoltorio de paginación reutilizable (igual que en reservas)
export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
};
```

---

## 4. Ejemplos

### Listar (GET /users)

`GET /users?role=RECEPCIONISTA&active=true&search=ana&page=0&size=20`

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

### Crear (POST /users) → 201

```json
// request
{ "name": "Ana", "last_name": "Pérez", "email": "ana@hmap.com",
  "phone": "88880000", "password": "secret123", "role": "RECEPCIONISTA" }
```

Devuelve el `AdminUser` creado (`active: true`).

### Editar (PUT /users/{id}) → 200

```json
{ "name": "Ana María", "last_name": "Pérez", "phone": "70000000", "role": "ADMINISTRADOR" }
```

> `email` y `password` **no** se editan aquí. Para la contraseña, usar el flujo de
> recuperación / cambio de contraseña.

### Activar/suspender (PATCH /users/{id}/active) → 200

```json
{ "active": false }
```

Un usuario suspendido (`active: false`) no puede iniciar sesión.

---

## 5. Errores a manejar en el FE

| Caso | Código | Cuerpo (ProblemDetail) |
|------|--------|------------------------|
| Validación de campos | 400 | `{ "detail": "Error de validación", "errors": { "email": "..." } }` |
| Rol no asignable (ej. CLIENTE) | 400 | `{ "detail": "El rol debe ser RECEPCIONISTA o ADMINISTRADOR" }` |
| Correo ya registrado | 409 | `{ "detail": "Ya existe una cuenta con ese correo" }` |
| Admin se quita su propio rol | 409 | `{ "detail": "No puedes quitarte tu propio rol de administrador" }` |
| Admin se desactiva a sí mismo | 409 | `{ "detail": "No puedes desactivar tu propia cuenta" }` |
| Usuario inexistente | 404 | `{ "detail": "Usuario no encontrado" }` |

Los errores de validación de campos vienen en `errors` (mapa `campo → mensaje`),
útil para pintar mensajes junto a cada input. Los de negocio traen el texto en
`detail`, ideal para un toast.

**UX de autoprotección:** deshabilita en la UI el botón de "suspender" y el cambio
de rol sobre la **propia** cuenta del admin logueado, para evitar el 409.

---

## 6. Pruebas automatizadas (Vitest)

Reutiliza `tests/api/client.ts` de [FE-API-TESTING.md](./FE-API-TESTING.md).
Archivo `tests/api/admin-users.test.ts`:

```ts
import { describe, it, expect, beforeAll } from "vitest";
import { api, login } from "./client";

const ADMIN = { email: "admin@hmap.com", password: "admin123" };

describe("panel admin — gestión de usuarios", () => {
  let token: string;

  beforeAll(async () => {
    token = await login(ADMIN.email, ADMIN.password);
  });

  it("crea un recepcionista y rechaza el correo duplicado", async () => {
    const email = `recep_${Date.now()}@hmap.com`;
    const body = {
      name: "Test", last_name: "Recep", email,
      password: "secret123", role: "RECEPCIONISTA",
    };

    const created = await api<{ id: number; role: string; active: boolean }>("/users", {
      method: "POST", token, body,
    });
    expect(created.status).toBe(201);
    expect(created.data.role).toBe("RECEPCIONISTA");
    expect(created.data.active).toBe(true);

    // mismo correo → 409
    const dup = await api("/users", { method: "POST", token, body });
    expect(dup.status).toBe(409);
  });

  it("rechaza rol no asignable (CLIENTE) con 400", async () => {
    const res = await api("/users", {
      method: "POST", token,
      body: { name: "X", last_name: "Y", email: `x_${Date.now()}@hmap.com`,
              password: "secret123", role: "CLIENTE" },
    });
    expect(res.status).toBe(400);
  });

  it("lista con filtros y devuelve el envoltorio paginado", async () => {
    const { status, data } = await api<{
      content: unknown[]; total_elements: number; total_pages: number;
    }>("/users?role=ADMINISTRADOR&active=true&page=0&size=10", { token });

    expect(status).toBe(200);
    expect(Array.isArray(data.content)).toBe(true);
    expect(data.total_elements).toBeGreaterThanOrEqual(1); // al menos el admin seed
  });

  it("impide que el admin se desactive a sí mismo (409)", async () => {
    // el id del admin logueado se obtiene de /auth/me
    const me = await api<{ id: number }>("/auth/me", { token });
    const res = await api(`/users/${me.data.id}/active`, {
      method: "PATCH", token, body: { active: false },
    });
    expect(res.status).toBe(409);
  });
});
```

Ejecutar (con el backend arriba y la migración V5 aplicada):

```bash
npm run test:api
```

---

## 7. Notas para el FE

- **Guardas de ruta por rol** (cierre de RNF-001): consolidar tres niveles —
  `CLIENTE`, `RECEPCIONISTA`, `ADMINISTRADOR`— reutilizando el layout interno del
  panel de recepción (E3) para el panel admin.
- El **listado** admite filtrar por cualquier rol (incluido CLIENTE); la
  **asignación** de rol en crear/editar se limita a RECEPCIONISTA/ADMINISTRADOR.
- Credenciales del admin sembrado: `admin@hmap.com` / `admin123` (cambiar en
  producción; ver [E4-ADMIN.md](./E4-ADMIN.md)).
