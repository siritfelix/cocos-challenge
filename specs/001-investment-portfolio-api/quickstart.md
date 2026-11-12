# Quickstart: Validación end-to-end de la API de Inversiones

**Feature**: 001-investment-portfolio-api | **Date**: 2026-11-12

Guía para verificar que la implementación satisface la [spec](./spec.md). Cada escenario indica el **resultado
esperado** en términos del contrato, no del código. Los detalles de mapeo y las consultas están en
[data-model.md](./data-model.md); el contrato HTTP completo en [contracts/openapi.yaml](./contracts/openapi.yaml).

---

## 1. Prerrequisitos

| Requisito | Versión | Verificación |
|---|---|---|
| JDK | 21 (LTS) | `java -version` |
| Maven | 3.9+ (o el wrapper `./mvnw`) | `mvn -v` |
| Docker | 24+ con daemon activo | `docker info` |
| PostgreSQL | 14+ (solo si se corre fuera de Docker) | `psql --version` |

No se requiere instalar PostgreSQL ni Docker Compose: `docker compose up` levanta todo.

---

## 2. Levantar el proyecto

### Opción A — Docker Compose (recomendada)

```bash
docker compose up --build
```

La API queda en `http://localhost:8080`. Swagger UI en `http://localhost:8080/swagger-ui.html`.

### Opción B — Local con Maven

```bash
# 1. Base de datos
docker compose up -d postgres

# 2. Aplicación (las migraciones de Liquibase se ejecutan al arrancar)
./mvnw spring-boot:run
```

### Opción C — Solo tests (sin infra manual)

```bash
./mvnw verify
```

Testcontainers levanta un PostgreSQL efímero. Docker debe estar activo.

---

## 3. Verificar el arranque

```bash
curl -s http://localhost:8080/v3/api-docs | head -c 200
```

**Esperado**: `{"openapi":"3.1.0",...}`. Si devuelve `404`, springdoc no está activo. Si el arranque falla con
`Unable to resolve Liquibase changelog` o el esquema no existe, revisar `spring.liquibase.change-log` y que la
dependencia sea `spring-boot-starter-liquibase` (research R-002).

---

## 4. Datos de referencia

El seed de Liquibase carga cuentas e instrumentos. Números de cuenta de ejemplo:

| `X-AccountNumber` | Correo |
|---|---|
| `10001` | emiliano@test.com |
| `10002` | jose@test.com |
| `10003` | francisco@test.com |
| `10004` | juan@test.com |

El instrumento de efectivo es `ARS` (`Peso argentino`, `MONEDA`). Las acciones tienen cotizaciones de referencia
con `close` y `previous_close` distintos, para poder verificar el rendimiento diario.

> Para los escenarios se recomienda usar una cuenta **limpia** (sin operaciones previas) o resetear la base
> entre corridas: `docker compose down -v && docker compose up -d postgres`.

---

## 5. Escenarios de validación

### 5.1 Identificación de cuenta (spec §4, §18.1)

```bash
# 1. Sin cabecera X-AccountNumber → 400
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/portfolio
# Esperado: 400

# 2. Cuenta inexistente → 404
curl -s -o /dev/null -w "%{http_code}\n" -H "X-AccountNumber: 99999" http://localhost:8080/api/v1/portfolio
# Esperado: 404

# 3. Cuenta válida → 200
curl -s -o /dev/null -w "%{http_code}\n" -H "X-AccountNumber: 10001" http://localhost:8080/api/v1/portfolio
# Esperado: 200
```

---

### 5.2 Instrumentos (spec §5, §18.2)

```bash
# Listado completo → 200, incluye ARS
curl -s http://localhost:8080/api/v1/instruments | grep -c '"ARS"'
# Esperado: 1 (aparece exactamente una vez)

# Búsqueda por ticker (case-insensitive)
curl -s "http://localhost:8080/api/v1/instruments?query=ypf" | grep -c 'YPFD'
# Esperado: >= 1

# Búsqueda por nombre
curl -s "http://localhost:8080/api/v1/instruments?query=macro" | grep -c 'BMA'
# Esperado: >= 1

# Sin coincidencias → 200 con lista vacía
curl -s "http://localhost:8080/api/v1/instruments?query=zzzzz"
# Esperado: []

# Query vacío → 400
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/instruments?query=%20"
# Esperado: 400
```

---

### 5.3 Ingreso de efectivo y compra (spec §18.3, §18.5)

```bash
KEY="smoke-$(date +%s%N)"

# 1. CASH_IN de 100.000 → 201 FILLED
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "X-AccountNumber: 10001" -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":100000.00}'
# Esperado: 201, status FILLED, price null, size 100000.00

# 2. BUY MARKET de 10 acciones → 201 FILLED (si hay efectivo y cotización)
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "X-AccountNumber: 10001" -H "Idempotency-Key: $KEY-2" -H "Content-Type: application/json" \
  -d '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":10}'
# Esperado: 201, status FILLED, price = último close, size 10.00
```

---

### 5.4 Rechazos de negocio → `201` con `REJECTED` (spec §6.8)

```bash
# Compra sin efectivo suficiente → 201 + REJECTED (NO 400)
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "X-AccountNumber: 10002" -H "Idempotency-Key: reject-1" -H "Content-Type: application/json" \
  -d '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1000}'
# Esperado: HTTP 201, body con status REJECTED y rejectionReason no vacío
```

Distinción clave a verificar:

| Situación | Código HTTP | ¿Se persiste la orden? |
|---|---|---|
| `size` y `amount` juntos | `400` | No |
| `size` fraccionario | `400` | No |
| `MARKET` con `price` | `400` | No |
| `LIMIT` sin `price` | `400` | No |
| Transferencia con `amount` | `400` | No |
| Saldo insuficiente | `201` | Sí (`REJECTED`) |
| Sin cotización para `MARKET` | `201` | Sí (`REJECTED`) |

```bash
# Errores de request → 400, sin orden persistida
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/orders \
  -H "X-AccountNumber: 10001" -H "Idempotency-Key: bad-1" -H "Content-Type: application/json" \
  -d '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":10,"amount":100}'
# Esperado: 400
```

---

### 5.5 Idempotencia (spec §7, §18.7)

```bash
KEY="idem-$(date +%s%N)"
BODY='{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":1000.00}'

# 1. Primera llamada
R1=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "X-AccountNumber: 10001" -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" -d "$BODY")

# 2. Reintento con la misma clave y mismo contenido → mismo id, misma orden
R2=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "X-AccountNumber: 10001" -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" -d "$BODY")

echo "$R1"; echo "$R2"
# Esperado: ambos cuerpos con el MISMO "id"

# 3. Misma clave con contenido distinto → 409
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/orders \
  -H "X-AccountNumber: 10001" -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":2000.00}'
# Esperado: 409
```

Verificar que existe **una sola** orden tras el reintento:

```bash
curl -s -H "X-AccountNumber: 10001" "http://localhost:8080/api/v1/orders?side=CASH_IN&size=100"
# Esperado: la orden aparece una sola vez
```

---

### 5.6 Órdenes LIMIT, reservas y cancelación (spec §6.6, §9, §18.6)

```bash
KEY="limit-$(date +%s%N)"

# 1. BUY LIMIT → 201 NEW (reserva efectivo)
R=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "X-AccountNumber: 10001" -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":5,"price":20000.00}')
echo "$R"
# Esperado: status NEW

ID=$(echo "$R" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')

# 2. El portfolio refleja la reserva
curl -s -H "X-AccountNumber: 10001" http://localhost:8080/api/v1/portfolio
# Esperado: reservedCash = 100000.00 (5 × 20000), availableCash = totalCash − reservedCash
#            totalCash NO cambió; totalValue NO cambió

# 3. Cancelar → 200 CANCELLED
curl -s -X DELETE "http://localhost:8080/api/v1/orders/$ID" -H "X-AccountNumber: 10001"
# Esperado: status CANCELLED

# 4. La reserva se liberó
curl -s -H "X-AccountNumber: 10001" http://localhost:8080/api/v1/portfolio
# Esperado: reservedCash = 0.00, availableCash = totalCash

# 5. Cancelar de nuevo → 409
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE "http://localhost:8080/api/v1/orders/$ID" -H "X-AccountNumber: 10001"
# Esperado: 409

# 6. Orden inexistente → 404
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE "http://localhost:8080/api/v1/orders/99999999" -H "X-AccountNumber: 10001"
# Esperado: 404

# 7. Orden de otra cuenta → 404 (no revela existencia)
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE "http://localhost:8080/api/v1/orders/$ID" -H "X-AccountNumber: 10002"
# Esperado: 404
```

---

### 5.7 Compra y venta por importe (spec §6.2, §18.3, §18.4)

```bash
# Con un precio de 1.500 y un importe de 10.000 → 6 acciones
# (verificar contra el close real del ticker usado)

# Importe insuficiente para una acción → 201 REJECTED
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "X-AccountNumber: 10001" -H "Idempotency-Key: amt-small" -H "Content-Type: application/json" \
  -d '{"ticker":"YPFD","side":"BUY","type":"MARKET","amount":1.00}'
# Esperado: 201, status REJECTED (cantidad calculada = 0)
```

Verificación del cálculo con el `close` real del instrumento:

```bash
CLOSE=$(curl -s http://localhost:8080/api/v1/instruments | tr ',' '\n' | grep -A3 YPFD | head -1)
# cantidad esperada = floor(amount / close)
```

---

### 5.8 Portfolio (spec §11, §18.9)

```bash
# Cuenta sin operaciones → ceros y lista vacía
curl -s -H "X-AccountNumber: 10004" http://localhost:8080/api/v1/portfolio
# Esperado: totalCash 0.00, availableCash 0.00, reservedCash 0.00, totalValue 0.00, positions []
```

Invariantes a comprobar sobre cualquier portfolio devuelto:

| Invariante | Cómo se verifica |
|---|---|
| `totalValue == totalCash + Σ(marketValue)` | Suma manual de las posiciones |
| `availableCash == totalCash − reservedCash` | Resta manual |
| `availableQuantity == totalQuantity − reservedQuantity` | Resta manual por posición |
| `totalCash` no cambia al crear una orden `LIMIT` | Comparar antes y después |
| `totalValue` no cambia al crear una orden `LIMIT` | Comparar antes y después |

Fórmulas de referencia:

```
rendimientoTotal  = ((marketValue − averageCost×totalQuantity) / (averageCost×totalQuantity)) × 100
rendimientoDiario = ((close − previousClose) / previousClose) × 100
```

---

### 5.9 Historial (spec §12, §18.10)

```bash
# Paginado
curl -s -H "X-AccountNumber: 10001" "http://localhost:8080/api/v1/orders?page=0&size=5"
# Esperado: content, totalElements, totalPages, page, size

# Filtros
curl -s -H "X-AccountNumber: 10001" "http://localhost:8080/api/v1/orders?status=FILLED"
curl -s -H "X-AccountNumber: 10001" "http://localhost:8080/api/v1/orders?side=BUY"
curl -s -H "X-AccountNumber: 10001" "http://localhost:8080/api/v1/orders?ticker=YPFD"

# Combinados
curl -s -H "X-AccountNumber: 10001" "http://localhost:8080/api/v1/orders?status=NEW&side=BUY&ticker=YPFD"

# Página inexistente → 200 con lista vacía
curl -s -o /dev/null -w "%{http_code}\n" -H "X-AccountNumber: 10001" "http://localhost:8080/api/v1/orders?page=99"
# Esperado: 200

# Validaciones → 400
curl -s -o /dev/null -w "%{http_code}\n" -H "X-AccountNumber: 10001" "http://localhost:8080/api/v1/orders?page=-1"
curl -s -o /dev/null -w "%{http_code}\n" -H "X-AccountNumber: 10001" "http://localhost:8080/api/v1/orders?size=101"
curl -s -o /dev/null -w "%{http_code}\n" -H "X-AccountNumber: 10001" "http://localhost:8080/api/v1/orders?status=INVALID"
curl -s -o /dev/null -w "%{http_code}\n" -H "X-AccountNumber: 10001" "http://localhost:8080/api/v1/orders?size=abc"
# Esperado: 400 en los cuatro casos
```

---

### 5.10 Formato de error (spec §14)

```bash
curl -s -H "X-AccountNumber: 99999" http://localhost:8080/api/v1/portfolio
```

**Esperado** (estructura uniforme):

```json
{
  "timestamp": "2026-11-12T04:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Account not found",
  "path": "/api/v1/portfolio"
}
```

Verificar que `timestamp` **no** lleva sufijo `Z` ni offset, y que no aparecen trazas ni detalles internos.

---

## 6. Verificación automatizada

```bash
./mvnw verify
```

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario | Value objects, cálculo de costo promedio, rendimientos, canonicalización del hash, reglas de dominio | `src/test/java/.../orders/domain/`, `.../portfolio/` |
| Integración | Los 5 endpoints de punta a punta contra PostgreSQL real | `src/test/java/.../infrastructure/`, `.../portfolio/` |
| Concurrencia | Compras y ventas simultáneas sobre la misma cuenta; dos peticiones con la misma clave de idempotencia | `src/test/java/.../concurrency/` |

Criterios observables que deben cumplirse:

- Ningún test deja efectivo disponible, acciones disponibles o posiciones en negativo.
- El test de idempotencia concurrente confirma **una sola** orden para una clave repetida.
- El test de concurrencia de compras confirma que la suma de las operaciones aceptadas nunca excede el saldo.
- El portfolio de una cuenta sin operaciones devuelve ceros y lista vacía, sin excepción.

---

## 7. Checklist de la Definición de Terminado (constitución, puerta de calidad)

- [ ] `./mvnw verify` compila y pasa en verde.
- [ ] Tests unitarios e integración de los 5 endpoints en verde.
- [ ] Contrato OpenAPI actualizado y consistente con `contracts/openapi.yaml`.
- [ ] Validaciones y mapeo uniforme de errores implementados.
- [ ] Sin secretos ni credenciales en el código.
- [ ] README y documentación de supuestos actualizados.
- [ ] Formato y análisis estático (`checkstyle`/`spotless`) integrados en el build.
