
---

description: "Task list template for feature implementation"
---

# Tasks: Precios en la consulta de instrumentos

**Input**: Design documents from `/specs/002-instrument-prices/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/instruments-openapi.yaml, quickstart.md

**Tests**: Incluidos. La constitución exige tests unitarios e integración para cada endpoint (Principio III, NO NEGOCIABLE) y la feature 001 ya establece el patrón de tests de contrato con Testcontainers. Los tests se escriben primero y deben FALLAR antes de la implementación.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project** (Maven, arquitectura hexagonal): `src/main/java/ar/cocos/challenge/portfolio/`, `src/test/java/ar/cocos/challenge/portfolio/`, `specs/`
- Contrato principal: `specs/001-investment-portfolio-api/contracts/openapi.yaml` (la feature integra allí el delta de `specs/002-instrument-prices/contracts/instruments-openapi.yaml`)
- Antes de compilar archivos Java nuevos: `python tools/normalize_imports.py`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Estado de partida verificado; no hay inicialización de proyecto (ya existe)

- [ ] T001 [P] Verificar el baseline del proyecto: ejecutar `python tools/normalize_imports.py` y `mvn verify` y confirmar 117 tests en verde y 0 violaciones de Checkstyle sin cambios de código (la feature solo toca el dominio `instruments`, así que el baseline protege el resto de la API)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: El método de obtención de precios en lote, compartido por US1 y US2

⚠️ **CRITICAL**: No user story work can begin until this phase is complete

- [ ] T002 [P] Extender el puerto de dominio `src/main/java/ar/cocos/challenge/portfolio/instruments/domain/MarketDataRepository.java` con `findLatestByInstrumentIds(List<Long> instrumentIds)` que devuelva `Map<Long, LatestQuote>`: la cotización más reciente (mayor `datetime`) de cada instrumento de la lista, omitiendo los identificadores sin cotización (research R-002, data-model.md)
- [ ] T003 Implementar la consulta en lote en `src/main/java/ar/cocos/challenge/portfolio/instruments/infrastructure/JpaMarketDataRepository.java` (JPQL: filas cuyo `datetime` es el máximo de su instrumento y cuyo `instrumentId` está en la lista) e implementar el método nuevo del puerto en `src/main/java/ar/cocos/challenge/portfolio/instruments/infrastructure/MarketDataRepositoryAdapter.java` mapeando `MarketDataEntity` → `LatestQuote` (depende de T002; costo: 1 consulta por request, prohibido el N+1)

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Consultar el catálogo con precios (Priority: P1) 🎯 MVP

**Goal**: `GET /api/v1/instruments` devuelve cada instrumento con su último precio disponible y su cierre anterior; los instrumentos sin cotización aparecen con los campos sin valor.

**Independent Test**: Consultar el listado completo y verificar que `YPFD` muestra `price`/`previousClose` coincidentes con su cotización más reciente del seed, que `ARS` (sin cotizaciones) aparece con solo sus datos básicos y que la solicitud responde 200.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T004 [P] [US1] Test de contrato en `src/test/java/ar/cocos/challenge/portfolio/instruments/InstrumentPricesContractIT.java` — listado completo responde `200`; cada instrumento con cotización incluye `price` igual al cierre de su cotización más reciente del seed (`YPFD` = 7837.50) y `previousClose` igual al `previous_close` de esa fila (7975.05); el instrumento sin cotización (`ARS`, tipo `MONEDA`, sin filas en cotizaciones) aparece con `id`/`ticker`/`name`/`type` y SIN campos de precio; cada ticker aparece exactamente una vez (spec US1, escenarios 1–3; data-model.md, reglas de mapeo)

### Implementation for User Story 1

- [ ] T005 [P] [US1] Agregar los campos `price` y `previousClose` (ambos `BigDecimal`, opcionales) a `src/main/java/ar/cocos/challenge/portfolio/instruments/application/InstrumentResponse.java`, con javadoc documentando que se informan solo si la cotización es utilizable según `LatestQuote.isAvailable()` / `hasPreviousClose()` (research R-003, R-005; los campos actuales no cambian)
- [ ] T006 [US1] Extender el conversor de `src/main/java/ar/cocos/challenge/portfolio/instruments/infrastructure/InstrumentMapper.java` para poblar los campos de precio desde el mapa de cotizaciones, con las reglas verbatim de data-model.md: `quote` ausente → ambos `null`; `price` = `quote.close()` solo si `quote.isAvailable()`; `previousClose` = `quote.previousClose()` solo si `quote.hasPreviousClose()` (depende de T005)
- [ ] T007 [US1] Modificar `src/main/java/ar/cocos/challenge/portfolio/instruments/application/InstrumentQueryUseCase.java` para que, tras resolver el listado o la búsqueda, obtenga las cotizaciones con UNA llamada a `findLatestByInstrumentIds` con los identificadores del resultado y entregue el mapa al mapper (depende de T002–T006; única fuente de verdad, invariantes 1 y 4 de data-model.md)
- [ ] T008 [US1] Integrar el delta al contrato principal `specs/001-investment-portfolio-api/contracts/openapi.yaml`: reemplazar `paths./api/v1/instruments` y `components.schemas.Instrument` por lo definido en `specs/002-instrument-prices/contracts/instruments-openapi.yaml` (`price` y `previousClose` opcionales, fuera de `required`, con ejemplos; descripción actualizada del endpoint) (depende de T004–T007; Principio VI)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Precios también en los resultados de búsqueda (Priority: P2)

**Goal**: Las búsquedas por texto devuelven los mismos instrumentos que hoy, con los campos de precio incluidos.

**Independent Test**: Buscar `ypf` (case-insensitive) y verificar que los resultados coinciden con el mecanismo actual y que cada resultado incluye `price`/`previousClose`; una búsqueda sin coincidencias sigue devolviendo lista vacía.

### Tests for User Story 2 ⚠️

- [ ] T009 [P] [US2] Extender `src/test/java/ar/cocos/challenge/portfolio/instruments/InstrumentPricesContractIT.java` — búsqueda case-insensitive por ticker (`query=ypf`) devuelve `YPFD` con campos de precio; búsqueda por nombre (`query=macro`) devuelve resultados con campos de precio; búsqueda sin coincidencias (`query=zzzzz`) devuelve `[]`; los instrumentos devueltos son idénticos a los del mecanismo actual (spec US2, escenarios 1–2; FR-004)

### Implementation for User Story 2

- [ ] T010 [US2] Verificar que el enriquecimiento de T007 en `src/main/java/ar/cocos/challenge/portfolio/instruments/application/InstrumentQueryUseCase.java` cubre el camino de búsqueda (punto único de integración después de resolver listado o búsqueda); si la implementación solo enriquece el listado completo, extenderla para que la búsqueda también pase por el mapa (esperado: sin código adicional; el test de T009 es la verificación) (depende de T007, T009)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Comportamiento y estructura conservados (Priority: P3)

**Goal**: Compatibilidad con los clientes existentes: campos actuales intactos, validaciones y errores sin cambios, contrato publicado consistente.

**Independent Test**: Repetir las consultas y errores existentes (query vacío → 400 con estructura unificada; dedup; paths del contrato) y verificar que no cambiaron, más el schema `Instrument` del runtime con los campos nuevos opcionales.

### Tests for User Story 3 ⚠️

- [ ] T011 [P] [US3] Verificación de regresión en `src/test/java/ar/cocos/challenge/portfolio/instruments/InstrumentQueryContractIT.java` y `src/test/java/ar/cocos/challenge/portfolio/instruments/InstrumentCatalogIT.java` — `query` vacío o solo espacios responde `400` con la estructura de error unificada (`ErrorResponse`: timestamp, status, error, message, path); resultados sin duplicados; casos existentes pasan SIN modificaciones (si falta algún caso explícito, agregarlo en el mismo archivo) (spec US3, escenarios 1–2; FR-004, FR-007)

### Implementation for User Story 3

- [ ] T012 [US3] Extender `src/test/java/ar/cocos/challenge/portfolio/contracts/OpenApiContractIT.java` — el schema `Instrument` publicado en runtime (`/v3/api-docs`) declara `price` y `previousClose` y los dos quedan FUERA de `required`; el conjunto de paths del runtime sigue coincidiendo con el contrato principal actualizado (depende de T008; Principio VI)

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Cierre documental y verificación final

- [ ] T013 [P] Actualizar `README.md`: describir los campos `price`/`previousClose` del catálogo en la sección de API y agregar un ejemplo de respuesta del listado con precios (Principio VII: documentación al día)
- [ ] T014 Ejecutar la validación completa de `specs/002-instrument-prices/quickstart.md` contra la aplicación en marcha (`docker compose up -d`, escenarios 4.1–4.5 incluido el invariante "el price del catálogo == el price de una compra a mercado") y registrar los resultados en el resumen de la fase (depende de T004–T012)
- [ ] T015 Ejecutar `./mvnw verify`: compila, suite completa en verde (117 tests previos + los nuevos), 0 violaciones de Checkstyle; marcar como completadas las tareas de este archivo al cierre de cada fase (final)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on T001 (baseline) - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - US1 → US2 → US3 (las historias comparten el punto de integración del caso de uso; el orden de prioridad es el orden natural de entrega)
- **Polish (Final Phase)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Depends on T007 (punto de integración del caso de uso) pero es verificable de forma independiente con su propio test (T009)
- **User Story 3 (P3)**: Depende de T008 (contrato) para el test de runtime-vs-contrato (T012); la verificación de regresión (T011) es independiente y puede correr en paralelo

### Within Each User Story

- Tests (T004, T009, T011) MUST be written and FAIL before implementation
- DTO (T005) antes del mapper (T006), antes del caso de uso (T007)
- Contrato (T008) después de la implementación que lo respalda
- Story complete before moving to next priority

### Parallel Opportunities

- T001, T002 pueden correr en paralelo con todo lo de su fase
- T004, T005, T009, T011, T013 están marcados [P] (archivos distintos, sin dependencias)
- Una vez completada la Foundation, US1 se implementa secuencialmente (T005→T006→T007→T008) y los tests de US2/US3 pueden escribirse en paralelo con la implementación de US1

---

## Parallel Example: User Story 1

```text
# En paralelo (archivos distintos):
Task: "Test de contrato en src/test/java/.../instruments/InstrumentPricesContractIT.java"  (T004)
Task: "Campos price/previousClose en src/main/java/.../instruments/application/InstrumentResponse.java"  (T005)

# Secuencial (dependencias):
T006 (mapper, depende de T005) → T007 (use case, depende de T002–T006) → T008 (contrato)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001 baseline en verde)
2. Complete Phase 2: Foundational (T002–T003, consulta en lote)
3. Complete Phase 3: User Story 1 (T004–T008)
4. **STOP and VALIDATE**: `mvn verify` + verificar el listado con precios contra la app en Docker
5. El catálogo ya entrega valor completo para el flujo principal

### Incremental Delivery

1. Setup + Foundational → base lista
2. US1 → listado con precios (MVP)
3. US2 → búsquedas con precios (una verificación adicional, casi sin código)
4. US3 → compatibilidad y contrato verificados (garantía de despliegue seguro)
5. Polish → documentación y validación quickstart end-to-end

### Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Cada historia es completable y testeable de forma independiente
- Los tests se escriben primero y deben fallar antes de implementar
- El delta de contrato (`contracts/instruments-openapi.yaml`) se integra al contrato principal en T008; el archivo delta queda como referencia del diseño
- Validador de tareas: `python tools/validate_tasks.py` valida el tasks.md de la feature 001; este archivo se verifica manualmente (formato y secuencia) al cierre de cada fase
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
