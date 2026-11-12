# Data Model: Precios en la consulta de instrumentos

**Feature**: 002-instrument-prices | **Date**: 2026-11-12

No hay cambios de esquema ni migraciones nuevas (FR-008). Este documento describe las entidades involucradas en la lectura y el enriquecimiento del catálogo.

---

## Entidades de dominio

### Instrument (existente, sin cambios)

Elemento del catálogo. No cambia su estructura ni su persistencia.

| Campo | Tipo | Descripción |
|---|---|---|
| id | Long | Identificador interno |
| ticker | String | Símbolo (único) |
| name | String | Nombre descriptivo |
| type | InstrumentType | `ACCIONES` \| `MONEDA` |

### LatestQuote (existente, sin cambios)

Proyección de la **cotización más reciente** de un instrumento. Es el valor que alimenta los campos nuevos.

| Campo | Tipo | Descripción | Regla de uso |
|---|---|---|---|
| close | BigDecimal | Último cierre disponible → `price` | Utilizable solo si no nulo y > 0 (`isAvailable`) |
| previousClose | BigDecimal | Cierre anterior de la misma cotización → `previousClose` | Utilizable solo si > 0 (`hasPreviousClose`) |
| datetime | LocalDateTime | Instante de la cotización | Define la "más reciente" (mayor `datetime`) |

Relación: `marketdata.instrument_id → instruments.id` (FK existente). Para esta feature solo importa la fila con mayor `datetime` **por cada** instrumento del resultado del catálogo/búsqueda.

### Puerto de cotizaciones: `MarketDataRepository` (extendido)

| Método | Estado | Semántica |
|---|---|---|
| `findLatestByInstrumentId(instrumentId)` | existente | Última cotización de UN instrumento (la usa la ejecución de órdenes a mercado) |
| `findLatestByInstrumentIds(instrumentIds)` | **nuevo** | Última cotización de CADA instrumento de la lista, devuelta como mapa `instrumentId → LatestQuote`. Una sola consulta; garantiza 1 query adicional por request (SC-006, research R-002) |

---

## DTO de respuesta: `InstrumentResponse` (extendido, aditivo)

| Campo | Tipo | Estado | Descripción |
|---|---|---|---|
| id | Long | existente | Identificador interno |
| ticker | String | existente | Símbolo |
| name | String | existente | Nombre |
| type | InstrumentType | existente | Tipo |
| **price** | BigDecimal | **nuevo, opcional** | Último precio disponible (cierre de la cotización más reciente); sin valor si no hay cotización utilizable |
| **previousClose** | BigDecimal | **nuevo, opcional** | Precio de cierre anterior de esa cotización; sin valor si no es utilizable |

Reglas de mapeo (research R-005):

```
quote = findLatestByInstrumentIds(ids).get(instrument.id)   # puede no existir
response.price         = quote != null && quote.isAvailable()  ? quote.close()         : null
response.previousClose = quote != null && quote.hasPreviousClose() ? quote.previousClose() : null
```

Los campos nulos se omiten en la serialización JSON, igual que el resto de los DTOs de la API.

---

## Invariantes

1. **Única fuente de verdad**: `price` del catálogo == `close` de la cotización más reciente == precio de ejecución de una orden a mercado == `currentPrice` de la valuación del portfolio. Ningún cálculo nuevo.
2. **Aditivo**: ningún campo actual cambia de nombre, tipo o significado.
3. **Homogéneo**: listado completo y resultados de búsqueda aplican el mismo mapeo (FR-003).
4. **N+1 prohibido**: la obtención de precios del catálogo es una consulta en lote, no una por instrumento (SC-006).
5. **Sin estado**: la consulta es de solo lectura; no hay transiciones de estado ni escrituras.

## Validaciones (sin cambios)

- `query` vacío o solo espacios → error de solicitud (400) con la estructura de error unificada (Principio II, comportamiento vigente).
- Sin coincidencias → 200 con listado vacío (comportamiento vigente).
