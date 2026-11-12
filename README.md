# API de Portfolio de Inversiones / Felix Sirit (siritfelix@gmail.com) / https://github.com/siritfelix/cocos-challenge.git

API REST para gestionar un portfolio de inversiones: transferencias de efectivo,
compras y ventas de acciones (a mercado y limitadas), consulta del estado
patrimonial y del historial de ordenes, y cancelacion de ordenes pendientes.

Implementada en **Java 21 + Spring Boot** con arquitectura **hexagonal**
(puertos y adaptadores), **PostgreSQL** con **Liquibase** y cobertura de
concurrencia con locks pesimistas por cuenta.

## Contenido

- [Ejecucion](#ejecucion)
- [API](#api)
- [Requests de ejemplo](#requests-de-ejemplo)
- [Arquitectura](#arquitectura)
- [Decisiones de diseno](#decisiones-de-diseno)
- [Supuestos](#supuestos)
- [Pruebas](#pruebas)
- [Estructura del proyecto](#estructura-del-proyecto)

## Ejecucion

### Con Docker (todo en uno, sin instalar nada)

```powershell
docker compose up --build
```

La API queda en `http://localhost:8080` y PostgreSQL en el puerto `5432`.
La base se crea y se migra sola al arrancar (Liquibase).

Para resetear el estado de la base (borra datos):

```powershell
docker compose down -v
```

### En local (JDK 21 + Maven + PostgreSQL en marcha)

```powershell
copy .env.example .env    # ajustar credenciales si hace falta
mvn spring-boot:run
```

La documentacion interactiva queda en `http://localhost:8080/swagger-ui.html`
y el contrato OpenAPI en `http://localhost:8080/v3/api-docs`.

### Verificaciones end-to-end

Los scripts de `tools/` corren casos completos contra la API en marcha:

```powershell
powershell -ExecutionPolicy Bypass -File tools/verify_orders_contract.ps1
powershell -ExecutionPolicy Bypass -File tools/verify_portfolio.ps1
powershell -ExecutionPolicy Bypass -File tools/verify_instruments.ps1
powershell -ExecutionPolicy Bypass -File tools/verify_history_contract.ps1
powershell -ExecutionPolicy Bypass -File tools/verify_cancel_contract.ps1
```

## API

| Metodo | Ruta | Descripcion | Cabeceras obligatorias |
|---|---|---|---|
| `GET` | `/api/v1/instruments` | Catalogo de instrumentos con su ultimo precio y cierre anterior, con filtro `query` opcional | - |
| `POST` | `/api/v1/orders` | Crea una orden (compra/venta/transferencia) | `X-AccountNumber`, `Idempotency-Key` |
| `GET` | `/api/v1/orders` | Historial de ordenes paginado y filtrable | `X-AccountNumber` |
| `DELETE` | `/api/v1/orders/{orderId}` | Cancela una orden pendiente | `X-AccountNumber` |
| `GET` | `/api/v1/portfolio` | Estado patrimonial de la cuenta | `X-AccountNumber` |

Los errores usan una estructura unificada:

```json
{
  "timestamp": "2026-11-12T04:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Missing required header 'X-AccountNumber'",
  "path": "/api/v1/orders"
}
```

### Catalogo con precios

`GET /api/v1/instruments` devuelve cada instrumento con su ultimo precio disponible
(`price`, cierre de la cotizacion mas reciente) y el cierre anterior referenciado por
esa misma cotizacion (`previousClose`). Los instrumentos sin cotizacion utilizable no
exponen esos campos (por ejemplo, el efectivo `ARS`):

```json
[
  {"id": 65, "ticker": "YPFD", "name": "Y.P.F. S.A.", "type": "ACCIONES",
   "price": 7837.50, "previousClose": 7975.05},
  {"id": 66, "ticker": "ARS", "name": "Peso argentino", "type": "MONEDA"}
]
```

## Requests de ejemplo

### Ingresar efectivo

```http
POST /api/v1/orders
X-AccountNumber: 10001
Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7
Content-Type: application/json

{"ticker": "ARS", "side": "CASH_IN", "type": "MARKET", "size": 100000.00}
```

Respuesta `201`:

```json
{"id": 1, "ticker": "ARS", "side": "CASH_IN", "type": "MARKET", "size": 100000.00,
 "status": "FILLED", "datetime": "2026-11-12T04:30:00"}
```

### Comprar acciones a mercado

```http
POST /api/v1/orders
X-AccountNumber: 10001
Idempotency-Key: 1f0c9b6d-3a92-4a44-9d1f-2c1e9b0a5d31
Content-Type: application/json

{"ticker": "YPFD", "side": "BUY", "type": "MARKET", "size": 10}
```

`201` con la orden `FILLED` (o `REJECTED` con motivo si no hay efectivo
suficiente).

### Colocar una orden limitada

```http
POST /api/v1/orders
X-AccountNumber: 10001
Idempotency-Key: 9e8d4b7c-6a1f-4e2b-b3c5-7d0e2f9a1c44
Content-Type: application/json

{"ticker": "YPFD", "side": "BUY", "type": "LIMIT", "size": 5, "price": 7500.00}
```

`201` con la orden `NEW`: queda pendiente y **reserva** el efectivo o las
acciones comprometidos hasta su ejecucion o cancelacion.

### Consultar el historial (paginado y filtrable)

```http
GET /api/v1/orders?page=0&size=20&status=NEW&side=BUY&ticker=YPFD
X-AccountNumber: 10001
```

`200` con una pagina (`content`, `totalElements`, `page`, `size`).

### Cancelar una orden pendiente

```http
DELETE /api/v1/orders/3
X-AccountNumber: 10001
```

`200` con la orden en estado `CANCELLED`; `404` si no existe o pertenece a
otra cuenta (sin revelar su existencia) y `409` si ya no esta pendiente.

### Consultar el portfolio

```http
GET /api/v1/portfolio
X-AccountNumber: 10001
```

`200` con el efectivo total/disponible/reservado, las posiciones con su
cantidad y valorizacion, el costo promedio y los rendimientos, y el valor
total del portfolio.

### Idempotencia

Reintentar el `POST` con la misma `Idempotency-Key` y el mismo cuerpo
devuelve la orden original sin crear una segunda. Con la misma clave y un
cuerpo distinto responde `409 Conflict`.

## Arquitectura

Arquitectura hexagonal: el dominio (`orders`, `portfolio`, `instruments`,
`users`, `shared`) no depende de la infraestructura; la dependencia apunta
hacia adentro a traves de puertos (interfaces de dominio) que los adaptadores
implementan.

```
src/main/java/ar/cocos/challenge/portfolio/
  orders/
    application/   casos de uso (CreateOrderUseCase, CancelOrderUseCase, ...)
    domain/        agregados y reglas (Order, ResourceCalculator, ...)
    infrastructure/ controller, mapper, JPA y adaptadores de puertos
  portfolio/       consulta del estado patrimonial
  instruments/     catalogo de instrumentos y cotizaciones
  users/           cuentas y resolucion de la cabecera
  shared/          errores, validaciones y utilidades transversales
```

Reglas de aislamiento destacadas:

- El contexto `portfolio` nunca consulta la tabla de ordenes directamente:
  consume su propio puerto `ExecutedOrdersPort`, implementado por un adapter
  de `orders`.
- El costo promedio y los rendimientos se calculan en el dominio con un fold
  ordenado (`CostBasisCalculator`), no con agregados SQL.
- Las reservas de efectivo y acciones **se derivan**, no se acumulan: son la
  suma de lo comprometido por las ordenes en estado `NEW`. Cancelar una orden
  libera implicitamente lo que reservaba.

## Decisiones de diseno

1. **Lock pesimista por cuenta (FR-094).** Toda operacion que lee o consume
   recursos toma primero el lock de la fila de la cuenta
   (`SELECT ... FOR NO KEY UPDATE`) y despues calcula los disponibles. Dos
   compras simultaneas que compiten por el mismo saldo se serializan: solo una
   se acepta, y la otra se rechaza por regla de negocio (nunca un error
   tecnico). Lo mismo aplica a ventas, cancelaciones y a la idempotencia.
2. **`lock_timeout` acotado (R-006).** El hint de Hibernate aplica el timeout
   despues de la sentencia de bloqueo; por eso el limite de espera se emite
   con `set_config('lock_timeout', ..., true)` como primera sentencia de cada
   transaccion.
3. **Idempotencia por clave + huella del cuerpo (FR-057 a FR-061).** La clave
   se registra junto a la orden en la misma transaccion, con el hash del
   cuerpo. Reintento igual reemplaza la respuesta; cuerpo distinto responde
   `409`. La clave no se elimina nunca: una orden cancelada sigue siendo
   idempotentemente consultable.
4. **Ordenes rechazadas se persisten (FR-042).** Un rechazo por regla de
   negocio es una orden `REJECTED` con motivo, no un error HTTP: la respuesta
   es `201`.
5. **Atomicidad de orden + clave (FR-060).** La orden se persiste con
   `saveAndFlush` antes de insertar la clave (orden de la FK); cualquier falla
   posterior revierte ambas.
6. **Fechas en UTC (FR-046).** El reloj se inyecta (`ClockProvider`); la zona
   JDBC es UTC y la serializacion es ISO-8601 sin zona.
7. **ModelMapper no instancia records.** Todos los DTOs de respuesta son
   clases con getters/setters, mapeados con `Converter` explicitos.
8. **`BigDecimal` para todo el dinero (FR-096).** La precision se conserva con
   `MathContext.DECIMAL128` y se redondea recien en el borde de la API.

## Supuestos

- Las ordenes `LIMIT` no se ejecutan automaticamente (no hay motor de
  matching): quedan `NEW` hasta que se cancelen.
- Las ordenes `MARKET` se ejecutan al instante al ultimo cierre disponible
  del instrumento; si no hay cotizacion utilizable, la orden se persiste
  `REJECTED` con motivo `NO_MARKET_DATA`.
- Solo se puede operar en cantidades enteras de acciones; operar por importe
  toma la parte entera de acciones que el importe compra.
- Las cuentas (`10001`, `10002`, ...) se cargan por Liquibase como datos de
  referencia; no hay registro de usuarios.
- El efectivo es un instrumento (`ARS`, tipo `MONEDA`); las transferencias
  (`CASH_IN`/`CASH_OUT`) son ordenes sobre ese instrumento.
- El portfolio `ARS` no aparece como posicion: las monedas forman parte del
  resumen de efectivo.

## Pruebas

```powershell
mvn verify
```

Cobertura en tres niveles:

- **Unitarias** (`*Test`): reglas de dominio (dinero, cantidades, estados).
- **De contrato/recursos** (`*IT` con Testcontainers + PostgreSQL real):
  escenarios end-to-end de cada endpoint, incluida la documentacion OpenAPI
  contra el contrato versionado de la especificacion.
- **De concurrencia** (`concurrency/*IT`): compras, ventas, idempotencia y
  cancelaciones simultaneas por HTTP real (puerto aleatorio), y rollback
  atomico de orden + clave de idempotencia.

El analisis estatico (Checkstyle) se ejecuta como parte de `verify` y el
build falla ante cualquier violacion.

## Estructura del proyecto

```
specs/001-investment-portfolio-api/   especificacion: tareas, contratos, quickstart
specs/002-instrument-prices/          mejora con especificacion: tareas, contratos, quickstart
src/main/java/...                     codigo de produccion (hexagonal)
src/main/resources/db/changelog/      migraciones Liquibase (esquema + datos de referencia)
src/test/java/...                     pruebas unitarias, de contrato y de concurrencia
tools/                                scripts de verificacion end-to-end (PowerShell)
docker-compose.yml                    entorno local completo
```
