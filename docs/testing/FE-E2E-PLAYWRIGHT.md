# Pruebas E2E con Playwright — Setup y convenciones (Frontend)

Guía para montar pruebas end-to-end de navegador sobre el frontend React + TS.
Complementa a [FE-API-TESTING.md](FE-API-TESTING.md) (pruebas de contrato con
Vitest + `fetch`). Los flujos concretos por rol están en
[FE-E2E-FLOWS.md](FE-E2E-FLOWS.md).

> **Cuándo usar cada una:**
> - **Vitest + fetch** → contrato de la API (rápido, sin navegador).
> - **Playwright** → la app real en el navegador: render, navegación, formularios,
>   guardas de ruta por rol.

---

## 1. Requisitos previos

- El **backend** corriendo en `http://localhost:8080` con las migraciones aplicadas
  (incluida `V5` → admin sembrado).
- El **frontend** en `http://localhost:5173` (Vite). El backend ya permite CORS
  desde ese origen.

Usuarios sembrados disponibles:

| Rol | Email | Contraseña |
|-----|-------|-----------|
| ADMINISTRADOR | `admin@hmap.com` | `admin123` |
| RECEPCIONISTA | `recepcion@hmap.com` | `recepcion123` |
| CLIENTE | *(no hay seed)* | se registra en la prueba |

> Cambiar estas contraseñas en producción (ver [E4-ADMIN.md](../modulos/E4-ADMIN.md)).

---

## 2. Instalación

```bash
npm i -D @playwright/test
npx playwright install
```

---

## 3. Configuración — `playwright.config.ts`

```ts
import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: "html",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:5173",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  // Levanta el FE automáticamente antes de las pruebas (el backend se arranca aparte).
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [
    { name: "setup", testMatch: /.*\.setup\.ts/ },
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
      dependencies: ["setup"],
    },
  ],
});
```

---

## 4. Autenticación reutilizable (storageState)

En lugar de loguear en cada prueba, se inicia sesión **una vez por rol** y se
guarda el estado (el JWT que el FE persiste en `localStorage`). Las pruebas parten
ya autenticadas.

`e2e/auth.setup.ts`:

```ts
import { test as setup, expect } from "@playwright/test";

const CREDS = {
  admin: { email: "admin@hmap.com", password: "admin123" },
  reception: { email: "recepcion@hmap.com", password: "recepcion123" },
};

async function loginAndSave(page, base: string, email: string, password: string, file: string) {
  await page.goto(`${base}/login`);
  await page.getByLabel(/correo|email/i).fill(email);
  await page.getByLabel(/contraseña|password/i).fill(password);
  await page.getByRole("button", { name: /iniciar sesión|entrar|login/i }).click();
  // Espera a que el login haya redirigido (ajusta a tu ruta post-login)
  await expect(page).not.toHaveURL(/\/login$/);
  await page.context().storageState({ path: file });
}

setup("authenticate as admin", async ({ page, baseURL }) => {
  await loginAndSave(page, baseURL!, CREDS.admin.email, CREDS.admin.password,
    "e2e/.auth/admin.json");
});

setup("authenticate as reception", async ({ page, baseURL }) => {
  await loginAndSave(page, baseURL!, CREDS.reception.email, CREDS.reception.password,
    "e2e/.auth/reception.json");
});
```

Uso en una prueba:

```ts
import { test } from "@playwright/test";

test.use({ storageState: "e2e/.auth/admin.json" });

test("el admin ve el panel de usuarios", async ({ page }) => {
  await page.goto("/admin/users");
  // ...
});
```

> Añade `e2e/.auth/` a `.gitignore`: contiene tokens de sesión.

---

## 5. Convenciones de selectores

Prioridad (de más robusto a menos):

1. **Rol accesible**: `getByRole("button", { name: /crear/i })`, `getByRole("table")`.
2. **Label / texto**: `getByLabel(/correo/i)`, `getByText(...)`.
3. **`data-testid`** para casos sin semántica clara: `getByTestId("user-row")`.

Evita selectores CSS frágiles (`.btn-primary > span`). Agrega `data-testid` en los
componentes React donde haga falta un anclaje estable.

> Los ejemplos usan rutas (`/login`, `/admin/users`) y textos de placeholder.
> **Ajústalos a las rutas y etiquetas reales de tu FE.**

---

## 6. Scripts y ejecución

`package.json`:

```json
{
  "scripts": {
    "e2e": "playwright test",
    "e2e:ui": "playwright test --ui",
    "e2e:report": "playwright show-report"
  }
}
```

```bash
# 1) backend arriba:  ./mvnw spring-boot:run   (en el repo de la API)
# 2) e2e (Playwright levanta el FE solo):
npm run e2e
```

---

## 7. CI (referencia)

Orden de arranque en el pipeline:

1. Levantar MySQL de test y aplicar migraciones (Flyway al arrancar la API).
2. Arrancar el backend (`./mvnw spring-boot:run` o el jar) y esperar al `:8080`.
3. `npm run e2e` (Playwright arranca el FE con `webServer`).

Sube el reporte HTML (`playwright-report/`) y las trazas como artefactos para
depurar fallos.
