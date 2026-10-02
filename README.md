# HMAP Backend API

Sistema de gestión hotelera para Hotel Manuel Antonio Park.

## Descripción

Backend desarrollado con Spring Boot que proporciona una API RESTful para la gestión integral de un hotel, incluyendo autenticación, gestión de habitaciones, reservas y panel administrativo.

## Tecnologías

- **Java 17**
- **Spring Boot 3.2.5**
- **PostgreSQL** con Flyway para migraciones
- **Spring Security** con JWT (Auth0)
- **Spring Mail** para notificaciones por correo
- **OpenAPI 3** (Swagger) para documentación de API
- **Lombok** para reducir código boilerplate

## Requisitos previos

- JDK 17
- PostgreSQL 12+
- Maven 3.8+ (o usar el wrapper incluido)

## Configuración

1. Clonar el repositorio

2. Copiar el archivo de configuración:
```bash
cp .env.example .env
```

3. Configurar las variables de entorno en `.env`:
   - Conexión a base de datos PostgreSQL
   - Configuración SMTP para envío de emails
   - Secret para JWT
   - URLs del frontend para CORS

## Ejecución

### Desarrollo local

```bash
./mvnw spring-boot:run
```

### Producción (fat jar)

```bash
./mvnw clean package
java -jar target/hmap-0.0.1-SNAPSHOT.jar
```

## Endpoints principales

La API está documentada con OpenAPI/Swagger:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

### Módulos

- **Autenticación** (E1): Registro, login, recuperación de contraseña
- **Reservas** (E2): Búsqueda de disponibilidad, creación de reservas
- **Recepción** (E3): Check-in, check-out, gestión de estancias
- **Administración** (E4): CRUD de habitaciones, gestión de usuarios, reportes

## Base de datos

El modelo de datos está normalizado (3FN) y se documenta en `docs/MODELO-DATOS.md`.

Las migraciones se gestionan con Flyway en `src/main/resources/db/migration/`.

## Testing

```bash
./mvnw test
```

## Despliegue

El proyecto está configurado para despliegue en Render con Docker. Ver `render.yaml` y `Dockerfile`.

## Estructura del proyecto

```
src/main/java/com/hmap/
├── config/          # Configuración (Security, CORS, Email)
├── controller/      # Endpoints REST
├── dto/             # Data Transfer Objects
├── model/           # Entidades JPA
├── repository/      # Repositorios Spring Data
├── service/         # Lógica de negocio
└── util/            # Utilidades (JWT, validators)
```

## Health check

El endpoint de salud de Actuator está disponible en:
```
GET /actuator/health
```

## Autor

Génesis Morales

## Licencia

Proyecto académico
