# ViajAR — Especificación Técnica del Backend

> [CLAUDE.md](../CLAUDE.md) · [Producto](producto.md)

## Qué es este documento

Este documento es la **fuente de verdad** del contrato del backend (entidades, endpoints,
reglas de negocio, validaciones, errores). Seguimos **spec-driven development**: antes de
programar una fase nueva, se escribe o actualiza la spec acá (qué endpoint, qué contrato,
qué reglas, qué casos de error) y recién después se implementa contra esa spec. El código
no es la referencia — si el código y la spec difieren, o se corrige el código o se actualiza
la spec explícitamente, pero no deben quedar desincronizados.

Cada sección de API/entidad tiene un **Estado**:
- ✅ **Implementado** — contrato ya en código, descrito tal cual existe.
- 🚧 **En desarrollo** — spec cerrada, implementación parcial o en curso.
- ⏳ **Pendiente** — spec propuesta para guiar la próxima fase, todavía sin código.

Al implementar algo marcado ⏳, actualizar su estado a ✅ y ajustar el contrato si cambió
algo durante la implementación (la spec se corrige, no se abandona).

## Estado general (fases)

| Fase | Alcance | Estado |
|------|---------|--------|
| 1 | Monorepo y estructura base | ✅ |
| 2 | Infraestructura (Postgres + PostGIS) | ✅ |
| 3 | Auth backend | ✅ |
| 4 | Perfiles y vehículos backend | ✅ |
| 5 | Auth mobile | ✅ |
| — | Onboarding de conductor (mobile + backend) | ✅ (validación de documentos pendiente, ver [producto.md](producto.md#validaciones-de-documentos--onboarding-conductor-pendiente-próximas-iteraciones)) |
| — | Migración backend Node.js → Java/Spring Boot | ✅ |
| 6 | Viajes backend (Trip, Stop, TripSegment) | ✅ |
| 7 | Búsqueda de viajes | ✅ |
| 7 | Reservas (Booking) | ✅ (create/mine/cancel; confirm-arrival/share pendientes) |
| 8 | WebSocket tracking GPS en tiempo real | ⏳ |
| 9 | Pagos (Mercado Pago escrow) | ⏳ |
| 10 | Notificaciones push (FCM) | ⏳ |
| 11 | Calificaciones (Rating) | ⏳ |

## Arquitectura y convenciones

- Capas: `Controller → Service → Repository` (Spring Data JPA). Sin lógica de negocio en el controller.
- Persistencia: Hibernate/JPA con `ddl-auto: none`; el schema lo gestiona **Flyway** (`src/main/resources/db/migration`).
- Toda entidad con PK `UUID` se mapea como `text` en Postgres (legado de Prisma), no `uuid` nativo:
  ```java
  @Id @UuidGenerator
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(columnDefinition = "text")
  private UUID id;
  ```
- Enums de dominio (`UserRole`, `DriverStatus`, `TripStatus`, `PaymentStatus`, `BookingStatus`) son
  tipos **enum nativos de PostgreSQL**; en Hibernate requieren `@JdbcType(PostgreSQLEnumJdbcType.class)`
  además de `@Enumerated(EnumType.STRING)`.
- Envoltorio de respuesta uniforme:
  - Éxito: `{ "data": T }` (`ApiResponse<T>`)
  - Error: `{ "code": string, "message": string }` (`ApiError`)
- Validación de request con Bean Validation (`jakarta.validation`) en los DTOs (`record`).
- Errores de negocio: lanzar `AppException` (`AppException.notFound/unauthorized/forbidden/badRequest/conflict`),
  capturados por `GlobalExceptionHandler` (`@RestControllerAdvice`). No usar excepciones genéricas para
  casos de negocio esperados.
- Autenticación: JWT stateless. `JwtFilter` valida el `Authorization: Bearer <token>` y setea el
  `UUID` del usuario como principal (`@AuthenticationPrincipal UUID userId` en los controllers).
  El rol (`UserRole`) va embebido como claim en el access token y se traduce a `GrantedAuthority`
  (`ROLE_DRIVER`, `ROLE_PASSENGER`, `ROLE_BOTH`) en `JwtFilter`; los endpoints que lo requieren usan
  `@PreAuthorize("hasAnyRole(...)")` (`@EnableMethodSecurity` en `SecurityConfig`). Ver
  [decisión de autorización por rol](#decisión-autorización-por-rol-✅).

## Manejo de errores — catálogo de códigos

| Código | HTTP | Cuándo |
|--------|------|--------|
| `BAD_REQUEST` | 400 | Validación de Bean Validation fallida, o `AppException.badRequest` (ej. parseo de fecha inválido) |
| `UNAUTHORIZED` | 401 | Token ausente/inválido/expirado, o credenciales incorrectas en login |
| `FORBIDDEN` | 403 | Usuario autenticado pero sin permiso sobre el recurso (ej. editar vehículo ajeno) |
| `NOT_FOUND` | 404 | Recurso inexistente |
| `CONFLICT` | 409 | Violación de constraint único (email/phone/dni/plate duplicado, mensaje diferenciado por constraint en `GlobalExceptionHandler`) o `AppException.conflict` |
| `INTERNAL_ERROR` | 500 | Excepción no controlada (loggeada, mensaje genérico al cliente) |

Al agregar un endpoint nuevo: reusar estos códigos siempre que aplique. Solo agregar un código
nuevo a la tabla si representa un caso de error realmente distinto (ej. `SEAT_UNAVAILABLE` para
reservas sin cupo), documentándolo acá antes de lanzarlo desde el código.

## Modelo de datos

### User ✅
Tabla `users`. Un usuario puede ser pasajero, conductor o ambos (`role`); los campos de
conductor quedan `null` hasta iniciar el onboarding.

| Campo | Tipo | Notas |
|-------|------|-------|
| id | UUID (text) | PK |
| name | string | — |
| email | string | unique |
| phone | string | unique |
| passwordHash | string | bcrypt |
| role | enum `passenger\|driver\|both` | default `passenger` |
| avatarUrl | string? | S3 |
| ratingAvg | double | default 0.0 |
| ratingCount | int | default 0 |
| verifiedAt | timestamp? | verificación OTP |
| createdAt | timestamp | inmutable |
| dni | string? | unique, onboarding conductor. Validado 7-8 dígitos numéricos |
| birthDate | timestamp? | onboarding conductor |
| dniPhotoUrl, licensePhotoUrl, criminalRecordUrl | string? | S3, onboarding conductor |
| licenseCategory | string? | ej. `D1`–`D4` |
| driverStatus | enum `pending_documents\|under_review\|approved\|rejected`? | estado del onboarding |

### Vehicle ✅
Tabla `vehicles`. Un conductor puede tener múltiples vehículos (`driverId` FK, sin unique).

| Campo | Tipo | Notas |
|-------|------|-------|
| id | UUID (text) | PK |
| driverId | UUID (text) | FK → users |
| brand, model, color | string | — |
| year | int | — |
| plate | string | unique. Validada formato viejo (`AAA111`) o Mercosur (`AA111AA`), normalizada a mayúsculas |
| photoUrl | string? | S3 |
| verifiedAt | timestamp? | — |
| cedulaUrl, insuranceUrl, vtvUrl | string? | S3 |
| insurancePolicy | string? | — |
| insuranceExpiresAt, vtvExpiresAt | timestamp? | Validadas como fecha futura al crear el vehículo (`400 BAD_REQUEST` si no) |
| doors | int | requerido, 2-4. El conductor elige libremente, sin default forzado |
| seats | int | requerido, 1-8. Capacidad de pasajeros (distinto de `doors`); es la fuente de `Trip.totalSeats` |
| hasAc | boolean | default false |
| hasSeatbelts | boolean | default true |

### Trip ✅ (Fase 6)
Tabla `trips`. Ya existía físicamente en la base (migración Prisma original del backend Node.js,
nunca se le había agregado código); `vehicleId` se agregó recién con la Fase 6 (no estaba en la
tabla heredada).

| Campo | Tipo | Notas |
|-------|------|-------|
| id | UUID (text) | PK |
| driverId | UUID (text) | FK → users |
| vehicleId | UUID (text) | FK → vehicles. Fija `totalSeats` al crear el viaje (snapshot, no join en vivo) |
| originName, destinationName | string | — |
| originLat, originLng, destinationLat, destinationLng | double | PostGIS: guardar como columnas `Float`/`double` + índice `geography` calculado, no tipo PostGIS mapeado por Hibernate (ver nota abajo). El índice geography y las queries `ST_*` todavía no existen — llegan en Fase 7 (Búsqueda) |
| departureAt | timestamp | — |
| totalSeats | int | = `vehicle.seats` al momento de crear el trip |
| availableSeats | int | ≤ totalSeats |
| status | enum `draft\|published\|in_progress\|completed\|cancelled` | default `draft` |
| createdAt | timestamp | inmutable |

**Nota PostGIS:** las queries geoespaciales (`ST_DWithin`, `ST_Point`) van en SQL nativo
(`@Query(nativeQuery = true)` o `EntityManager.createNativeQuery`), no mapeadas como tipo de
columna en la entidad JPA — así se hizo en el backend Node.js con Prisma y se mantiene el criterio.

### Stop ✅ (Fase 6)
Tabla `stops`. Paradas intermedias de un viaje, en orden. Ya existía en la tabla heredada de Prisma.

| Campo | Tipo | Notas |
|-------|------|-------|
| id | UUID (text) | PK |
| tripId | UUID (text) | FK → trips |
| name | string | — |
| lat, lng | double | — |
| order | int | posición declarada por el request (no tiene que ser contigua; solo se valida que no haya valores duplicados — ver `POST /trips`) |
| estimatedArrivalAt | timestamp | — |

### TripSegment ✅ (Fase 6)
Tabla `trip_segments`. Tramos entre paradas consecutivas (incluye origen/destino como límites).
Ya existía en la tabla heredada de Prisma.

| Campo | Tipo | Notas |
|-------|------|-------|
| id | UUID (text) | PK |
| tripId | UUID (text) | FK → trips |
| fromStopId | UUID (text)? | null = origen del viaje |
| toStopId | UUID (text)? | null = destino del viaje |
| distanceKm | double | Haversine entre los dos puntos del tramo (`GeoUtils`); Google Maps Distance Matrix queda para más adelante |
| suggestedPrice | decimal | `distanceKm * PRICE_PER_KM_ARS * factorZona` |
| finalPrice | decimal | Hoy = `suggestedPrice` (todavía no existe el endpoint para que el conductor lo ajuste ±30%, ver "a definir" en `POST /trips`) |
| order | int | secuencial; el tramo "recorrido completo" (ambos FKs null, solo cuando hay ≥1 parada intermedia) siempre tiene el último `order` |

### Booking ✅ (Fase 7, etapa 2 — parcial, ver más abajo)
Tabla `bookings` — ya existía desde la migración heredada de Prisma (`V1__init.sql`), incluidos
los enums `BookingStatus`/`PaymentStatus`. `V7__bookings_nullable_stops.sql` relaja
`from_stop_id`/`to_stop_id` de `NOT NULL` a nullable (el schema heredado los tenía obligatorios,
lo cual no permitía representar "sube/baja en el origen/destino" con `null`, mismo criterio que
`TripSegment`).

| Campo | Tipo | Notas |
|-------|------|-------|
| id | UUID (text) | PK |
| passengerId | UUID (text) | FK → users |
| tripId | UUID (text) | FK → trips |
| fromStopId | UUID (text)? | parada de subida; `null` = sube en el origen del viaje |
| toStopId | UUID (text)? | **hoy siempre `null`** (destino final) — ver simplificación abajo |
| seatNumber | int | asignado automáticamente al reservar |
| totalPrice | decimal | suma de `finalPrice` de los `trip_segments` desde `fromStopId` hasta el destino |
| paymentStatus | enum `pending\|held\|released\|refunded` | **siempre `pending`** hoy — no hay integración real de Mercado Pago todavía (Fase 9) |
| mpPaymentId | string? | referencia Mercado Pago — sin uso todavía |
| status | enum `confirmed\|cancelled\|completed` | default `confirmed` |
| shareToken | UUID (text) | único, generado al crear la reserva — sin uso todavía (`GET /bookings/:id/share` no implementado) |
| createdAt | timestamp | inmutable |

**Simplificación de producto (decidida con el usuario):** por ahora **todo pasajero viaja hasta
el destino final del viaje** — no hay baja en paradas intermedias. Esto implica que dos
reservas del mismo viaje siempre se superponen (ambas terminan en el mismo punto), así que la
disponibilidad de butacas es simplemente "reservas `confirmed` < `trip.availableSeats`", sin
necesidad de chequear superposición por tramo. Si en el futuro se habilita bajar en paradas
intermedias, hay que revisar `BookingService.create` (la cuenta de asientos ya no alcanza,
hace falta chequear superposición de rangos por asiento).

### Rating ⏳ (Fase 11)
Tabla `ratings`.

| Campo | Tipo | Notas |
|-------|------|-------|
| id | UUID (text) | PK |
| bookingId | UUID (text) | FK → bookings |
| fromUserId, toUserId | UUID (text) | quién califica / a quién |
| score | int | 1–5 |
| tags | string[] | subset de `puntual, auto_limpio, buen_manejo, amable` |
| comment | string? | — |
| createdAt | timestamp | inmutable |

## Especificación de API

### Auth ✅

#### POST /auth/register
Público. Crea usuario y dispara envío de OTP por SMS (Twilio).

**Request** (`RegisterRequest`):
```json
{ "name": "string", "email": "email", "phone": "string", "password": "string (min 6)", "role": "passenger|driver|both" }
```
**Response 201** (`AuthResponse`): `{ "data": { "accessToken", "refreshToken", "user": UserResponse } }`
**Errores:** `400 BAD_REQUEST` (validación), `409 CONFLICT` (email/phone duplicado)

#### POST /auth/login
Público.
**Request** (`LoginRequest`): `{ "email": "email", "password": "string" }`
**Response 200** (`AuthResponse`)
**Errores:** `401 UNAUTHORIZED` (credenciales inválidas)

#### POST /auth/refresh
Público (el refresh token es la credencial).
**Request** (`RefreshRequest`): `{ "refreshToken": "string" }`
**Response 200** (`TokensResponse`): `{ "accessToken", "refreshToken" }`
**Errores:** `401 UNAUTHORIZED` (refresh token inválido/expirado)

#### POST /auth/verify-otp
Público.
**Request** (`VerifyOtpRequest`): `{ "phone": "string", "code": "string (6 dígitos)" }`
**Response 200:** `{ "data": null }`
**Errores:** `400 BAD_REQUEST` (código incorrecto/expirado)
**Nota:** OTP actualmente en memoria (`Map` con TTL 5 min), no en Redis. Migrar a Redis antes de escalar a múltiples instancias (single point of failure si hay más de una instancia del backend).

### Users ✅

#### GET /users/me
Auth requerida. Devuelve el perfil completo del usuario autenticado (`UserResponse`, incluye campos de conductor si aplica).

#### PUT /users/me
Auth requerida. `multipart/form-data` o `application/json`.
**Request:** `name` (string, requerido si se envía), `avatar` (file, opcional)
**Response 200** (`UserResponse`)

#### GET /users/:id
Público. Perfil público (mismo `UserResponse`, el frontend decide qué mostrar).

### Drivers ✅
> No estaba documentado en `CLAUDE.md` — se agrega acá porque ya existe implementado y es
> parte del flujo de onboarding de conductor.

#### GET /drivers/profile
Auth requerida. Devuelve el mismo `UserResponse` con foco en campos de conductor/`driverStatus`.

#### POST /drivers/profile
Auth requerida. `multipart/form-data`. Envía/actualiza los datos de onboarding de conductor.
**Request** (`DriverProfileRequest` + archivos):
```
dni: string (7-8 dígitos numéricos)
birthDate: string "YYYY-MM-DD" (≥ 21 años)
licenseCategory: string (regex D[1-4])
dniPhoto, licensePhoto, criminalRecord: file (requeridos; jpg/png/pdf, máx 5MB)
```
**Response 200** (`UserResponse`)
**Errores:** `400 BAD_REQUEST` (validación de campo, archivo faltante/tipo inválido/tamaño excedido, menor de 21 años), `409 CONFLICT` (dni/email/teléfono/licencia duplicados, con mensaje diferenciado por constraint)
**Pendiente:** validación real de contenido de los documentos (hoy solo se valida tipo/tamaño, no que el documento sea auténtico) — ver [producto.md](producto.md#validaciones-de-documentos--onboarding-conductor-pendiente-próximas-iteraciones).
**Pendiente (a definir):** no existe ningún proceso ni endpoint para pasar `driverStatus` de
`under_review` a `approved` — hoy es un `UPDATE` manual en la tabla `users`. Falta definir un
flujo de aprobación real (¿panel admin? ¿revisión manual con cola de moderación? ¿automática
con algún control mínimo?) antes de habilitar conductores en producción. Mientras tanto,
`POST /trips/:id/publish` y `GET /trips/search` ya exigen `approved` (ver secciones de Trips).

### Vehicles ✅

#### GET /vehicles/mine
Auth requerida. Lista los vehículos del usuario autenticado (`List<VehicleResponse>`).

#### POST /vehicles
Auth requerida. `multipart/form-data`.
**Request** (`CreateVehicleRequest` + archivos):
```
brand, model, plate, color: string (requeridos; plate formato AAA111 o AA111AA, normalizada a mayúsculas)
year: int (requerido, máx 20 años de antigüedad)
doors: int (requerido, 2-4)
seats: int (requerido, 1-8)
hasAc, hasSeatbelts: boolean (opcional)
insurancePolicy: string (opcional)
insuranceExpiresAt: string (opcional — sin input en el mobile; si se envía, se valida igual como fecha futura)
vtvExpiresAt: string (requerido en `POST`; debe ser fecha futura — la VTV tiene que estar vigente)
photo: file (opcional)
cedula, insurance, vtv: file (requeridos en `POST`; jpg/png/pdf, máx 5MB)
```
**Response 201** (`VehicleResponse`)
**Errores:** `409 CONFLICT` (patente duplicada, mensaje diferenciado), `400 BAD_REQUEST` (validación de campo, archivo faltante/tipo inválido/tamaño excedido, fecha de vencimiento no futura)

#### PUT /vehicles/:id
Auth requerida, solo el dueño (`driverId == userId`, verificar en `VehicleService`).
Mismo request que `POST /vehicles`, pero `cedula`/`insurance`/`vtv` quedan opcionales (actualización
parcial: si no se reenvía un archivo, se conserva el existente).
**Errores:** `403 FORBIDDEN` (vehículo ajeno), `404 NOT_FOUND`

---

### Trips ✅ (Fase 6)

#### POST /trips
Auth requerida, rol `driver` o `both` (`@PreAuthorize("hasAnyRole('DRIVER','BOTH')")`, ver
[decisión de autorización por rol](#decisión-autorización-por-rol-✅)).
Crea un viaje en estado `draft` con sus paradas y calcula los tramos sugeridos.

**Request** (`CreateTripRequest`, también usado por `PUT /trips/:id`):
```json
{
  "vehicleId": "uuid",
  "originName": "string", "originLat": 0.0, "originLng": 0.0,
  "destinationName": "string", "destinationLat": 0.0, "destinationLng": 0.0,
  "departureAt": "ISO-8601",
  "availableSeats": 1,
  "stops": [{ "name": "string", "lat": 0.0, "lng": 0.0, "order": 1 }]
}
```
**Reglas de negocio:**
- `availableSeats` ≤ `vehicle.seats` (`400 BAD_REQUEST` si no); `totalSeats` del trip = `vehicle.seats`.
- El vehículo debe pertenecer al conductor autenticado (`403 FORBIDDEN` si no, `404 NOT_FOUND` si no existe).
- `departureAt` debe ser una fecha futura (`@Future`); `originLat/destinationLat` ∈ [-90, 90],
  `originLng/destinationLng` ∈ [-180, 180] (mismo rango para cada `stop`).
- Origen y destino no pueden ser el mismo punto (distancia Haversine < 50m → `400 BAD_REQUEST`).
- `stops` se ordenan por su campo `order`; si hay valores duplicados → `400 BAD_REQUEST`. No hace
  falta que sean contiguos, solo estrictamente distintos entre sí.
- Al crear, se generan automáticamente los `trip_segments` entre cada par de puntos consecutivos
  (origen → paradas en orden → destino) con `suggestedPrice` calculado.
- Precio: `precio_tramo = distancia_km * PRICE_PER_KM_ARS * factor_zona.factor()`. `distancia_km` se
  calcula con Haversine (`GeoUtils`); Google Maps Distance Matrix queda pendiente para una iteración
  futura. `factor_zona` sale de `ZonaResolver` — **hoy `StubZonaResolver` siempre devuelve `conurbano`
  (factor `1.0`)**, es un placeholder a propósito porque todavía no existe una definición geográfica
  real de conurbano/interurbano/CABA (**a definir**: polígonos, reverse-geocoding o bounding boxes).
- Si hay ≥1 parada intermedia, se agrega un `trip_segment` adicional "recorrido completo"
  (`fromStopId`/`toStopId` ambos null) cuyo `suggestedPrice` es el de la suma de los tramos
  individuales con un descuento (`FULL_TRIP_DISCOUNT = 0.85`, con un clamp defensivo a `0.95` de esa
  suma) — así el precio del recorrido completo siempre es menor a la suma de los tramos. Sin paradas
  intermedias, el único segmento (origen→destino directo) ya cumple ese rol y no se duplica.
- `finalPrice` = `suggestedPrice` al crear. El ajuste ±30% por tramo se hace después, vía
  `PATCH /trips/:id/segments` (ver más abajo), no en la creación.
- `TripSegment.finalPrice` es el precio **por pasajero/asiento reservado** de ese tramo, no un total
  del viaje — cada pasajero paga el `finalPrice` completo del tramo que reserva (no hay división ni
  multiplicación por cantidad de pasajeros en el cálculo).

**Response 201** (`TripResponse`): el trip con sus `stops` y `segments` completos.
**Errores:** `400 BAD_REQUEST` (paradas con `order` duplicado, `availableSeats` > capacidad, origen=destino, campos fuera de rango), `403 FORBIDDEN` (vehículo ajeno), `404 NOT_FOUND` (vehículo inexistente)

#### PUT /trips/:id
Auth requerida, solo el conductor dueño, **solo si `status == draft`**.
Mismo shape que `POST /trips`. Borra y regenera `stops`/`trip_segments` desde cero con las mismas
reglas de creación. **Importante:** esto resetea cualquier ajuste manual de precio hecho vía
`PATCH /trips/:id/segments` (los tramos nuevos no tienen relación con los viejos ajustados) — es
comportamiento esperado, documentado también como comentario en `TripService.update()`.
**Errores:** `409 CONFLICT` si el viaje ya está `published` o en un estado posterior (no se edita, se cancela y crea uno nuevo), `403 FORBIDDEN` (dueño distinto), `404 NOT_FOUND`.

#### PATCH /trips/:id/segments
Auth requerida, rol `driver`/`both`, solo el conductor dueño, **solo si `status == draft`**.
Ajusta el `finalPrice` de uno o más tramos ya generados, sin recalcular `suggestedPrice` ni tocar el
resto del trip/stops.

**Request** (`AdjustSegmentPricesRequest`):
```json
{ "segments": [{ "segmentId": "uuid", "finalPrice": 0.0 }] }
```
**Reglas de negocio:**
- `finalPrice` debe ser positivo (`400 BAD_REQUEST` si no). **Por ahora, sin límite de rango**: el
  margen ±30% sobre `suggestedPrice` que describe `producto.md` todavía no se aplica — el conductor
  puede poner el precio que quiera mientras el trip esté en `draft`. Si se decide reintroducir el
  margen más adelante, es un chequeo acotado a este método (`TripService.adjustSegmentPrices`).
- `segmentId` debe pertenecer al trip del path (`400 BAD_REQUEST` si no).

**Response 200** (`TripResponse`): el trip actualizado con los `finalPrice` nuevos.
**Errores:** `400 BAD_REQUEST` (precio fuera de rango, segmento ajeno al trip), `403 FORBIDDEN` (dueño distinto), `404 NOT_FOUND`, `409 CONFLICT` (trip no está en `draft`).

#### POST /trips/:id/publish
Auth requerida, solo el dueño, `status == draft` → `published`.
**Reglas:** `availableSeats ≥ 1` (ya se garantiza en la creación, pero se revalida). El conductor
debe tener `driverStatus == approved` — todavía no hay ningún proceso que revise la
documentación subida en el onboarding (ver nota más abajo), así que hoy esto solo se cumple si
alguien lo aprobó manualmente (UPDATE directo en la tabla `users`, no hay endpoint admin).
**Errores:** `409 CONFLICT` si no está en `draft` o si el conductor no está `approved`,
`403 FORBIDDEN` (dueño distinto), `404 NOT_FOUND`.

#### GET /trips/mine
Auth requerida. Lista los viajes del conductor autenticado (todos los estados), cada uno con sus
`stops`/`segments`/`bookings` (resumen de pasajeros que se sumaron, ver Bookings).

#### GET /trips/:id
Auth: público si el viaje está `published` o posterior; el dueño puede verlo en cualquier estado.
**Decisión:** si el viaje está en `draft` y quien pide no es el dueño (incluido acceso sin token),
responde `404 NOT_FOUND` (no `403`) para no filtrar la existencia de viajes en borrador.
`bookings` (`List<BookingSummaryResponse>`) solo se completa para el dueño del viaje — para
cualquier otro caso (público o un pasajero viendo un viaje ajeno) viene vacío, para no exponer
quién más se sumó al viaje.

#### DELETE /trips/:id
Auth requerida, solo el dueño. Cancela (`status = cancelled`), no borra físicamente.
**Reglas:** transición de estado + chequeo de ownership/estado terminal (`409 CONFLICT` si ya
está `completed`/`cancelled`) + cascada: las reservas `confirmed` del viaje pasan a `cancelled`
(ver sección Bookings).
**Pendiente:** reembolso real de las reservas canceladas — hoy no hace falta porque
`paymentStatus` nunca pasa de `pending` (sin Mercado Pago todavía, Fase 9). Cuando exista pago
real, falta definir la política según anticipación a `departureAt` (`producto.md` solo define
la regla para cancelación del conductor con +2hs de anticipación, no qué pasa con menos).

### Búsqueda de viajes ✅ (Fase 7, etapa 1)

#### GET /trips/search?destinationLat&destinationLng&date?
Público. Devuelve viajes `published` (con `departureAt` futuro) que matchean.

**Decisión de producto:** la búsqueda es **solo por destino final** — el pasajero todavía no
elige dónde se sube (eso lo decide después, mirando el mapa completo con todas las paradas en
`GET /trips/:id`). Se pidió así explícitamente para simplificar el primer paso: se buscan
"viajes que pasen por donde quiero ir", y recién en el detalle del viaje el pasajero evalúa
qué parada le conviene usar para subirse.

**Regla de matcheo:** implementada en Java (`TripService.search`), **no con PostGIS** —
decisión tomada al implementar para no requerir una migración nueva (columnas `geography` +
índice GIST) ni cambiar la imagen de Testcontainers de `postgres:16-alpine` a
`postgis/postgis`. Reusa `GeoUtils.haversineKm` (mismo criterio que el cálculo de precios de
Fase 6), suficiente para el volumen de datos actual; se puede migrar a PostGIS más adelante
si hace falta escalar (`producto.md` no se ve afectado, describe la regla de negocio, no la
implementación).
1. Se arma la ruta ordenada del viaje `[origen, stops..., destino]` (mismo orden que
   `generateStopsAndSegments`). El destino buscado matchea con el punto de la ruta más
   cercano dentro de `SEARCH_MATCH_RADIUS_KM = 0.8` (800m), **excluyendo el origen del viaje**
   (índice 0): nadie busca un viaje para "llegar" al punto donde arranca el conductor.
2. Si no hay match, el viaje no aparece en los resultados.
3. El conductor del viaje debe tener `driverStatus == approved` — si no, el viaje no aparece
   aunque matchee geográficamente (mismo chequeo que en `POST /trips/:id/publish`).
4. `date` (opcional) acota los candidatos a ese día calendario (UTC); sin `date`, devuelve
   cualquier viaje futuro.
5. **Todavía no filtra por asientos disponibles reales** (no hay `bookings` que los
   decrementen — `availableSeats` del resultado es directamente el declarado por el
   conductor, igual que en `GET /trips/:id`). Se corrige junto con Reservas.

**Precio orientativo (`minPrice`):** como todavía no se eligió dónde subir, se muestra el
precio del último tramo antes del punto matcheado (subir en la parada inmediata anterior es
la forma más barata de llegar ahí) — **no** es un precio final ni usa el descuento del tramo
"recorrido completo" (ese solo aplica si se sube exactamente en el origen del viaje, algo que
el pasajero recién decide en el detalle).

**Response 200** (`List<TripSearchResult>`): por cada viaje matcheado, `tripId`, datos básicos
del conductor (`driverName`, `driverRatingAvg`) y vehículo (`vehicleBrand`, `vehicleModel`),
`originName`/`destinationName` del viaje completo, `departureAt`, el punto matcheado
(`matchedToName`, `toStopId` nullable — null tanto si matcheó el destino final del viaje como,
en general, cualquier extremo sin id propio), `minPrice` y `availableSeats`.

**Elegir dónde subirse:** eso pasa en `GET /trips/:id` (ya expone `stops` y `segments`
completos con su `finalPrice` cada uno) — el frontend muestra ahí el mapa y la lista de tramos
para que el pasajero evalúe la opción más conveniente antes de reservar (Reservas, etapa 2).

**Pendiente, deliberadamente fuera de esta etapa:**
- `GET /trips/:id/segments` (asientos libres por tramo) — no aporta nada sin `Booking` (nada
  decrementa `availableSeats` todavía), se implementa junto con Reservas.
- Filtrar resultados por asientos realmente disponibles.

---

### Bookings ✅ (Fase 7, etapa 2 — parcial)

Solo se implementan `create`/`mine`/`cancel`. `confirm-arrival` y `share`/`share/:token` quedan
**deliberadamente sin implementar**, documentados más abajo — dependen de tracking GPS en vivo
(Fase 8, no existe) y de una captura real de pago que libere/reembolse (Fase 9, no existe).

#### POST /bookings
Auth requerida (cualquier usuario autenticado; no puede reservar su propio viaje).
**Request** (`CreateBookingRequest`): `{ "tripId": "uuid", "fromStopId": "uuid?" }` — sin
`toStopId`: hoy toda reserva llega hasta el destino final del viaje (ver simplificación en la
sección de la entidad `Booking`).
**Reglas de negocio:**
1. El viaje debe estar `published` (`409 CONFLICT` si no).
2. No podés reservar tu propio viaje (`400 BAD_REQUEST` si `trip.driverId == passengerId`).
3. `fromStopId` debe ser una parada real del viaje o `null` (origen); si no, `400 BAD_REQUEST`.
   No puede ser el último punto de la ruta (no hay tramo que reservar desde ahí).
4. `totalPrice` = precio desde `fromStopId` hasta el destino final (`RoutePricing.priceForRange`,
   la misma utilidad que usa la búsqueda — si `fromStopId` es `null` usa el tramo "recorrido
   completo" con descuento, si no suma los tramos individuales restantes).
5. Un pasajero no puede tener más de una reserva `confirmed` en el mismo viaje — `409 CONFLICT`
   si ya tiene una activa (tiene que cancelarla primero para reservar otro tramo/asiento).
6. Asiento: como toda reserva llega al destino, alcanza con contar reservas `confirmed` del
   viaje contra `trip.availableSeats` — si ya está lleno, `409 CONFLICT` (`SEAT_UNAVAILABLE`).
   Se asigna el primer número de butaca 1..`availableSeats` no usado por una reserva activa.
7. `paymentStatus` queda en `pending` — **sin integración real de Mercado Pago todavía** (Fase 9).
8. Se genera `shareToken`, pero sin ningún endpoint que lo use todavía.

**Limitación conocida, no resuelta en esta etapa:** la asignación de butaca lee y después
escribe sin lock — dos reservas simultáneas sobre el último asiento libre podrían ambas ver
cupo disponible. Aceptable a escala de MVP; requiere hardening (lock pesimista o constraint a
nivel DB) antes de tráfico real concurrente.

**Response 201** (`BookingResponse`): datos de la reserva + resumen del viaje ya resuelto
(conductor, vehículo, ruta) para que "Mis reservas" no necesite pedidos adicionales.
**Errores:** `409 CONFLICT` (`SEAT_UNAVAILABLE`, viaje no `published`, ya tenés una reserva activa
en este viaje), `400 BAD_REQUEST` (reservar el propio viaje, `fromStopId` ajeno al viaje o es el
último punto de la ruta), `404 NOT_FOUND` (viaje inexistente).

#### GET /bookings/mine
Auth requerida. Reservas del usuario autenticado (`List<BookingResponse>`).

#### POST /bookings/:id/cancel
Auth requerida, solo el pasajero dueño de la reserva, solo si `status == confirmed`.
**Reglas:** pasa a `cancelled`. Reembolso: no aplica todavía — `paymentStatus` nunca pasó de
`pending` (no hay captura real), la política de reembolso según anticipación sigue **a
definir** para cuando exista Mercado Pago (Fase 9), igual que para cancelación de trips.
**Errores:** `403 FORBIDDEN` (dueño distinto), `409 CONFLICT` (la reserva ya no está activa),
`404 NOT_FOUND`.

**Cascada desde `DELETE /trips/:id`:** al cancelar un viaje, sus reservas `confirmed` pasan
automáticamente a `cancelled` (ver `TripService.cancel`).

---

#### Pendiente, deliberadamente fuera de esta etapa

##### POST /bookings/:id/confirm-arrival
Auth requerida, solo el pasajero dueño.
**Reglas:** libera el pago (`paymentStatus: held → released`) al conductor. Si no se llama en 30 min desde `estimatedArrivalAt`, liberar automáticamente (requiere job/scheduler — a definir mecanismo: `@Scheduled` de Spring vs cola). No implementable de forma útil sin Fase 8 (no hay `estimatedArrivalAt` real todavía) ni Fase 9 (no hay pago que liberar).

##### GET /bookings/:id/share
Auth requerida, dueño de la reserva. Devuelve/genera el link público (`/share/:token`).

##### GET /share/:token
Público (sin auth). Vista de solo lectura: conductor, patente, ruta, ETA. No debe exponer datos
sensibles del pasajero ni de otros pasajeros del mismo viaje. Sin valor real sin Fase 8 (no hay
posición/ETA en vivo que mostrar).

---

### Ratings ⏳ (Fase 11)

#### POST /ratings
Auth requerida. Solo tras `booking.status == completed`.
**Request:** `{ "bookingId": "uuid", "toUserId": "uuid", "score": 1-5, "tags": ["puntual", ...], "comment": "string?" }`
**Reglas:** un usuario califica una vez por `booking` (constraint único `bookingId + fromUserId`).
Al guardar, recalcular `ratingAvg`/`ratingCount` del `toUser`.
**Errores:** `409 CONFLICT` (ya calificado), `403 FORBIDDEN` (no participó del booking)

---

### WebSocket (STOMP) ⏳ (Fase 8)

Spring WebSocket + STOMP. Canal autenticado por JWT en el handshake (`Authorization` header o query param — definir al implementar).

| Evento | Dirección | Payload | Notas |
|--------|-----------|---------|-------|
| `driver:location_update` | conductor → server | `{ tripId, lat, lng, timestamp }` | cada 3s. Server escribe en Redis `driver_location:{driver_id}` TTL 30s, no en Postgres. |
| `passenger:location_received` | server → pasajeros del viaje | `{ tripId, lat, lng, timestamp }` | broadcast a todos los `bookings` activos del trip |
| `trip:status_changed` | server → interesados | `{ tripId, status }` | al cambiar `TripStatus` |
| `booking:driver_nearby` | server → pasajero | `{ bookingId, etaMinutes }` | trigger cuando ETA calculado ≤ 5 min; dispara también push FCM |
| `trip:next_stop_updated` | conductor → server → pasajeros | `{ tripId, nextStopId }` | vista de seguimiento de ruta |
| `share:location_update` | server → cliente público (sin auth) | `{ token, lat, lng, eta }` | mismo dato que `passenger:location_received` pero servido a través del `share_token`, no requiere sesión |

**Persistencia:** solo se escribe en PostgreSQL la ubicación final al completar el viaje. Todo lo demás vive en Redis con TTL.

---

### Pagos (Mercado Pago — escrow) ⏳ (Fase 9)

No son endpoints REST propios sino integración dentro de `POST /bookings` (captura) y
`POST /bookings/:id/confirm-arrival` / cancelación (liberación o reembolso), más un webhook:

#### POST /webhooks/mercadopago
Público (validar firma de Mercado Pago). Recibe notificaciones de cambio de estado de pago
y actualiza `Booking.paymentStatus` / `mpPaymentId` en consecuencia.

**Reglas de negocio (de `producto.md`):**
- Comisión de la app: 10% sobre cada transacción.
- Reembolso automático si el conductor cancela con +2hs de anticipación.
- Liberación automática a los 30 min si el pasajero no confirma llegada.

---

## Decisión: autorización por rol ✅

Se optó por **embeber el rol en el JWT de acceso** (claim `role`) en vez de hacer lookup a
`UserRepository` en cada request — el rol se setea una única vez en `AuthService.register` y hoy
ningún endpoint lo muta después, así que el riesgo de "rol stale" es mínimo, y evita un query extra
por request (importante de cara al WebSocket de Fase 8, de alta frecuencia).

- `JwtUtil.generateAccessToken(UUID userId, UserRole role)` agrega el claim `role`.
- `JwtFilter` lo lee (`extractRoleFromAccess`) y arma `GrantedAuthority` (`ROLE_DRIVER`,
  `ROLE_PASSENGER`, `ROLE_BOTH`).
- `SecurityConfig` tiene `@EnableMethodSecurity`; los controllers usan
  `@PreAuthorize("hasAnyRole(...)")` (ver `TripController.create`).
- `AuthService.refresh` **relee el rol actual desde `UserRepository`** al regenerar el access token
  — así, si en el futuro algo llega a mutar el rol de un usuario, la ventana de staleness queda
  acotada al ciclo de refresh (15 min hoy, `jwt.access-expiration-ms`) en vez de persistir hasta el
  logout.
- El chequeo de **ownership** (vehículo/trip ajeno) sigue siendo explícito en el Service
  (`AppException.forbidden()`) — el rol solo gatea "puede crear viajes", no reemplaza esa validación.

## Cómo agregar una spec nueva

1. Agregar la entidad a **Modelo de datos** con estado ⏳ y sus campos.
2. Agregar los endpoints a **Especificación de API** con: request, reglas de negocio,
   response, errores. Marcar explícitamente lo que quede sin definir ("a definir") en vez
   de asumir un comportamiento.
3. Implementar contra esa spec.
4. Actualizar el estado a ✅ y corregir cualquier detalle que haya cambiado durante la
   implementación (la spec es la que se ajusta a lo real, no al revés).
5. Actualizar la tabla de **Estado general (fases)**.
