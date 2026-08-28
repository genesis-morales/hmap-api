# Anclaje de errores de validación en el frontend

Los errores de la API ahora distinguen entre **errores atribuibles a un campo** (que
deben pintarse debajo del input correspondiente) y **avisos globales** (que van al
toast). Este documento describe el contrato final, el helper a reutilizar en cada
formulario, y el checklist de cuáles formularios tocar.

---

## 1. Contrato del backend (ya implementado)

Todos los errores son `ProblemDetail` (Spring). El mensaje legible viaja en `detail`.
Cuando el error pertenece a un campo del formulario, el mismo texto **también** viene
en `errors` (mapa `campo → mensaje`) con la clave en **`snake_case`**.

### Ejemplo 1: validación declarativa (campo obligatorio)

```json
POST /auth/register
{ "name": "Ana", "email": "no-valido", "password": "123" }

→ 400
{
  "detail": "Error de validación",
  "errors": {
    "last_name": "El apellido es obligatorio",
    "email": "El correo no tiene un formato válido",
    "password": "La contraseña debe tener al menos 8 caracteres"
  }
}
```

El frontend pinta `"El apellido es obligatorio"` en rojo debajo del input
`last_name`, y lo mismo con los otros dos.

### Ejemplo 2: error de negocio con campo

```json
POST /auth/change-password
{ "current_password": "incorrecta", "new_password": "nuevaClave1" }

→ 400
{
  "detail": "La contraseña actual no es correcta",
  "errors": { "current_password": "La contraseña actual no es correcta" }
}
```

Se ancla al input de contraseña actual. **Antes** iba solo a `detail` sin `errors`,
y el FE lo mostraba como toast que desaparece: el usuario no sabía cuál de los tres
campos era el problema.

### Ejemplo 3: conflicto de estado (sin campo)

```json
POST /reservations/123/cancel

→ 409
{
  "detail": "La reserva ya no puede cancelarse"
}
```

No hay `errors`, porque no pertenece a ningún input: es el estado de la reserva. El
FE muestra un toast con el texto de `detail`.

### Ejemplo 4: login fallido (deliberadamente sin campo)

```json
POST /auth/login
{ "email": "admin@hmap.com", "password": "mala" }

→ 401
{
  "detail": "Credenciales inválidas"
}
```

Tampoco lleva `errors`. Si dijera "contraseña incorrecta", revelaría que el correo
existe (permite enumerar usuarios). El mensaje genérico más el toast global es
correcto aquí.

---

## 2. Todos los errores de negocio con campo

| Endpoint | Campo | Mensaje | Cuándo aparece |
|---|---|---|---|
| `POST /auth/register` | `email` | "Ya existe una cuenta con ese correo" | Correo duplicado |
| `POST /auth/change-password` | `current_password` | "La contraseña actual no es correcta" | Contraseña actual no coincide con el hash |
| `POST /users` (admin) | `email` | "Ya existe una cuenta con ese correo" | Correo duplicado en alta de usuario interno |
| `POST /users` (admin) | `role` | "El rol debe ser RECEPCIONISTA o ADMINISTRADOR" | Se envía `CLIENTE` u otro valor inválido |
| `POST /rooms`, `PUT /rooms/:id` | `slug` | "Ya existe una habitación con ese slug" | Slug duplicado en crear o editar habitación |
| `POST /rooms`, `PUT /rooms/:id` | `status` | "Estado de habitación inválido: X" | Enum no reconocido |
| `GET /rooms/available` | `guests` | "La cantidad de huéspedes debe ser al menos 1" | Búsqueda con `guests=0` o negativo |
| `POST /reservations` | `check_in` | "La fecha de entrada no puede ser anterior a hoy" | Fechas de estancia inválidas |
| `POST /reservations` | `check_out` | "La fecha de salida debe ser posterior a la de entrada" | `check_out` ≤ `check_in` |
| `POST /reservations` | `guests` | "La habitación admite hasta N huéspedes" | Capacidad excedida |
| `POST /reservations` | `check_in` | "La habitación ya no está disponible en esas fechas." | Solape de reservas |
| `POST /reservations/:id/cancel` | `reason` | "El motivo de la cancelación es obligatorio" | Cancelación interna sin motivo |

Los **conflictos de estado** (check-in de una reserva no confirmada, cancelación
fuera de plazo, quitarse el propio rol de admin) **no llevan campo**: no pertenecen
a un input, van a toast.

---

## 3. Helper del frontend (`shared/api/client.ts`)

Reemplaza el `catch` manual de cada formulario. Decide solo: si hay `errors` los
ancla con `form.setFields`, si no lo hay muestra el `detail` como `message.error`.

```ts
import type { FormInstance } from 'antd'

/**
 * Aplica el error de la API al formulario o lo muestra como aviso.
 *
 * - Si el error tiene `errors`, ancla cada mensaje al input correspondiente.
 * - Si no, muestra `detail` como toast con el mensaje por defecto de respaldo.
 *
 * Uso:
 *   try {
 *     await api.post('/users', body)
 *   } catch (error) {
 *     applyApiError(error, form, 'No se pudo crear el usuario.')
 *   }
 */
export function applyApiError(
  error: unknown,
  form: FormInstance,
  fallbackMessage: string
): void {
  const fieldErrors = getFieldErrors(error)

  if (Object.keys(fieldErrors).length > 0) {
    // Ancla cada error al input correspondiente; el input se pone en rojo y
    // muestra el mensaje debajo. Antd enfoca automáticamente el primer campo
    // con error (el orden es el de declaración en el DTO del backend).
    form.setFields(
      Object.entries(fieldErrors).map(([name, msg]) => ({
        name,
        errors: [msg],
      }))
    )
  } else {
    // No es atribuible a un campo (conflicto de estado, autoprotección, etc.):
    // el aviso global es la UX correcta.
    message.error(getErrorMessage(error, fallbackMessage))
  }
}
```

`getFieldErrors` y `getErrorMessage` ya existen en `client.ts` y funcionan con el
nuevo contrato. No hay que cambiar nada en ellos.

---

## 4. Formularios a actualizar (12 en total)

Reemplaza el bloque `catch` actual por una llamada a `applyApiError`. **No tocar el
login**: ahí el toast global es deliberado por seguridad.

| Archivo | Formulario | Qué actualizar |
|---|---|---|
| `src/features/auth/pages/RegisterPage/RegisterPage.tsx` | Registro | Reemplazar `catch` con `applyApiError(error, form, 'No se pudo crear la cuenta.')` |
| `src/features/client/pages/ChangePasswordPage/ChangePasswordPage.tsx` | Cambio de contraseña | `applyApiError(error, form, 'No se pudo actualizar la contraseña.')` |
| `src/features/client/pages/ProfilePage/ProfilePage.tsx` | Perfil propio | `applyApiError(error, form, 'No se pudo actualizar el perfil.')` |
| `src/features/client/pages/ConfirmReservationPage/ConfirmReservationPage.tsx` | Confirmar reserva | `applyApiError(error, form, 'No se pudo confirmar la reserva.')` |
| `src/features/client/pages/EditReservationPage/EditReservationPage.tsx` | Editar reserva (cliente) | `applyApiError(error, form, 'No se pudo actualizar la reserva.')` |
| `src/features/auth/pages/ResetPasswordPage/ResetPasswordPage.tsx` | Restablecer contraseña | `applyApiError(error, form, 'No se pudo restablecer la contraseña.')` |
| `src/features/reception/components/ManualReservationModal/ManualReservationModal.tsx` | Reserva manual | Ya usa el patrón completo con `getFieldErrors`; **verificar** que funcione con el nuevo contrato |
| `src/features/reception/components/EditReservationModal/EditReservationModal.tsx` | Editar reserva (recepción) | `applyApiError(error, form, 'No se pudo actualizar la reserva.')` |
| `src/features/reception/components/RoomFormModal/RoomFormModal.tsx` | Crear/editar habitación | Ya usa `getFieldErrors`; **verificar** |
| `src/features/admin/components/CreateUserModal/CreateUserModal.tsx` | Alta de usuario | Ya usa `getFieldErrors`; **verificar** |
| `src/features/admin/components/EditUserModal/EditUserModal.tsx` | Editar usuario | Ya usa `getFieldErrors`; **verificar** |
| `src/features/home/components/SearchBar/SearchBar.tsx` | Búsqueda de disponibilidad | Validar si hace submit a la API; si es solo navegación, no tocar |

Los modales del panel admin y recepción **ya** tienen el patrón completo
(`getFieldErrors` + `form.setFields` vs `message.error`). El helper `applyApiError`
unifica esa lógica en una sola función. Verificar que `form.setFields` reciba
directamente los nombres de campo del backend (`last_name`, `current_password`, etc.)
sin traducir manualmente.

---

## 5. Checklist de implementación

1. **Añadir el helper** `applyApiError` a `src/shared/api/client.ts`.
2. **Importar** `applyApiError` en cada uno de los 12 archivos listados arriba.
3. **Reemplazar** el bloque `catch` de cada formulario:
   ```ts
   // ANTES
   } catch (error) {
     message.error(getErrorMessage(error, 'No se pudo crear el usuario.'))
   }

   // DESPUÉS
   } catch (error) {
     applyApiError(error, form, 'No se pudo crear el usuario.')
   }
   ```
4. **Probar** los 6 casos clave contra la API arrancada:
   - Registro con correo duplicado → error anclado al input `email`.
   - Cambio de contraseña con actual incorrecta → anclado a `current_password`.
   - Alta de usuario con correo duplicado → anclado a `email`.
   - Alta de usuario con rol `CLIENTE` → anclado a `role`.
   - Crear habitación con slug duplicado → anclado a `slug`.
   - Reserva con fechas solapadas → anclado a `check_in`.
5. **Probar** un caso de aviso global (sin campo):
   - Admin intenta desactivar su propia cuenta → toast, **no** anclaje.
6. **Verificar** que el login siga mostrando el toast (no anclar al campo).

---

## 6. Ejemplo completo: antes y después

### Antes (`ChangePasswordPage.tsx`)

```tsx
const onFinish = async (values: ChangePasswordForm) => {
  setSaving(true)
  try {
    await profileApi.changePassword({
      current_password: values.current_password,
      new_password: values.new_password,
    })
    message.success('Contraseña actualizada con éxito.')
    navigate('/panel/perfil')
  } catch (error) {
    // TODO: el toast desaparece y el usuario no sabe cuál campo falló
    message.error(getErrorMessage(error, 'No se pudo actualizar la contraseña.'))
  } finally {
    setSaving(false)
  }
}
```

### Después

```tsx
import { applyApiError } from '@/shared/api/client'  // añadir import

const onFinish = async (values: ChangePasswordForm) => {
  setSaving(true)
  try {
    await profileApi.changePassword({
      current_password: values.current_password,
      new_password: values.new_password,
    })
    message.success('Contraseña actualizada con éxito.')
    navigate('/panel/perfil')
  } catch (error) {
    // El error se ancla al input de current_password en rojo, no desaparece.
    applyApiError(error, form, 'No se pudo actualizar la contraseña.')
  } finally {
    setSaving(false)
  }
}
```

---

## 7. Notas de UX

- **No deshabilitar el submit mientras haya errores anclados.** Antd ya pinta el
  input en rojo y muestra el mensaje debajo; el usuario puede corregir y reenviar.
- **Limpiar los errores al cambiar el valor.** Antd lo hace automáticamente: en
  cuanto el usuario edita un input con error, el mensaje desaparece.
- **Orden de foco.** Cuando hay varios campos con error, Antd enfoca el primero en
  el orden de declaración del DTO (el backend devuelve un `LinkedHashMap` que
  conserva ese orden).
- **Login.** El toast global es **correcto**: no debe revelar si falló el correo o
  la contraseña (evita enumerar usuarios). Mantener `message.error` tal cual.
- **Autoprotección del admin.** El botón de *suspender* sobre la propia cuenta debe
  estar **deshabilitado** en la UI (compara el `id` con el de `GET /auth/me`). El
  409 del backend es red de seguridad, no un error que el usuario deba ver.

---

## 8. Verificación del contrato (ya ejecutada en el BE)

Los siguientes casos fueron verificados contra la API real arrancada con MySQL:

```bash
# validación declarativa → errors con snake_case
POST /auth/register {"name":"Test","email":"no-valido","password":"123"}
→ 400 {"errors": {"last_name": "...", "email": "...", "password": "..."}}

# contraseña actual incorrecta
POST /auth/change-password {"current_password":"mala","new_password":"nuevaClave1"}
→ 400 {"errors": {"current_password": "La contraseña actual no es correcta"}}

# correo duplicado en alta admin
POST /users {"email":"admin@hmap.com", ...}
→ 409 {"errors": {"email": "Ya existe una cuenta con ese correo"}}

# rol no asignable
POST /users {"role":"CLIENTE", ...}
→ 400 {"errors": {"role": "El rol debe ser RECEPCIONISTA o ADMINISTRADOR"}}

# autoprotección (sin campo, aviso)
PATCH /users/7/active {"active":false}
→ 409 {"detail": "No puedes desactivar tu propia cuenta"}  // sin errors

# login fallido (sin campo, aviso)
POST /auth/login {"email":"admin@hmap.com","password":"mala"}
→ 401 {"detail": "Credenciales inválidas"}  // sin errors
```

Todos confirman el contrato: los errores atribuibles a un campo llevan `errors`, los
avisos globales solo `detail`.

---

## 9. Pendientes (opcional, fuera de alcance inmediato)

- **Traducir errores de enum.** `"Estado de habitación inválido: XYZ"` podría
  prevenirse con un `<Select>` en lugar de `<Input>` para el estado. O bien, validar
  en el FE antes de enviar.
- **Validación anticipada de solapes.** Antes de enviar la reserva, consultar
  `/rooms/available` y deshabilitar fechas ocupadas en el DatePicker. Reduce errores
  409 de solape, pero no los elimina (otra reserva puede entrar entre la búsqueda y
  el submit). El anclaje sigue siendo la red de seguridad.
- **Errores anidados.** Si un día los DTOs anidan objetos (`guest.email`), el
  backend ya traduce cada segmento a `snake_case` (`guest.email` → `guest.email`, no
  cambia porque ambos segmentos son minúsculas; `guest.lastName` → `guest.last_name`).
  Antd soporta rutas anidadas en `form.setFields([{ name: ['guest', 'last_name'], ... }])`.

---

**Resumen:** el backend distingue errores de campo de avisos globales, y los nombres
van en `snake_case`. El helper `applyApiError` decide entre anclar y notificar. Los
12 formularios listados deben adoptar ese helper en su `catch`. El login se queda con
el toast. La autoprotección del admin debe prevenirse en la UI deshabilitando el
botón.
