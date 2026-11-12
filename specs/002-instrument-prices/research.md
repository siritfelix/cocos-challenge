# Research: Precios en la consulta de instrumentos

**Feature**: 002-instrument-prices | **Date**: 2026-11-12

Investigación de decisiones técnicas para extender `GET /api/v1/instruments` con precios.

---

## R-001: Fuente de verdad de los precios y definición de "último precio" y "cierre anterior"

**Decision**: Ambos valores provienen del puerto de dominio `MarketDataRepository` del contexto `instruments`, concretamente de la cotización **más reciente** del instrumento (la fila con mayor `datetime`). El "último precio disponible" es el `close` de esa cotización; el "precio de cierre anterior" es el `previousClose` que esa misma cotización referencia.

**Rationale**: Es la misma fuente que ya usa la ejecución de órdenes a mercado (`OrderPricingPolicy.resolveMarketPrice`) y la valuación de posiciones del portfolio (`PortfolioQueryUseCase`). Reutilizarla garantiza que catálogo, ejecución y valuación nunca muestren precios distintos entre sí (única fuente de verdad, supuesto documentado en la spec). `LatestQuote` ya transporta exactamente esos dos valores y ya define cuándo son utilizables (`isAvailable`, `hasPreviousClose`), así que no hay que reinventar reglas.

**Alternatives considered**:
- Consultar la tabla de cotizaciones con una query propia desde el mapper/controller: rompe la separación hexagonal (el controller no toca persistencia) y duplica la definición de "cótización más reciente".
- Obtener precios de un servicio externo: fuera de alcance (la spec lo excluye) y agregaría una dependencia nueva.

---

## R-002: Estrategia de obtención de precios para el catálogo completo (N+1 vs. lote)

**Decision**: Agregar un método **en lote** al puerto `MarketDataRepository` que devuelva la última cotización de cada instrumento a partir de una lista de identificadores, y que el caso de uso lo invoque **una sola vez** con todos los instrumentos del resultado. El precio de cada instrumento se adjunta desde ese mapa.

**Rationale**: El catálogo tiene ~66 instrumentos y crecerá. La alternativa de llamar al método existente `findLatestByInstrumentId` por cada instrumento ejecutaría 1 consulta por instrumento (N+1): la consulta completa pasaría de ~2 consultas a ~68, degradando `SC-006` sin beneficio. La consulta en lote mantiene el costo en **1 consulta adicional** por request, independiente del tamaño del catálogo. La implementación es una variante de la consulta existente `findFirstByInstrumentIdOrderByDatetimeDesc`, pero agrupada por instrumento, en el adaptador del mismo dominio (sin dependencias cruzadas: Principio V intacto).

**Alternatives considered**:
- N+1 con el método existente: código mínimo, pero degradación medible y anti-patrón conocido.
- JOIN en la consulta de instrumentos (fetch de cotizaciones junto al catálogo): mezcla dos agregados en una query del repositorio de instrumentos y complica el mapper por entity gráfica; el método en lote del puerto de cotizaciones es más simple y local.
- Cache de cotizaciones: fuera de alcance (la spec excluye actualización de cotizaciones y agrega complejidad de invalidación).

---

## R-003: Forma aditiva del DTO de respuesta y tratamiento de valores sin precio

**Decision**: Agregar dos campos a la respuesta del instrumento: `price` y `previousClose`, ambos **opcionales** (sin valor cuando no hay cotización utilizable). La serialización ya omite los campos nulos del resto de las respuestas de la API (verificado: una orden `NEW` no expone `rejectionReason`), así que el comportamiento será consistente: el cliente que no use los campos nuevos no se ve afectado.

**Rationale**: Es exactamente lo que pide la spec: mejora aditiva (FR-006), instrumentos sin cotización responden exitosamente con campos de precio sin valor (FR-005). Null en Java + omisión en JSON es el patrón ya establecido en la API.

**Alternatives considered**:
- Devolver `0` cuando no hay precio: ambiguo (¿precio cero real o "no hay dato"?) y mentiroso para el consumidor.
- Excluir al instrumento del listado cuando no tiene cotización: contradice FR-005 y cambiaría resultados de búsqueda.
- DTO nuevo (p. ej. `InstrumentWithPriceResponse`): duplicaría el contrato de la respuesta; el enunciado pide mantener la estructura general.

---

## R-004: Naming de los campos de precio

**Decision**: `price` (último precio disponible) y `previousClose` (precio de cierre anterior).

**Rationale**: `price` ya es el nombre del campo de precio en `OrderResponse` (precio al que se ejecutó una orden) y `previousClose` es el vocabulario que la especificación original usa para el rendimiento diario; `PositionResponse` ya usa `currentPrice` para el precio de valuación intradía. Usar `price` para "el precio de referencia del instrumento" y `previousClose` para el cierre anterior mantiene el diccionario de la API coherente sin renombrar nada existente.

**Alternatives considered**:
- `currentPrice` como en `PositionResponse`: sería preciso pero introduce un tercer nombre para el precio en la API; en el contexto del catálogo "el precio" es sinónimo del último cierre disponible, y `price` es el término más simple.
- `lastClose`/`prevClose`: vocabulario técnico no usado en ningún contrato actual.

---

## R-005: Cotizaciones no utilizables (close nulo, cero o negativo; previousClose no positivo)

**Decision**: `price` se informa solo si la cotización más reciente es utilizable según la regla existente (`LatestQuote.isAvailable`: cierre no nulo y estrictamente positivo). `previousClose` se informa solo si es un valor válido según la regla existente (`hasPreviousClose`: estrictamente positivo). Un campo no utilizable queda sin valor.

**Rationale**: Reutiliza las validaciones de dominio ya implementadas y probadas, que existen precisamente porque un precio cero o negativo "no es un dato" para la API (FR-028 y FR-075 de la spec original). Nota: en el esquema actual `close` y `previous_close` son `NOT NULL` y el seed nunca carga valores no positivos, así que en la práctica los campos siempre estarán presentes para instrumentos con cotización; la regla es defensa de dominio y mantiene el comportamiento idéntico al del resto de la API ante datos anómalos.

**Alternatives considered**:
- Informar el valor crudo aunque sea inválido: expondría precios sin sentido (0.00) que ningún otro endpoint reporta.
- Rechazar el instrumento: contradice FR-005.

---

## R-006: Actualización del contrato OpenAPI (Principio VI)

**Decision**: Actualizar el contrato versionado de la API con el schema `Instrument` extendido (campos `price` y `previousClose` opcionales, tipo number, sin mínimo exigido) y verificar con tests que el OpenAPI en runtime declare los campos nuevos (patrón `OpenApiContractIT` de la feature 001). El contrato de esta feature (`contracts/instruments-openapi.yaml`) documenta el delta del path `/api/v1/instruments` y su schema.

**Rationale**: La constitución exige respuestas HTTP consistentes y el proyecto ya tiene la infraestructura de verificación runtime-vs-contrato; reutilizarla evita divergencia entre lo documentado y lo publicado.

**Alternatives considered**:
- Contrato separado definitivo (reemplazar el de 001): la integración al contrato principal pertenece a la fase de implementación/tasks; aquí se especifica el delta.
- No documentar: violaría el Principio VI y dejaría al cliente sin contrato de los campos nuevos.
