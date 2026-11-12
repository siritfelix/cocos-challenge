# Quickstart: Validación end-to-end de la mejora de precios en instrumentos

**Feature**: 002-instrument-prices | **Date**: 2026-11-12

Guía para verificar que la implementación satisface la [spec](./spec.md). Los detalles de
mapeo y consultas están en [data-model.md](./data-model.md); el delta de contrato HTTP en
[contracts/instruments-openapi.yaml](./contracts/instruments-openapi.yaml).

---

## 1. Prerrequisitos

| Requisito | Versión | Verificación |
|---|---|---|
| JDK | 21 (LTS) | `java -version` |
| Maven | 3.9+ (o el wrapper `./mvnw`) | `mvn -v` |
| Docker | 24+ con daemon activo | `docker info` |

No se requiere instalar PostgreSQL ni Docker Compose: `docker compose up` levanta todo.

---

## 2. Levantar el proyecto

```bash
docker compose up --build
# o, para solo la base + la app en local:
# docker compose up -d postgres && ./mvnw spring-boot:run
```

La API queda en `http://localhost:8080`.

> Los datos de referencia se cargan por Liquibase. El seed incluye instrumentos de tipo
> `ACCIONES` **con** cotizaciones y el instrumento de efectivo `ARS` (tipo `MONEDA`)
> **sin** cotizaciones: con eso bastan los dos estados de precio sin datos preparados.
> Si se corrieron operaciones antes, el catálogo no cambia: esta feature es de solo
> lectura y no depende del estado de las cuentas.

---

## 3. Verificar el arranque

```bash
curl -s http://localhost:8080/v3/api-docs | head -c 200
```

**Esperado**: `{"openapi":"3.1.0",...}` con el schema `Instrument` actualizado
(campos `price` y `previousClose` opcionales).

---

## 4. Escenarios de validación

### 4.1 Listado completo con precios (spec US1)

```bash
curl -s http://localhost:8080/api/v1/instruments
```

**Esperado** (para instrumentos con cotización, p. ej. `YPFD`):

```json
{
  "id": 65,
  "ticker": "YPFD",
  "name": "Y.P.F. S.A.",
  "type": "ACCIONES",
  "price": 7837.50,
  "previousClose": 7975.05
}
```

Invariantes a comprobar:

| Invariante | Cómo se verifica |
|---|---|
| `price` es el cierre de la cotización más reciente | Comparar contra la última fila de cotización del instrumento (mayor `datetime`) |
| `previousClose` es el cierre anterior de esa misma cotización | Comparar contra el `previous_close` de esa fila |
| Los campos básicos (`id`, `ticker`, `name`, `type`) no cambian | Mismos valores que la versión anterior de la API |
| Cada instrumento aparece exactamente una vez | `grep -c '"ticker": "YPFD"'` → 1 |

### 4.2 Instrumento sin cotización (spec US1, edge case)

```bash
curl -s http://localhost:8080/api/v1/instruments | python -m json.tool | grep -A6 '"ARS"'
```

**Esperado**: el instrumento de efectivo aparece con sus datos básicos y **sin** los
campos `price`/`previousClose` (nulos omitidos en el JSON):

```json
{
  "id": 66,
  "ticker": "ARS",
  "name": "Peso argentino",
  "type": "MONEDA"
}
```

La solicitud responde `200` aunque el listado contenga instrumentos sin cotización.

### 4.3 Búsqueda con precios (spec US2)

```bash
# Busqueda parcial por ticker, case-insensitive
curl -s "http://localhost:8080/api/v1/instruments?query=ypf"
# Esperado: los mismos instrumentos que matchea hoy, con price/previousClose incluidos

# Busqueda por nombre
curl -s "http://localhost:8080/api/v1/instruments?query=macro"
# Esperado: resultados vigentes, con price/previousClose

# Sin coincidencias
curl -s "http://localhost:8080/api/v1/instruments?query=zzzzz"
# Esperado: [] (comportamiento actual, sin cambios)
```

### 4.4 Comportamiento de errores conservado (spec US3)

```bash
# Query vacia (solo espacios) -> 400 con estructura de error unificada
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/instruments?query=%20"
# Esperado: 400
```

### 4.5 Consistencia con la ejecución de órdenes (invariante de única fuente de verdad)

```bash
# El price del catalogo debe coincidir con el price de una compra a mercado
KEY="qs-$(date +%s%N)"
CATALOG_PRICE=$(curl -s http://localhost:8080/api/v1/instruments | python -c "import sys,json;print([i['price'] for i in json.load(sys.stdin) if i['ticker']=='YPFD'][0])")
ORDER=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "X-AccountNumber: 10001" -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1}')
echo "catalogo=$CATALOG_PRICE orden=$ORDER"
# Esperado: el price de la orden FILLED es igual al price del catalogo
```

---

## 5. Verificación automatizada

```bash
./mvnw verify
```

Criterios observables que deben cumplirse:

- Los tests de contrato del catálogo (con/sin `query`) pasan incluyendo los campos de precio.
- Un test verifica que el instrumento sin cotización responde `200` sin campos de precio.
- Un test verifica que el `price` del catálogo coincide con la cotización más reciente del seed.
- El contrato OpenAPI en runtime declara `price` y `previousClose` como campos opcionales de `Instrument` (verificación runtime-vs-contrato).
- Checkstyle sin violaciones; sin N+1 verificado por diseño (una consulta en lote por request).

---

## 6. Checklist de la Definición de Terminado

- [ ] `./mvnw verify` compila y pasa en verde.
- [ ] Tests de catálogo con y sin `query`, y del instrumento sin cotización, en verde.
- [ ] Contrato de `/api/v1/instruments` actualizado y consistente entre runtime y `contracts/`.
- [ ] Campos actuales sin cambios; mejora aditiva verificada.
- [ ] Sin migraciones nuevas ni endpoints nuevos.
- [ ] Formato y análisis estático integrados en el build.
