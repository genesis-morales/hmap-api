# Despliegue en Neon (PostgreSQL)

Este documento describe cómo configurar y desplegar la API de HMAP usando PostgreSQL en Neon.

## Cambios realizados

### 1. Dependencias (pom.xml)
- ❌ Removido: `mysql-connector-j` y `flyway-mysql`
- ✅ Agregado: `postgresql` y `flyway-core`

### 2. Configuración (application.properties)
- Actualizado el driver JDBC a PostgreSQL
- Configuradas variables de entorno para la conexión:
  - `DATABASE_URL`: URL de conexión JDBC
  - `DATABASE_USERNAME`: Usuario de la base de datos
  - `DATABASE_PASSWORD`: Contraseña de la base de datos

### 3. Migraciones SQL
Todas las migraciones fueron adaptadas de MySQL a PostgreSQL:

#### Cambios principales:
- `AUTO_INCREMENT` → `BIGSERIAL`
- `DATETIME` → `TIMESTAMP`
- `ALTER TABLE ... ADD COLUMN ... AFTER` → `ALTER TABLE ... ADD COLUMN` (PostgreSQL no soporta AFTER)
- `MODIFY COLUMN` → `ALTER COLUMN ... SET NOT NULL`

## Configuración en Neon

### 1. Crear proyecto en Neon
1. Ve a [neon.tech](https://neon.tech) y crea una cuenta
2. Crea un nuevo proyecto
3. Crea una base de datos llamada `hmap_db`
4. Obtén la cadena de conexión

### 2. Cadena de conexión
Neon te proporciona una cadena de conexión como esta:
```
postgresql://user:password@ep-xxx-xxx.us-east-2.aws.neon.tech/hmap_db?sslmode=require
```

Conviértela al formato JDBC para Spring Boot:
```
jdbc:postgresql://ep-xxx-xxx.us-east-2.aws.neon.tech/hmap_db?sslmode=require
```

### 3. Variables de entorno

Crea un archivo `.env` basado en `.env.example`:

```bash
cp .env.example .env
```

Edita `.env` y configura:

```properties
# Ejemplo con Neon
DATABASE_URL=jdbc:postgresql://ep-xxx-xxx.us-east-2.aws.neon.tech/hmap_db?sslmode=require
DATABASE_USERNAME=tu-usuario-neon
DATABASE_PASSWORD=tu-password-neon

# Resto de variables...
JWT_SECRET=tu-secreto-jwt-seguro
MAIL_HOST=sandbox.smtp.mailtrap.io
MAIL_PORT=2525
MAIL_USERNAME=tu-usuario-mailtrap
MAIL_PASSWORD=tu-password-mailtrap
MAIL_FROM=no-reply@hmap.com
FRONTEND_RESET_URL=https://tu-frontend.com/reset-password
IMAGES_BASE_URL=https://res.cloudinary.com/tu-cloud/image/upload
```

### 4. Ejecutar migraciones

Flyway ejecutará las migraciones automáticamente al iniciar la aplicación:

```bash
./mvnw spring-boot:run
```

Si quieres ejecutar las migraciones manualmente primero:

```bash
./mvnw flyway:migrate
```

### 5. Verificar la conexión

La aplicación debería iniciar sin errores y verás en los logs:

```
Flyway Community Edition x.x.x
Successfully validated X migrations
Successfully applied X migrations
```

## Desarrollo local con PostgreSQL

Si quieres desarrollar localmente con PostgreSQL antes de usar Neon:

### Opción 1: Docker
```bash
docker run --name postgres-hmap -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=hmap_db -p 5432:5432 -d postgres:16
```

### Opción 2: PostgreSQL instalado localmente
1. Instala PostgreSQL 16+ (o 15+)
2. Crea la base de datos:
```sql
CREATE DATABASE hmap_db;
```
3. Configura `.env`:
```properties
DATABASE_URL=jdbc:postgresql://localhost:5432/hmap_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
```

## Usuarios por defecto

Después de ejecutar las migraciones, tendrás estos usuarios de prueba:

### Administrador
- Email: `admin@hmap.com`
- Contraseña: `admin123`

### Recepcionista
- Email: `recepcion@hmap.com`
- Contraseña: `recepcion123`

**⚠️ IMPORTANTE:** Cambia estas contraseñas en producción.

## Notas importantes

### SSL/TLS en Neon
Neon **requiere** SSL. Asegúrate de incluir `?sslmode=require` en tu URL de conexión.

### Pooling de conexiones
Neon tiene límites de conexiones según tu plan. Considera configurar el pool de conexiones en `application.properties`:

```properties
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.minimum-idle=2
```

### Backups
Neon hace backups automáticos, pero considera exportar datos críticos periódicamente.

## Troubleshooting

### Error: "password authentication failed"
- Verifica que el usuario y contraseña sean correctos
- Verifica que las variables de entorno estén configuradas

### Error: "SSL connection required"
- Agrega `?sslmode=require` a tu DATABASE_URL

### Error: "Connection timeout"
- Verifica que tu IP esté permitida en Neon
- Verifica la URL del endpoint de Neon

### Migraciones fallan
- Verifica que la base de datos esté vacía si es la primera ejecución
- Revisa los logs de Flyway para ver qué migración falla
- Asegúrate de que la sintaxis SQL sea compatible con PostgreSQL

## Recursos

- [Documentación de Neon](https://neon.tech/docs)
- [Spring Boot con PostgreSQL](https://spring.io/guides/gs/accessing-data-postgresql/)
- [Flyway con PostgreSQL](https://flywaydb.org/documentation/database/postgresql)
