# Historias de Usuario por Módulo y Actor
**Proyecto:** Desarrollo de una herramienta web para la gestión de reservas y recepción en el Hotel Manuel Antonio Park

---

## 1. Módulo: Portal Público
### Actor: Visitante

* **HU-001: Ver página principal del hotel**
    * **Descripción:** Visualizar la página de inicio del hotel con descripción general, habitaciones disponibles, comodidades, galería, testimonios e instalaciones con mapa de ubicación.
    * **Propósito:** Conocer el hotel, sus servicios y evaluar si se ajusta a mis necesidades antes de hacer una reserva.

* **HU-002: Ver detalle de habitación**
    * **Descripción:** Ver la información completa de una habitación: fotos, nombre, descripción, capacidad, precio por noche y amenidades incluidas.
    * **Propósito:** Tomar una decisión informada antes de iniciar el proceso de reserva.

---

## 2. Módulo: Autenticación y Seguridad
### Actores: Visitante / Cliente / Recepcionista / Administrador

* **HU-003: Registrarse en el sistema**
    * **Descripción:** Crear una cuenta con mi nombre, correo electrónico y contraseña.
    * **Propósito:** Poder acceder al sistema como huésped y realizar reservas.

* **HU-004: Iniciar y cerrar sesión**
    * **Descripción:** Ingresar al sistema con mi correo y contraseña, y cerrar sesión cuando lo desee.
    * **Propósito:** Acceder a las funcionalidades del sistema de forma segura y proteger mi cuenta al terminar.

* **HU-005: Solicitar recuperación de contraseña**
    * **Descripción:** Solicitar un enlace de restauración de credenciales en caso de olvido.
    * **Propósito:** Recuperar el acceso seguro a la cuenta personal.

* **HU-006: Restablecer contraseña**
    * **Descripción:** Ingresar una nueva contraseña a través del enlace de recuperación recibido.
    * **Propósito:** Actualizar las credenciales de acceso para volver a utilizar la plataforma.

---

## 3. Módulo: Portal Cliente
### Actor: Cliente (Huésped registrado)

* **HU-007: Ver pantalla de inicio del portal**
    * **Descripción:** Visualizar la sección de bienvenida de mi portal con accesos rápidos y notificaciones relevantes.

* **HU-008: Consultar disponibilidad de habitación**
    * **Descripción:** Buscar habitaciones libres filtrando por fechas de entrada, salida y cantidad de personas.

* **HU-009: Confirmar reserva**
    * **Descripción:** Formalizar la solicitud de reserva de la habitación seleccionada para las fechas deseadas.

* **HU-010: Ver listado de reservas**
    * **Descripción:** Visualizar el historial y estado de todas las reservas asociadas a mi cuenta.

* **HU-011: Ver detalle de reserva**
    * **Descripción:** Acceder a la información específica de una reserva particular (fechas, habitación, montos).

* **HU-012: Editar reserva**
    * **Descripción:** Modificar las fechas o parámetros de una reserva pendiente bajo los límites de tiempo establecidos.

* **HU-013: Cancelar reserva**
    * **Descripción:** Dar de baja una reserva activa dentro del plazo permitido por las políticas del hotel.

* **HU-014: Ver y editar perfil**
    * **Descripción:** Visualizar y actualizar mis datos de contacto personales en la cuenta.

* **HU-015: Cambiar contraseña**
    * **Descripción:** Modificar la clave de acceso vigente desde la configuración de mi perfil.

---

## 4. Módulo: Panel Recepcionista
### Actor: Recepcionista

### Gestión de Operaciones y Dashboard
* **HU-016: Ver dashboard ocupación:** Visualizar en tiempo real el resumen analítico de habitaciones ocupadas, libres y en mantenimiento.
* **HU-017: Ver calendario de reservas:** Consultar de forma gráfica la planificación de reservas mensuales y diarias.
* **HU-018: Ver check-ins y check-outs del día:** Monitorear el listado de entradas y salidas programadas para la fecha actual.
* **HU-019: Registrar check-in y check-out:** Formalizar físicamente el ingreso o la salida del huésped, cambiando el estado operativo de la habitación.

### Gestión de Reservas Internas
* **HU-020: Crear reserva manual:** Registrar una nueva reserva en el sistema para clientes que soliciten atención por teléfono o de forma presencial.
* **HU-021: Editar reserva desde recepción:** Modificar las condiciones de reservas existentes a solicitud del cliente.
* **HU-022: Cancelar reserva desde recepción:** Anular reservas de forma justificada desde el panel interno.
* **HU-023: Buscar reserva:** Localizar registros específicos de estancias mediante filtros rápidos (nombre, identificador, fechas).
* **HU-024: Ver listado completo de reservas:** Acceder a la tabla global de reservas históricas y actuales del hotel.

### Mantenimiento de Inventario
* **HU-025: Crear habitación:** Dar de alta un nuevo espacio físico con sus respectivas características y tarifas.
* **HU-026: Editar habitación:** Actualizar detalles, fotos o precios de los tipos de habitación.
* **HU-027: Eliminar habitación:** Remover del catálogo habitaciones obsoletas que no posean registros transaccionales activos.
* **HU-028: Cambiar estado de habitación:** Modificar el estado actual (ej. pasar a mantenimiento por limpieza o reparaciones).
* **HU-029: Ver listado de habitaciones:** Listar el inventario total de habitaciones del hotel.

---

## 5. Módulo: Panel Administrador
### Actor: Administrador

* **HU-030: Crear usuario**
    * **Descripción:** Registrar nuevas cuentas de personal interno (recepcionistas u otros administradores).

* **HU-031: Editar usuario**
    * **Descripción:** Modificar la información de las cuentas de usuario existentes.

* **HU-032: Asignar rol o perfil**
    * **Descripción:** Definir los niveles de acceso y permisos específicos para cada cuenta dentro del sistema.

* **HU-033: Activar o desactivar usuario**
    * **Descripción:** Habilitar o suspender temporal/definitivamente el acceso de un usuario por razones operativas.

* **HU-034: Ver listado de usuarios**
    * **Descripción:** Visualizar de forma centralizada la nómina de usuarios y roles registrados en la plataforma.

---

## 6. Módulo Transversal: Notificaciones
### Actor: Sistema (Automatizado)

* **HU-035: Recibir correo de confirmación de reserva:** Envío automático de los detalles de la estancia al cliente tras una reserva web exitosa.
* **HU-036: Recibir correo de cancelación de reserva:** Notificación electrónica inmediata cuando se procesa la anulación de una reserva.
* **HU-037: Recibir correo de reserva manual:** Envío automático de las credenciales o detalles de reserva cuando el recepcionista la ingresa de forma interna.
* **HU-038: Recibir correo de recuperación de contraseña:** Despacho del enlace seguro temporizado para el restablecimiento de contraseñas olvidadas.