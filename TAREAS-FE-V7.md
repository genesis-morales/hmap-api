# Tareas Frontend: cambios V7 (room_number + deactivation_reason)

Tareas para integrar los cambios de backend V7 en el frontend. El backend ya implementó:
- `rooms.room_number` (segunda llave natural, única)
- `users.deactivation_reason` (observación obligatoria al desactivar)
- `403` con mensaje propio al loguear con cuenta desactivada (no `401`)

---

## 1. Campo `room_number` en el CRUD de habitaciones

**Ubicación:** Panel admin → Inventario de habitaciones

**Archivos probables:**
- Formulario de crear/editar habitación (componente que envía `POST /rooms` o `PUT /rooms/:id`)
- Type/interface `Room` o `RoomFormData`
- Tabla/listado de habitaciones

**Cambios:**

1. Agregar input de texto `room_number` al formulario:
   - Label: "Número de habitación" (o similar)
   - Validaciones: requerido, máximo 10 caracteres
   - Placeholder sugerido: "Ej. 101, 102, A-3"

2. Mapear el campo en el submit:
   ```ts
   // Body POST/PUT /rooms
   {
     slug: "...",
     room_number: "101",  // ← nuevo
     name: "...",
     // ... resto de campos
   }
   ```

3. Agregar columna `room_number` en la tabla de habitaciones (si existe)

4. Manejar error de duplicado:
   - Backend responde `409` con `errors.room_number: "Ya existe una habitación con ese número"`
   - Anclar el mensaje al input correspondiente

5. Actualizar el type:
   ```ts
   type Room = {
     id: number
     slug: string
     room_number: string  // ← nuevo
     name: string
     // ... resto
   }
   ```

**Complejidad:** Baja (mecánico, igual que otros campos)  
**Estimación:** 20-30 min

---

## 2. Observación obligatoria al desactivar cuenta

**Ubicación:** Panel admin → Gestión de usuarios → Activar/Suspender

**Archivos probables:**
- Modal/diálogo de confirmar desactivación
- Handler que llama `PATCH /users/:id/active`
- Type `UpdateUserActiveRequest` y `AdminUser`

**Cambios:**

1. Agregar textarea `observation` en el diálogo de desactivar:
   - Label: "Motivo de la desactivación"
   - Placeholder: "Indica el motivo de la desactivación (máx. 300 caracteres)"
   - Validación: requerido cuando `active: false`, máximo 300 caracteres

2. Enviar al backend:
   ```ts
   // Al desactivar
   PATCH /users/:id/active
   { active: false, observation: "Cese de funciones" }
   
   // Al reactivar
   { active: true }  // sin observation o con null
   ```

3. Manejar error:
   - Backend responde `400` con `errors.observation: "Debes indicar el motivo..."`
   - Mostrar el mensaje debajo del textarea

4. Mostrar el motivo en cuentas desactivadas (opcional pero recomendado):
   - En la fila o detalle del usuario, mostrar `deactivation_reason` si no es `null`
   - Ej. badge "Suspendida" + tooltip con el motivo

5. Actualizar types:
   ```ts
   type AdminUser = {
     id: number
     name: string
     // ...
     active: boolean
     deactivation_reason: string | null  // ← nuevo
     created_at: string
   }
   
   type UpdateUserActiveRequest = {
     active: boolean
     observation?: string  // ← nuevo (obligatorio al desactivar)
   }
   ```

**Complejidad:** Media (requiere tocar el modal)  
**Estimación:** 30-40 min

---

## 3. Interceptor: no cerrar sesión ante `403` de cuenta desactivada en login ⚠️

**Ubicación:** Axios interceptor de respuestas + componente de login

**Archivos probables:**
- `axios.interceptors.response` (buscar manejo de `401`, `403`, `logout`)
- Hook/context de autenticación
- Componente de login

**Problema actual:**

El interceptor probablemente limpia la sesión ante `401`. El backend ahora devuelve `403` (no `401`) cuando logueás con cuenta desactivada, con:

```json
{
  "status": 403,
  "detail": "Estimado usuario, su cuenta se encuentra desactivada. En caso de consulta, comuníquese con la administración del Hotel Manuel Antonio Park."
}
```

Ese `403` debe:
- **NO** cerrar sesión (porque no hay sesión aún)
- Mostrar el `detail` en el formulario de login

**Solución sugerida:**

1. **En el interceptor**, distinguir tres casos:

   ```ts
   axios.interceptors.response.use(
     response => response,
     error => {
       const status = error.response?.status
       const url = error.config?.url
   
       if (status === 401) {
         // Sesión expirada/token inválido → limpiar token, redirigir a login
         clearAuth()
         navigate('/login')
       } else if (status === 403) {
         if (url?.includes('/auth/login')) {
           // 403 en login → cuenta desactivada, NO limpiar sesión
           // Dejar que el componente de login maneje el error
         } else {
           // 403 en ruta protegida → sin permiso, NO cerrar sesión
           // (el usuario está logueado, solo no tiene permiso para esa acción)
           toast.error('No tienes permiso para esta acción')
         }
       }
       
       return Promise.reject(error)
     }
   )
   ```

2. **En el componente de login**, capturar el `403`:

   ```ts
   try {
     await login(email, password)
   } catch (error) {
     if (error.response?.status === 403) {
       // Mostrar el mensaje del backend tal cual
       setError(error.response.data.detail)
     } else if (error.response?.status === 401) {
       setError('Credenciales inválidas')
     } else {
       setError('Error al iniciar sesión')
     }
   }
   ```

**⚠️ Riesgo:**

Mal implementado, podría romper el manejo de sesión existente. Casos a testear:

- ✅ Login con credenciales incorrectas → `401`, mensaje genérico
- ✅ Login con cuenta desactivada → `403`, mensaje del hotel, **no** cierra sesión
- ✅ Ruta protegida sin permiso (post-login) → `403`, toast "sin permiso", **no** cierra sesión
- ✅ Token expirado en cualquier request → `401`, **sí** cierra sesión y redirige

**Complejidad:** Alta (delicado, afecta flujo de autenticación)  
**Estimación:** 40-60 min

---

## 4. Actualizar tipos TypeScript

**Archivos probables:**
- `types/api.ts` o similar
- `types/models.ts`

**Cambios:**

```ts
// Ya cubiertos en las tareas 1 y 2, resumidos aquí:

type Room = {
  // ...
  room_number: string  // ← nuevo
}

type AdminUser = {
  // ...
  deactivation_reason: string | null  // ← nuevo
}

type UpdateUserActiveRequest = {
  active: boolean
  observation?: string  // ← nuevo
}
```

**Complejidad:** Trivial  
**Estimación:** 5 min

---

## Resumen

| # | Tarea | Complejidad | Tiempo |
|---|-------|-------------|--------|
| 1 | Campo `room_number` | Baja | 20-30 min |
| 2 | Observación al desactivar | Media | 30-40 min |
| 3 | Interceptor 403 login ⚠️ | Alta | 40-60 min |
| 4 | Tipos TS | Trivial | 5 min |
| **Total** | | | **~2 horas** |

**Orden recomendado:**
1. Tipos (4) — rápido, desbloquea el resto
2. Campo `room_number` (1) — mecánico, bajo riesgo
3. Observación (2) — requiere modal pero es directo
4. Interceptor (3) — el más delicado, requiere testing exhaustivo

---

## Contratos de API actualizados

### `GET /rooms` — ahora incluye `room_number`

```json
{
  "id": 1,
  "slug": "deluxe-cama-grande",
  "room_number": "102",
  "name": "Habitación Deluxe con cama extragrande",
  "status": "DISPONIBLE",
  ...
}
```

### `POST/PUT /rooms` — `room_number` obligatorio

```json
{
  "slug": "nueva-hab",
  "room_number": "104",
  "name": "Nueva Habitación",
  ...
}
```

**Error si duplicado:**
```json
{
  "status": 409,
  "detail": "Ya existe una habitación con ese número",
  "errors": { "room_number": "Ya existe una habitación con ese número" }
}
```

### `GET /users` — ahora incluye `deactivation_reason`

```json
{
  "id": 5,
  "name": "Ana",
  "active": false,
  "deactivation_reason": "Cese de funciones",
  ...
}
```

### `PATCH /users/:id/active` — `observation` obligatoria al desactivar

**Desactivar:**
```json
{ "active": false, "observation": "Cese de funciones" }
```

**Reactivar:**
```json
{ "active": true }
```

**Error si desactivás sin observación:**
```json
{
  "status": 400,
  "detail": "Debes indicar el motivo de la desactivación",
  "errors": { "observation": "Debes indicar el motivo de la desactivación" }
}
```

### `POST /auth/login` — `403` para cuenta desactivada

**Antes (V6):** `401` genérico "Credenciales inválidas"  
**Ahora (V7):** `403` con mensaje propio

```json
{
  "status": 403,
  "detail": "Estimado usuario, su cuenta se encuentra desactivada. En caso de consulta, comuníquese con la administración del Hotel Manuel Antonio Park."
}
```

**⚠️ El FE NO debe cerrar sesión ante este `403`** (solo mostrarlo en el form de login).

---

## Referencias

- Backend implementado en commit `4a5a643` (rama `fea/admin-v4`)
- Migración: `V7__room_number_and_deactivation_reason.sql`
- Docs actualizados: `MODELO-DATOS.md`, `api-contrato.md`, `E3-RECEPCION.md`, `E4-ADMIN.md`
- Justificación de diseño: `docs/CAMBIOS-MODELO-V7.md`
