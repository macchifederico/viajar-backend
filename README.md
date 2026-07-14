# ViajAR Backend

API REST del backend de ViajAR (carpooling/viajes compartidos), construida con Java 21 y
Spring Boot.

> - Documento de producto y negocio: repo [`viajar-frontend`](https://github.com/macchifederico/viajar-frontend), `docs/producto.md`
> - Especificación técnica (endpoints, modelo de datos, reglas de negocio): [docs/backend-spec.md](docs/backend-spec.md)

## Stack técnico

| Capa | Tecnología |
|------|-----------|
| Backend | Java 21 + Spring Boot 3.5.1 (Maven) |
| Base de datos | PostgreSQL + PostGIS (geo) |
| Cache / RT | Redis (posiciones GPS, sesiones) |
| Pagos | Mercado Pago API |
| Push notifications | Firebase FCM |
| SMS / OTP | Twilio |
| Storage | AWS S3 (fotos de perfil y vehículos) |
| Mapas | Google Maps Platform |
| Infra | Docker + Railway |
| CI | GitHub Actions |

## Requisitos

- Java 21
- Maven
- Docker (para levantar Postgres+PostGIS y Redis localmente)

## Cómo correrlo localmente

1. Levantar la base de datos y Redis:
   ```bash
   docker compose -f infra/docker-compose.yml up -d
   ```
2. Copiar `.env.example` a `.env` y completar las variables necesarias (ver abajo). Para
   desarrollo local alcanza con dejar en blanco las credenciales de servicios externos
   (Mercado Pago, Twilio, Firebase, AWS) y setear `ENVIRONMENT=local`, lo que hace que las
   imágenes se guarden en base64 en la tabla `stored_images` en vez de subirse a S3 real.
3. Levantar la app:
   ```bash
   mvn spring-boot:run
   ```
   Por defecto queda escuchando en `http://localhost:8085` (ver `application.properties`).
4. Las migraciones de Flyway (`src/main/resources/db/migration`) corren automáticamente al
   arrancar la app.

## Variables de entorno

Ver [.env.example](.env.example) para el listado completo. Las más relevantes para desarrollo
local:

```
DATABASE_URL=jdbc:postgresql://localhost:5432/viajarapp
DB_USER=...
DB_PASS=...
REDIS_URL=redis://localhost:6379
JWT_SECRET=...
JWT_REFRESH_SECRET=...
ENVIRONMENT=local   # guarda imágenes en base64 en vez de subir a S3 real
PRICE_PER_KM_ARS=150
```

El resto (Mercado Pago, Firebase, Twilio, Google Maps, AWS S3) son necesarias para integrar
esos servicios reales, pero no bloquean levantar el backend en local.

## Tests

```bash
mvn test
```

## Estructura del repo

```
/
├── src/
│   ├── main/java/ar/com/viajar/   # controller / service / repository / domain / dto
│   └── test/java/ar/com/viajar/
├── infra/
│   └── docker-compose.yml         # Postgres+PostGIS y Redis para desarrollo local
└── docs/
    └── backend-spec.md            # fuente de verdad de la API
```

## Convenciones de código

- Arquitectura en capas: Controller → Service → Repository (Spring Data JPA)
- Validación con Bean Validation (`jakarta.validation`) en los DTOs de request
- Errores con `AppException` + `GlobalExceptionHandler` (`@ControllerAdvice`)
- Persistencia: Hibernate/JPA (`ddl-auto: none`) + Flyway para migraciones
- Tests con JUnit 5 + Spring Boot Test (`@WebMvcTest`, Testcontainers para Postgres)
- Commits en español, convencional: `feat:`, `fix:`, `chore:`
