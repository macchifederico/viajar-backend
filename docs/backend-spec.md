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
| 7 | Búsqueda + reservas (Booking) | ⏳ |
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

### Booking ⏳ (Fase 7)
Tabla `bookings`.

| Campo | Tipo | Notas |
|-------|------|-------|
| id | UUID (text) | PK |
| passengerId | UUID (text) | FK → users |
| tripId | UUID (text) | FK → trips |
| fromStopId, toStopId | UUID (text)? | tramo reservado (mismo criterio null que TripSegment) |
| seatNumber | int | asignado automáticamente al reservar |
| totalPrice | decimal | suma de `finalPrice` de los `trip_segments` cubiertos |
| paymentStatus | enum `pending\|held\|released\|refunded` | default `pending` |
| mpPaymentId | string? | referencia Mercado Pago |
| status | enum `confirmed\|cancelled\|completed` | default `confirmed` |
| shareToken | UUID (text) | único, generado al crear la reserva |
| createdAt | timestamp | inmutable |

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
**Reglas:** `availableSeats ≥ 1` (ya se garantiza en la creación, pero se revalida).
**Errores:** `409 CONFLICT` si no está en `draft`, `403 FORBIDDEN` (dueño distinto), `404 NOT_FOUND`.

#### GET /trips/mine
Auth requerida. Lista los viajes del conductor autenticado (todos los estados), cada uno con sus `stops`/`segments`.

#### GET /trips/:id
Auth: público si el viaje está `published` o posterior; el dueño puede verlo en cualquier estado.
**Decisión:** si el viaje está en `draft` y quien pide no es el dueño (incluido acceso sin token),
responde `404 NOT_FOUND` (no `403`) para no filtrar la existencia de viajes en borrador.
Todavía no expone `bookings` (no existe la entidad hasta Fase 7).

#### DELETE /trips/:id
Auth requerida, solo el dueño. Cancela (`status = cancelled`), no borra físicamente.
**Implementado en Fase 6:** solo la transición de estado + chequeo de ownership/estado terminal
(`409 CONFLICT` si ya está `completed`/`cancelled`).
**Pendiente para Fase 7** (hay un TODO explícito en `TripService.cancel`): si hay `bookings` con
`paymentStatus == held`, disparar reembolso (ver [Pagos](#pagos-mercado-pago---escrow--fase-9)) solo
si faltan +2hs para `departureAt`; si faltan menos, **política a definir con el usuario** antes de
implementar (hoy `producto.md` solo define la regla de reembolso para cancelación del conductor con
+2hs de anticipación, no qué pasa si cancela con menos).

### Búsqueda de viajes ⏳ (Fase 7)

#### GET /trips/search?from=lat,lng&to=lat,lng&date=ISO-8601
Público. Devuelve viajes `published` que matchean.

**Regla de matcheo (PostGIS):**
1. `from` está a ≤800m de alguna parada del viaje (`ST_DWithin(stop.location, ST_Point(:lng,:lat)::geography, 800)`).
2. `to` está a ≤800m de alguna parada **posterior** a la de origen matcheada (por `order`).
3. Al menos 1 asiento disponible en el tramo resultante (sumar `bookings` activas del tramo vs `availableSeats`).

**Response 200:** lista de viajes con el tramo específico matcheado (`fromStopId`, `toStopId`, `finalPrice`, asientos libres en ese tramo).

#### GET /trips/:id/segments
Público. Lista los `trip_segments` disponibles de un viaje publicado, con asientos libres por tramo.

---

### Bookings ⏳ (Fase 7)

#### POST /bookings
Auth requerida (pasajero).
**Request:** `{ "tripId": "uuid", "fromStopId": "uuid?", "toStopId": "uuid?" }`
**Reglas de negocio:**
1. Verificar asientos disponibles en todos los `trip_segments` cubiertos por el tramo pedido.
2. Asignar `seatNumber` automáticamente (primer número libre 1..`totalSeats` del trip).
3. `totalPrice` = suma de `finalPrice` de los segments cubiertos.
4. Iniciar captura de pago en Mercado Pago (escrow) → `paymentStatus = held` si la captura es exitosa,
   la reserva no se confirma si el pago falla.
5. Generar `shareToken` único.
**Response 201:** Booking creado.
**Errores:** `409 CONFLICT` (`SEAT_UNAVAILABLE` — sin cupo en el tramo), `402`/`BAD_REQUEST` (pago rechazado — definir código al implementar Mercado Pago).

#### GET /bookings/mine
Auth requerida. Reservas del pasajero autenticado.

#### POST /bookings/:id/cancel
Auth requerida, solo el pasajero dueño de la reserva.
**Reglas:** política de reembolso según anticipación — **a definir** (no especificada en `producto.md` para cancelación del pasajero, solo para el conductor).

#### POST /bookings/:id/confirm-arrival
Auth requerida, solo el pasajero dueño.
**Reglas:** libera el pago (`paymentStatus: held → released`) al conductor. Si no se llama en 30 min desde `estimatedArrivalAt`, liberar automáticamente (requiere job/scheduler — a definir mecanismo: `@Scheduled` de Spring vs cola).

#### GET /bookings/:id/share
Auth requerida, dueño de la reserva. Devuelve/genera el link público (`/share/:token`).

#### GET /share/:token
Público (sin auth). Vista de solo lectura: conductor, patente, ruta, ETA. No debe exponer datos
sensibles del pasajero ni de otros pasajeros del mismo viaje.

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
