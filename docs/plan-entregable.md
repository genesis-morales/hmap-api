# Plan de Entregables
**Proyecto:** Herramienta web para la gestión de reservas y recepción — Hotel Manuel Antonio Park (HMAP)

Cuatro entregables. Cada uno agrupa historias de usuario (HU) y requerimientos funcionales (RF) que se completan de punta a punta (API + Frontend), de modo que al cierre de cada entrega hay funcionalidad demostrable.

Convención: ver contrato de endpoints en [api-contrato.md](./api-contrato.md).

---

## Entregable 1 — Portal Público + Autenticación ✅ (completado)

| Área | Alcance |
|---|---|
| **FE** | Portal público: página principal, secciones (habitaciones, amenidades, galería, testimonios, ubicación), detalle de habitación. Auth: registro, login/logout, recuperar y restablecer contraseña, contexto de sesión (JWT). |
| **API** | Registro, login (JWT), `me`, forgot/reset password. Cifrado BCrypt. Correo de recuperación. |

**HU:** HU-001, HU-002, HU-003, HU-004, HU-005, HU-006, HU-038
**RF:** RF-001, RF-002, RF-003, RF-004, RF-005
**RNF cubiertos:** RNF-007 (BCrypt), RNF-008 (JWT)

---

## Entregable 2 — Portal del Cliente + Motor de Reservas

El corazón del sistema: el cliente puede buscar disponibilidad, reservar y gestionar sus reservas. Incluye la base de datos de habitaciones (necesaria para calcular disponibilidad) aunque su CRUD administrativo llega en el Entregable 3.

> **Estado:** API ✅ completada (ver [E2-RESERVAS.md](modulos/E2-RESERVAS.md)) · FE ✅ completado
> (panel cliente, buscador compartido, flujo de reserva y gestión de reservas propias).

| Área | Alcance |
|---|---|
| **FE** | Layout del panel cliente + ruta protegida por rol. Inicio del portal (HU-007). Perfil y cambio de contraseña. Buscador de disponibilidad **compartido** entre portal público (RF-006) y panel cliente (HU-008), con persistencia de la selección previa al pasar por login (RNF-006). Flujo de confirmación de reserva. Listado, detalle, edición y cancelación de reservas propias. |
| **API** | Actualizar perfil y cambiar contraseña. Habitaciones (lectura, servidas desde BD — migrar el catálogo hoy hardcodeado en el FE). Disponibilidad por fechas/huéspedes. CRUD de reservas del cliente con reglas de negocio (validación de solapamiento, límites de tiempo para editar/cancelar, estados de reserva). Correos de confirmación y cancelación. |

**HU:** HU-007, HU-008, HU-009, HU-010, HU-011, HU-012, HU-013, HU-014, HU-015, HU-035, HU-036
**RF:** RF-006, RF-007, RF-008, RF-009, RF-010 (+ parte de RF-016)
**RNF foco:** RNF-006 (persistencia de selección), RNF-002/003 (usabilidad y responsive del panel)

**Dependencias clave:**
- El endpoint de disponibilidad exige que las habitaciones vivan en la BD → la migración del catálogo se hace aquí, no en el E3.
- Las políticas de edición/cancelación (plazos) se definen y validan en la API; el FE solo refleja `can_edit` / `can_cancel`.

---

## Entregable 3 — Panel de Recepcionista

Operación interna diaria: ocupación, calendario, check-in/out, reservas manuales y mantenimiento del inventario de habitaciones.

> **Estado:** API ✅ completada (ver [E3-RECEPCION.md](modulos/E3-RECEPCION.md)) · FE ✅ completado
> (rama `feat/v3-reception`: layout interno, ocupación, calendario, check-in/out, reservas e inventario).

| Área | Alcance |
|---|---|
| **FE** | Layout del panel interno (compartible con el panel admin del E4). Dashboard de ocupación en tiempo real. Calendario de reservas (mensual/diario). Lista de check-ins/check-outs del día + registro de check-in/out. Tabla global de reservas con búsqueda y filtros; crear/editar/cancelar reserva manual. CRUD de habitaciones y cambio de estado (disponible/ocupada/mantenimiento). |
| **API** | Métricas de ocupación. Consulta de reservas global (filtros por nombre, id, fechas). Reservas manuales (creación a nombre de un cliente). Check-in/check-out con transición de estado de habitación. CRUD completo de habitaciones + estados, con regla de no eliminar habitaciones con transacciones activas. Correo de reserva manual. |

**HU:** HU-016 → HU-029, HU-037
**RF:** RF-011, RF-012, RF-013, RF-014 (+ parte de RF-016)
**RNF foco:** RNF-004 (rendimiento del dashboard/calendario)

**Dependencias clave:** requiere el motor de reservas del E2 (misma entidad, se agregan estados operativos: check-in, check-out).

---

## Entregable 4 — Panel de Administrador + Cierre

Gestión de usuarios internos, control de acceso por roles consolidado y cierre de calidad del sistema completo.

> **Estado:** API ✅ completada (ver [E4-ADMIN.md](modulos/E4-ADMIN.md)) · FE ⏳ pendiente —
> es el **único frente de trabajo abierto** del proyecto: falta el módulo `features/admin`
> y las rutas `/panel-admin/**`. Contrato listo para consumir en
> [E4-ADMIN.md § 4](modulos/E4-ADMIN.md#4-contrato-de-datos-json-para-el-frontend).

| Área | Alcance |
|---|---|
| **FE** | Panel admin: listado de usuarios, crear/editar usuario interno, asignación de rol, activar/desactivar cuenta. Guardas de ruta por rol consolidadas (CLIENTE / RECEPCIONISTA / ADMINISTRADOR). Pulido responsive y de usabilidad transversal. |
| **API** | CRUD de usuarios internos, asignación de roles, activación/suspensión. Autorización por rol en todos los endpoints (revisión transversal). |
| **Cierre** | Verificación de RNFs: seguridad y control de acceso (RNF-001), usabilidad (RNF-002), responsive (RNF-003), rendimiento (RNF-004), disponibilidad (RNF-005). Pruebas de flujo completo y documentación final. |

**HU:** HU-030, HU-031, HU-032, HU-033, HU-034
**RF:** RF-015
**RNF foco:** RNF-001 y verificación final de todos los RNF

---

## Resumen de cobertura

| Entregable | HU | RF | Estado |
|---|---|---|---|
| E1 — Público + Auth | 001–006, 038 | 001–005 | ✅ Completado |
| E2 — Portal Cliente + Reservas | 007–015, 035, 036 | 006–010, 016* | ✅ Completado (API + FE) |
| E3 — Panel Recepcionista | 016–029, 037 | 011–014, 016* | ✅ Completado (API + FE) |
| E4 — Panel Admin + Cierre | 030–034 | 015 | API ✅ / **FE pendiente** |

\* RF-016 (notificaciones por correo) se reparte: cada entregable implementa los correos de sus propios flujos.

**Total: 38 HU / 16 RF — todas cubiertas.**

---

## Estado actual y trabajo restante

La **API está cerrada**: los cuatro entregables están implementados con 82 tests
unitarios en verde. Lo que queda es frontend y cierre.

### Mejoras recientes (rama `fea/admin-v4`)

1. **Anclaje de errores de validación**: Los errores atribuibles a un campo ahora
   viajan en `errors` (además de `detail`) con nombres en `snake_case`, para que
   el FE los ancle al input en rojo en lugar de mostrar un toast genérico. Guía
   completa para el FE en [FE-VALIDACION-ERRORES.md](FE-VALIDACION-ERRORES.md).

2. **Tipo de reserva**: Campo `type` (`ONLINE` | `MANUAL`) agregado a las reservas
   para distinguir su origen en el panel de recepción. Las reservas del portal
   público se marcan como `ONLINE`, las creadas por recepcionistas como `MANUAL`.

| Pendiente | Dónde | Detalle |
|---|---|---|
| Panel admin (HU-030 → HU-034) | FE | Módulo `features/admin` + rutas `/panel-admin/**` con guarda `ADMINISTRADOR`. Contrato en E4-ADMIN.md § 4. |
| Anclaje de errores | FE | Implementar helper `applyApiError` en 12 formularios (ver [FE-VALIDACION-ERRORES.md](FE-VALIDACION-ERRORES.md) § 4). |
| Columna "Origen" en reservas | FE | Agregar columna `type` en tabla de reservas del panel recepción (ver [FE-VALIDACION-ERRORES.md](FE-VALIDACION-ERRORES.md) § 10). |
| Tests E2E de admin | FE | `e2e/admin.spec.ts` los tiene comentados esperando la UI. |
| Tests de API de admin | FE | Crear `tests/api/admin-users.test.ts` (E4-ADMIN.md § 8). |
| Pulido responsive/usabilidad | FE | RNF-002 y RNF-003, transversal a los tres paneles. |
| Cierre de versión | API | Tag `v4-entregable` y merge de `fea/admin-v4` → `main`. |
