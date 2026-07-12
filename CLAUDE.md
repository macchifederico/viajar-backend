# ViajAR Backend — Especificaciones Técnicas

> Documento de producto y negocio: ver repo `viajar-frontend/docs/producto.md`
> Especificación técnica del backend (endpoints, modelo de datos, reglas de negocio): [docs/backend-spec.md](docs/backend-spec.md)

Este repo era parte del monorepo `viajarapp` y se separó para manejarse de forma
independiente. El frontend mobile vive ahora en el repo `viajar-frontend`.

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

## API REST, modelo de datos y reglas de negocio

Ver [docs/backend-spec.md](docs/backend-spec.md) — es la fuente de verdad de endpoints,
entidades, contratos de request/response, validaciones, códigos de error y eventos
WebSocket, con su estado de implementación (✅ implementado / 🚧 en desarrollo / ⏳ pendiente).
Trabajamos con **spec-driven development**: ese documento se escribe/actualiza antes de
implementar cada fase nueva.

## Variables de entorno necesarias
```
DATABASE_URL=jdbc:postgresql://...   # formato JDBC (datasource de Spring)
DB_USER=...
DB_PASS=...
REDIS_URL=redis://...
JWT_SECRET=...
JWT_REFRESH_SECRET=...
MP_ACCESS_TOKEN=...          # Mercado Pago
MP_PUBLIC_KEY=...
FIREBASE_CREDENTIALS=...     # FCM push notifications
TWILIO_ACCOUNT_SID=...
TWILIO_AUTH_TOKEN=...
TWILIO_PHONE=...
GOOGLE_MAPS_KEY=...
AWS_S3_BUCKET=...
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
PRICE_PER_KM_ARS=150         # Precio base por km en ARS (actualizable)
ENVIRONMENT=                 # "local" hace que S3Service guarde imágenes en base64 en la tabla stored_images en vez de subirlas a S3
```

## Convenciones de código
- Java 21, Spring Boot 3.5.1, build con Maven (`pom.xml`)
- Arquitectura en capas: Controller → Service → Repository (Spring Data JPA)
- Validación con Bean Validation (`jakarta.validation`) en los DTOs de request
- Errores con `AppException` + `GlobalExceptionHandler` (`@ControllerAdvice`)
- Persistencia: Hibernate/JPA (`ddl-auto: none`) + Flyway para migraciones
- Tests con JUnit 5 + Spring Boot Test (`@WebMvcTest`, Testcontainers para Postgres)

## General
- Commits en español, convencional: `feat:`, `fix:`, `chore:`
- PRs pequeños, una funcionalidad por PR
- No pushear .env nunca
