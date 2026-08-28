# Flujos E2E con Playwright — por rol

Escenarios end-to-end concretos sobre el frontend, agrupados por actor. Requiere
el setup de [FE-E2E-PLAYWRIGHT.md](FE-E2E-PLAYWRIGHT.md) (config, `auth.setup.ts`,
usuarios sembrados).

> Las rutas (`/login`, `/rooms`, `/admin/users`, ...) y textos son **placeholders**;
> alinéalos con tu FE. Los códigos de estado y reglas provienen del contrato real
> de la API (E1–E4).

---

## 1. Portal público (sin sesión)

`e2e/public.spec.ts`:

```ts
import { test, expect } from "@playwright/test";

test("la home carga y muestra el catálogo de habitaciones", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
});

test("las imágenes del catálogo se sirven desde Cloudinary", async ({ page }) => {
  await page.goto("/rooms");
  const firstImg = page.getByRole("img").first();
  await expect(firstImg).toBeVisible();
  // Regresión del bug de IMAGES_BASE_URL: deben ser URLs absolutas de Cloudinary
  await expect(firstImg).toHaveAttribute("src", /res\.cloudinary\.com/);
});

test("el detalle de habitación muestra precio y amenidades", async ({ page }) => {
  await page.goto("/rooms");
  await page.getByRole("link", { name: /ver|detalle|reservar/i }).first().click();
  await expect(page).toHaveURL(/\/rooms\/.+/);
  await expect(page.getByText(/noche|USD|\$/)).toBeVisible();
});
```

---

## 2. Autenticación

`e2e/auth.spec.ts`:

```ts
import { test, expect } from "@playwright/test";

test("login inválido muestra error y no redirige", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel(/correo|email/i).fill("nadie@hmap.com");
  await page.getByLabel(/contraseña|password/i).fill("malaclave");
  await page.getByRole("button", { name: /iniciar sesión|entrar/i }).click();
  await expect(page.getByText(/credenciales inválidas|inválid/i)).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
});

test("registro de un nuevo cliente e ingreso al portal", async ({ page }) => {
  const email = `cliente_${Date.now()}@hmap.com`;
  await page.goto("/register");
  await page.getByLabel(/nombre/i).first().fill("Nuevo");
  await page.getByLabel(/apellido/i).fill("Cliente");
  await page.getByLabel(/correo|email/i).fill(email);
  await page.getByLabel(/contraseña|password/i).fill("secret123");
  await page.getByRole("button", { name: /registrar|crear cuenta/i }).click();
  await expect(page).not.toHaveURL(/\/register$/);
});
```

---

## 3. Cliente — motor de reservas

Puedes registrar el cliente por UI (arriba) o crear su sesión en un `beforeAll`.
Flujo principal:

`e2e/client-reservations.spec.ts`:

```ts
import { test, expect } from "@playwright/test";

// Si tienes un storageState de cliente, úsalo; si no, loguéate en beforeEach.
test("crear una reserva y verla en 'mis reservas'", async ({ page }) => {
  await page.goto("/rooms");
  await page.getByRole("link", { name: /reservar|ver/i }).first().click();

  // Formulario de reserva (ajusta labels)
  await page.getByLabel(/entrada|check.?in/i).fill("2026-09-01");
  await page.getByLabel(/salida|check.?out/i).fill("2026-09-03");
  await page.getByLabel(/huésped|personas|guests/i).fill("2");
  await page.getByRole("button", { name: /reservar|confirmar/i }).click();

  await expect(page.getByText(/reserva|RSV-/i)).toBeVisible();

  await page.goto("/mis-reservas");
  await expect(page.getByText(/RSV-/)).toBeVisible();
});

test("cancelar una reserva dentro de la ventana permitida", async ({ page }) => {
  await page.goto("/mis-reservas");
  await page.getByRole("button", { name: /cancelar/i }).first().click();
  await page.getByRole("button", { name: /confirmar|sí/i }).click();
  await expect(page.getByText(/cancelada/i)).toBeVisible();
});
```

---

## 4. Recepción

```ts
import { test, expect } from "@playwright/test";

test.use({ storageState: "e2e/.auth/reception.json" });

test("el dashboard de ocupación muestra los conteos", async ({ page }) => {
  await page.goto("/panel-reception");
  await expect(page.getByText(/ocupad|disponible|mantenimiento/i)).toBeVisible();
});

test("registrar el check-in de una reserva del día", async ({ page }) => {
  await page.goto("/panel-reception/today");
  await page.getByRole("button", { name: /check.?in/i }).first().click();
  await expect(page.getByText(/check.?in|ocupada/i)).toBeVisible();
});

test("crear una reserva manual", async ({ page }) => {
  await page.goto("/panel-reception/reservations/new");
  await page.getByLabel(/nombre/i).first().fill("Huésped");
  await page.getByLabel(/apellido/i).fill("Telefónico");
  await page.getByLabel(/correo|email/i).fill(`manual_${Date.now()}@hmap.com`);
  await page.getByLabel(/entrada|check.?in/i).fill("2026-10-01");
  await page.getByLabel(/salida|check.?out/i).fill("2026-10-04");
  await page.getByLabel(/huésped|personas/i).fill("2");
  await page.getByRole("button", { name: /crear|guardar/i }).click();
  await expect(page.getByText(/RSV-/)).toBeVisible();
});
```

---

## 5. Administrador — gestión de usuarios (E4)

```ts
import { test, expect } from "@playwright/test";

test.use({ storageState: "e2e/.auth/admin.json" });

test("crear un recepcionista y verlo en la lista", async ({ page }) => {
  const email = `recep_${Date.now()}@hmap.com`;
  await page.goto("/admin/users");
  await page.getByRole("button", { name: /nuevo|crear usuario/i }).click();

  await page.getByLabel(/nombre/i).first().fill("Test");
  await page.getByLabel(/apellido/i).fill("Recep");
  await page.getByLabel(/correo|email/i).fill(email);
  await page.getByLabel(/contraseña|password/i).fill("secret123");
  await page.getByLabel(/rol/i).selectOption("RECEPCIONISTA");
  await page.getByRole("button", { name: /crear|guardar/i }).click();

  await expect(page.getByText(email)).toBeVisible();
});

test("correo duplicado muestra error de conflicto (409)", async ({ page }) => {
  await page.goto("/admin/users");
  await page.getByRole("button", { name: /nuevo|crear usuario/i }).click();
  await page.getByLabel(/nombre/i).first().fill("Dup");
  await page.getByLabel(/apellido/i).fill("Licado");
  await page.getByLabel(/correo|email/i).fill("recepcion@hmap.com"); // ya existe
  await page.getByLabel(/contraseña|password/i).fill("secret123");
  await page.getByLabel(/rol/i).selectOption("RECEPCIONISTA");
  await page.getByRole("button", { name: /crear|guardar/i }).click();
  await expect(page.getByText(/ya existe una cuenta con ese correo/i)).toBeVisible();
});

test("suspender a otro usuario", async ({ page }) => {
  await page.goto("/admin/users");
  const row = page.getByRole("row", { name: /recep_/i }).first();
  await row.getByRole("button", { name: /suspender|desactivar/i }).click();
  await expect(row.getByText(/inactiv|suspendid/i)).toBeVisible();
});

test("el admin no puede desactivarse a sí mismo (autoprotección)", async ({ page }) => {
  await page.goto("/admin/users");
  // La fila del propio admin: el botón debería estar deshabilitado en la UI.
  const selfRow = page.getByRole("row", { name: /admin@hmap\.com/i });
  const toggle = selfRow.getByRole("button", { name: /suspender|desactivar/i });
  // Opción A (recomendada): el FE lo deshabilita
  await expect(toggle).toBeDisabled();
  // Opción B: si no está deshabilitado, al intentarlo la API responde 409 y se ve el error
  // await toggle.click();
  // await expect(page.getByText(/no puedes desactivar tu propia cuenta/i)).toBeVisible();
});
```

---

## 6. Guardas de ruta por rol (RNF-001)

```ts
import { test, expect } from "@playwright/test";

test.use({ storageState: "e2e/.auth/reception.json" });

test("un recepcionista no puede entrar al panel admin", async ({ page }) => {
  await page.goto("/admin/users");
  // El FE debe bloquear/redirigir (403 no cierra sesión); ajusta a tu comportamiento.
  await expect(page).not.toHaveURL(/\/admin\/users$/);
});
```

---

## 7. Notas

- **Aislamiento de datos:** varios flujos escriben en la BD (reservas, usuarios).
  Usa correos/fechas únicos (`Date.now()`) y, si puedes, una BD de test que se
  recree por corrida para que las pruebas sean repetibles.
- **Regresiones útiles ya incluidas:** imágenes de Cloudinary (§1) y autoprotección
  del admin (§5).
- Mantén los textos de los `getByText`/`getByRole` alineados con los de tu UI
  (o cámbialos por `data-testid` si cambian a menudo).
