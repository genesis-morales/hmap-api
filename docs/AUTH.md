# Módulo de Autenticación y Seguridad

Documentación del módulo de autenticación del backend del Hotel Manuel Antonio Park.
Cubre las historias **HU-003** (registro), **HU-004** (login), **HU-005** (solicitar
recuperación) y **HU-006** (restablecer contraseña), además de los **roles** del sistema.

---

## 1. Lo que se implementó

### Roles
Tres roles sembrados vía migración: **ADMIN**, **CLIENTE**, **RECEPCIONISTA**.
- Tabla `roles` con los 3 registros (seed en `V1__init_auth.sql`).
- Las autoridades de Spring Security se exponen como `ROLE_<NOMBRE>` (ej. `ROLE_CLIENTE`).

### Endpoints (`/auth`)

| Método | Ruta                    | Descripción                                   | Respuesta            |
|--------|-------------------------|-----------------------------------------------|----------------------|
| POST   | `/auth/register`        | Registra cuenta de huésped (rol CLIENTE)      | `201` + `TokenDTO`   |
| POST   | `/auth/login`           | Inicia sesión                                 | `200` + `TokenDTO`   |
| POST   | `/auth/forgot-password` | Solicita enlace de recuperación (envía correo)| `200` (sin cuerpo)   |
| POST   | `/auth/reset-password`  | Restablece la contraseña con el token         | `200` (sin cuerpo)   |

Ejemplos de cuerpo:

```jsonc
// POST /auth/register
{ "name": "Ana Pérez", "email": "ana@mail.com", "password": "claveSegura1" }

// POST /auth/login
{ "email": "ana@mail.com", "password": "claveSegura1" }

// POST /auth/forgot-password
{ "email": "ana@mail.com" }

// POST /auth/reset-password
{ "token": "uuid-recibido-por-correo", "newPassword": "nuevaClave1" }
```

`TokenDTO` → `{ "token": "<jwt>" }`. Se envía en las peticiones protegidas con el header
`Authorization: Bearer <jwt>`.

### Seguridad
- **JWT stateless** (librería `java-jwt` de Auth0), emisor `API HMAP`, expiración 2 h.
- Contraseñas cifradas con **BCrypt**.
- `SecurityFilter` valida el token en cada petición y autentica al usuario.
- Rutas públicas: `/auth/**` y Swagger; el resto requiere autenticación.

### Recuperación de contraseña
- Token de un solo uso (UUID) guardado en `password_reset_tokens` con expiración (30 min por defecto).
- Se envía por **correo SMTP** un enlace al frontend: `<FRONTEND_RESET_URL>?token=<uuid>`.
- Por seguridad, `forgot-password` responde igual exista o no el correo (evita enumeración de usuarios).
- El token se marca como usado tras el reseteo y deja de ser válido.

### Otros
- **Swagger** habilitado con botón *Authorize* (Bearer JWT).
- Manejo de errores centralizado (`GlobalExceptionHandler`): validaciones → `400`,
  reglas de negocio → `400`, credenciales inválidas → `401`.
- Esquema de base de datos versionado con **Flyway** (`V1__init_auth.sql`).

### Estructura de archivos relevante
```
src/main/java/com/hmap/backend/
├── auth/
│   ├── controller/AuthController.java
│   ├── dto/{LoginRequest, RegisterRequest, ForgotPasswordRequest, ResetPasswordRequest, TokenDTO}.java
│   ├── entity/{Auth, PasswordResetToken}.java
│   ├── repository/{AuthRepository, PasswordResetTokenRepository}.java
│   └── service/{AuthService, TokenService}.java
├── role/
│   ├── entity/Role.java
│   ├── enums/RoleName.java
│   └── repository/RoleRepository.java
├── security/{SecurityConfig, SecurityFilter, CustomUserDetailsService}.java
├── notification/MailService.java
├── config/OpenApiConfig.java
└── excepton/{GlobalExceptionHandler, BadRequestException, ResourceNotFoundException}.java

src/main/resources/db/migration/V1__init_auth.sql
```

---

## 2. Lo que queda por hacer (de tu parte)

### Imprescindible para que funcione
1. **Ejecutar la migración.** Arranca la app (`./mvnw spring-boot:run`) y Flyway aplica
   `V1__init_auth.sql` automáticamente, o ejecuta `./mvnw flyway:migrate`.
   Verifica que se crearon `roles` (3 filas), `users` y `password_reset_tokens`.
2. **Configurar el SMTP (Mailtrap para desarrollo).** Crea una cuenta gratis en
   [mailtrap.io](https://mailtrap.io) y exporta las variables (o ponlas en `application.properties`):
   ```bash
   export MAIL_USERNAME=tu_usuario_mailtrap
   export MAIL_PASSWORD=tu_password_mailtrap
   ```
   Host (`sandbox.smtp.mailtrap.io`) y puerto (`2525`) ya están por defecto.
   Para producción solo cambias estas credenciales por las de tu proveedor (Brevo, SES, etc.).

### Recomendado
3. **Cambiar el secret del JWT.** Por defecto es `12345678`. En producción exporta `JWT_SECRET`
   con un valor largo y aleatorio.
4. **Definir la URL del frontend de reseteo.** Por defecto apunta a
   `http://localhost:5173/reset-password`. Ajusta con `FRONTEND_RESET_URL` cuando exista el front.

### Variables de entorno disponibles
| Variable             | Por defecto                          | Uso                                      |
|----------------------|--------------------------------------|------------------------------------------|
| `JWT_SECRET`         | `12345678`                           | Secret de firma del JWT                  |
| `MAIL_HOST`          | `sandbox.smtp.mailtrap.io`           | Host SMTP                                |
| `MAIL_PORT`          | `2525`                               | Puerto SMTP                              |
| `MAIL_USERNAME`      | *(vacío)*                            | Usuario SMTP                             |
| `MAIL_PASSWORD`      | *(vacío)*                            | Contraseña SMTP                          |
| `MAIL_FROM`          | `no-reply@hmap.com`                  | Remitente de los correos                 |
| `FRONTEND_RESET_URL` | `http://localhost:5173/reset-password` | Base del enlace de recuperación        |
| `RESET_TOKEN_MINUTES`| `30`                                 | Minutos de validez del token de reseteo  |

---

## 3. Cómo probar

1. `./mvnw spring-boot:run`
2. Abre Swagger: `http://localhost:8080/swagger-ui/index.html`
3. Flujo sugerido:
   - `POST /auth/register` → devuelve token; el usuario queda con rol CLIENTE.
   - `POST /auth/login` → token. Pulsa *Authorize* y pega el token para probar rutas protegidas.
   - `POST /auth/forgot-password` → revisa la bandeja de **Mailtrap**: llega el correo con el enlace + token.
   - `POST /auth/reset-password` con ese token → `200`. El login con la nueva contraseña funciona;
     reutilizar el token devuelve `400`.

Tests automatizados:
```bash
./mvnw test
```

---

## 4. Pendientes / fuera de alcance actual
- **Logout (HU-004):** con JWT stateless el cierre de sesión es del lado del cliente (descartar el token).
  No hay endpoint salvo que se quiera implementar una blacklist de tokens.
- **Correos transaccionales restantes** (HU-035 a HU-037): confirmación/cancelación de reserva, etc.
- **Cambio de contraseña desde el perfil** (HU-015) y gestión de usuarios por el admin (HU-030 a HU-034).
- Renombrar el paquete `excepton` → `exception` (cosmético).
