# Pruebas de API automatizadas desde el Frontend (React + TS)

Guía para automatizar, desde el proyecto **frontend**, el flujo que hoy se prueba a
mano en Swagger: `login → token → llamar endpoints → verificar transiciones de estado`.

Stack: **Vitest** + `fetch` nativo. Son tests de **integración contra el backend
corriendo** (no mocks), así que fijan el contrato real de la API y te avisan si algo
se rompe antes de tocar la UI.

> Requisito: el backend debe estar levantado (`./mvnw spring-boot:run`, en
> `http://localhost:8080`) antes de correr estos tests. En CI, se arranca el backend
> como paso previo.

---

## 1. Contrato de la API (verificado contra el código)

| Método | Ruta | Auth | Body | Respuesta |
|--------|------|------|------|-----------|
| POST | `/auth/login` | — | `{ email, password }` | `{ token }` |
| GET | `/rooms` | — | — | `Room[]` (imágenes = URLs absolutas de Cloudinary) |
| GET | `/panel-reception/occupancy` | interno | — | `{ occupied, available, maintenance, total }` |
| POST | `/reservations` | huésped | `{ room_id, check_in, check_out, guests }` | `201` + `ReservationDTO` |
| POST | `/reservations/{id}/confirm` | interno | — | `ReservationDTO` |
| POST | `/reservations/{id}/check-in` | interno | — | `ReservationDTO` |
| POST | `/reservations/{id}/check-out` | interno | — | `ReservationDTO` |
| GET | `/reservations/today` | interno | — | `{ check_ins, check_outs }` |

- **"interno"** = rol `RECEPCIONISTA` o `ADMINISTRADOR` (requiere `Authorization: Bearer <token>`).
- El JSON viaja en **snake_case** (`room_id`, `check_in`, `can_edit`, `created_at`, ...).
- `ReservationDTO.status` transiciona: `PENDIENTE → CONFIRMADA → CHECK_IN → CHECK_OUT`.

### Forma de `ReservationDTO`

```ts
type ReservationDTO = {
  id: number;
  code: string;              // "RSV-000123"
  guest: { /* GuestDTO */ };
  room: RoomDTO;
  check_in: string;          // "YYYY-MM-DD"
  check_out: string;
  guests: number;
  nights: number;
  total: number;
  status: "PENDIENTE" | "CONFIRMADA" | "CHECK_IN" | "CHECK_OUT" | "CANCELADA";
  can_edit: boolean;
  can_cancel: boolean;
  created_at: string;        // ISO datetime
};
```

---

## 2. Instalación

```bash
npm i -D vitest
```

---

## 3. Cliente HTTP tipado — `tests/api/client.ts`

```ts
const BASE_URL = process.env.API_URL ?? "http://localhost:8080";

export async function api<T = unknown>(
  path: string,
  opts: { method?: string; body?: unknown; token?: string } = {},
): Promise<{ status: number; data: T }> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: opts.method ?? "GET",
    headers: {
      "Content-Type": "application/json",
      ...(opts.token ? { Authorization: `Bearer ${opts.token}` } : {}),
    },
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  const text = await res.text();
  return { status: res.status, data: text ? JSON.parse(text) : (undefined as T) };
}

export async function login(email: string, password: string): Promise<string> {
  const { status, data } = await api<{ token: string }>("/auth/login", {
    method: "POST",
    body: { email, password },
  });
  if (status !== 200) throw new Error(`login falló: ${status}`);
  return data.token;
}
```

---

## 4. Flujo automatizado — `tests/api/reception-flow.test.ts`

```ts
import { describe, it, expect, beforeAll } from "vitest";
import { api, login } from "./client";

// Credenciales del seed (ajústalas a las tuyas)
const RECEPTION = { email: "recepcion@hmap.com", password: "recepcion123" };
const GUEST = { email: process.env.GUEST_EMAIL!, password: process.env.GUEST_PASSWORD! };

describe("catálogo público", () => {
  it("GET /rooms devuelve imágenes como URLs absolutas de Cloudinary", async () => {
    const { status, data } = await api<any[]>("/rooms");
    expect(status).toBe(200);
    expect(data.length).toBeGreaterThan(0);
    // regresión del bug de IMAGES_BASE_URL: nada de rutas relativas
    expect(data[0].images[0]).toMatch(/^https:\/\/res\.cloudinary\.com\//);
  });
});

describe("flujo de recepción", () => {
  let receptionToken: string;

  beforeAll(async () => {
    receptionToken = await login(RECEPTION.email, RECEPTION.password);
  });

  it("occupancy responde con el conteo por estado", async () => {
    const { status, data } = await api<{
      occupied: number; available: number; maintenance: number; total: number;
    }>("/panel-reception/occupancy", { token: receptionToken });

    expect(status).toBe(200);
    expect(data.total).toBe(data.occupied + data.available + data.maintenance);
  });

  it("reserva: crear (huésped) → confirmar → check-in transiciona el estado", async () => {
    const guestToken = await login(GUEST.email, GUEST.password);

    // 1. Huésped crea la reserva → nace PENDIENTE
    const created = await api<{ id: number; status: string }>("/reservations", {
      method: "POST",
      token: guestToken,
      body: { room_id: 1, check_in: "2026-09-01", check_out: "2026-09-03", guests: 2 },
    });
    expect(created.status).toBe(201);
    expect(created.data.status).toBe("PENDIENTE");
    const id = created.data.id;

    // 2. Recepción confirma
    const confirmed = await api<{ status: string }>(`/reservations/${id}/confirm`, {
      method: "POST", token: receptionToken,
    });
    expect(confirmed.data.status).toBe("CONFIRMADA");

    // 3. Recepción hace check-in
    const checkedIn = await api<{ status: string }>(`/reservations/${id}/check-in`, {
      method: "POST", token: receptionToken,
    });
    expect(checkedIn.data.status).toBe("CHECK_IN");
  });
});
```

---

## 5. Script y ejecución

`package.json`:

```json
{
  "scripts": {
    "test:api": "vitest run tests/api --environment node"
  }
}
```

Con el backend arriba:

```bash
GUEST_EMAIL=cliente@hmap.com GUEST_PASSWORD=cliente123 npm run test:api
```

(En Windows PowerShell: `$env:GUEST_EMAIL="cliente@hmap.com"; $env:GUEST_PASSWORD="cliente123"; npm run test:api`)

---

## 6. Notas y buenas prácticas

- **Aísla estos tests** de los unitarios de componentes. Los de API usan
  `environment: 'node'` (solo `fetch`); los de UI usan `jsdom`.
- **Datos de prueba:** el flujo de reserva *escribe* en la BD. Para que sea
  repetible, apunta a una BD de test recreada por corrida, o usa fechas/`room_id`
  que no choquen con datos existentes.
- **Regresión de Cloudinary:** el test de `/rooms` falla si alguien vuelve a dejar
  `IMAGES_BASE_URL` vacío en el `.env` del backend — te avisa antes de que las fotos
  dejen de cargar en la UI.
- **Base URL configurable:** `API_URL` permite apuntar a local, staging o al backend
  de CI sin tocar código.

---

## 7. Opcional — E2E de navegador con Playwright

Si más adelante quieres probar la app real (abrir páginas, clickear, ver la UI):

```bash
npm i -D @playwright/test && npx playwright install
```

El `request` context de Playwright también sirve para API, pero para "automatizar
el flujo de Swagger" Vitest + `fetch` (secciones anteriores) es más directo. Usa
Playwright cuando quieras validar el render y la navegación del frontend.
