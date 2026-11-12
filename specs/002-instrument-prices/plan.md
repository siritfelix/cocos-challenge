# Implementation Plan: Precios en la consulta de instrumentos

**Branch**: `002-instrument-prices` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-instrument-prices/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Extender la respuesta de `GET /api/v1/instruments` para que cada instrumento incluya, junto a sus datos básicos, el último precio disponible y el precio de cierre anterior obtenidos de la cotización más reciente registrada del instrumento. La fuente de precios es la misma que ya usa la ejecución de órdenes a mercado y la valuación del portfolio (puerto de cotizaciones del dominio `instruments`), lo que garantiza una única fuente de verdad. La búsqueda y el resto de la respuesta no cambian: la mejora es aditiva y los campos de precio pueden estar sin valor cuando el instrumento no tiene cotización utilizable. No se crean endpoints, tablas ni migraciones nuevas.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 4.1.2 (Web MVC, Data JPA, Validation, Liquibase), springdoc-openapi (Swagger UI / api-docs), PostgreSQL JDBC driver

**Storage**: PostgreSQL 16 (esquema administrado por Liquibase; esta feature NO agrega migraciones)

**Testing**: JUnit 6 + Spring Boot Test, Testcontainers (PostgreSQL efímero), Checkstyle en `mvn verify`

**Target Platform**: Server API REST sobre Linux (Docker) / Windows dev; clientes HTTP del catálogo

**Project Type**: Web service (API REST de un solo módulo Maven, arquitectura hexagonal)

**Performance Goals**: Consulta del catálogo (66 instrumentos en seed) en el mismo orden de magnitud que hoy; idealmente una sola consulta adicional agregada en lote (no N+1)

**Constraints**: Compatibilidad aditiva (los campos actuales no cambian); campos de precio `null`/ausentes cuando no hay cotización utilizable; sin nuevas tablas ni columnas; contrato OpenAPI actualizado y consistente (Principio VI)

**Scale/Scope**: 1 endpoint modificado (`GET /api/v1/instruments`), 4–5 archivos de producción tocados, tests de contrato + recursos nuevos

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Chequeo | Estado |
|---|---|---|
| I. Código limpio, legible y documentado | Cambios acotados al dominio `instruments`; javadoc en clases/métodos nuevos; sin lógica duplicada | ✅ PASS |
| II. Validación de entradas obligatoria | El `query` sigue validándose igual que hoy (vacío/espacios → 400); la nueva lógica no agrega entradas sin validar | ✅ PASS |
| III. Tests unitarios e integración por endpoint (NO NEGOCIABLE) | Tests de contrato + de recursos para catálogo con/sin precios, búsqueda y casos sin cotización | ✅ PASS |
| IV. Spring Data JPA + PostgreSQL + Liquibase | Reutiliza la infraestructura JPA existente; NO agrega migraciones (FR-008: sin tablas ni estructura nueva) | ✅ PASS |
| V. Arquitectura hexagonal por dominio + `shared` | Los precios entran por el puerto `MarketDataRepository` (ya existe); el use case orquesta; el mapper traduce; el controller no toca JPA | ✅ PASS |
| VI. Respuestas HTTP consistentes y códigos correctos | Sin cambios de códigos: 200/400 como hoy; campos nuevos documentados en el contrato OpenAPI y verificados contra el runtime | ✅ PASS |
| VII. Configuración segura sin datos sensibles | No agrega configuración ni credenciales | ✅ PASS |

**Post-design re-check**: El diseño agrega un método en lote al puerto de cotizaciones (dominio `instruments`, adaptador propio). No introduce dependencias cruzadas entre dominios ni lógica de negocio nueva: sigue siendo consulta de lectura dentro del mismo contexto. ✅ PASS (verificado tras completar data-model y contracts)

## Project Structure

### Documentation (this feature)

```text
specs/002-instrument-prices/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── instruments-openapi.yaml
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/java/ar/cocos/challenge/portfolio/instruments/
├── application/
│   ├── InstrumentQueryUseCase.java   # MODIFICAR: enriquece con cotizaciones (en lote)
│   └── InstrumentResponse.java       # MODIFICAR: campos price y previousClose (aditivos)
├── domain/
│   ├── LatestQuote.java              # EXISTE: close + previousClose + validaciones de uso
│   └── MarketDataRepository.java     # MODIFICAR: consulta en lote (puerto del dominio)
└── infrastructure/
    ├── InstrumentController.java     # MODIFICAR: solo firma de mapper si cambia (endpoint igual)
    ├── InstrumentMapper.java         # MODIFICAR: mapea los campos de precio
    ├── JpaMarketDataRepository.java  # MODIFICAR: consulta en lote por lista de instrumentos
    └── MarketDataRepositoryAdapter.java # MODIFICAR: implementa el metodo en lote del puerto

src/test/java/ar/cocos/challenge/portfolio/instruments/
├── InstrumentQueryContractIT.java    # EXISTE: casos de catalogo y busqueda (se extienden)
├── InstrumentCatalogIT.java          # EXISTE: consistencia del catalogo (se extiende)
└── InstrumentPricesContractIT.java   # NUEVO: precios en listado, busqueda y sin cotizacion
```

**Structure Decision**: Estructura hexagonal existente, sin módulos nuevos. La feature es un enriquecimiento de lectura del catálogo dentro del dominio `instruments`: se extienden el caso de uso, el DTO de respuesta, el mapper y el puerto/adaptador de cotizaciones (método en lote para evitar N+1). El controller mantiene su firma: la API visible no cambia salvo los campos nuevos.

## Complexity Tracking

> No hay violaciones de la constitución que justificar: la feature no agrega proyectos, patrones ni capas nuevas.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (ninguna) | — | — |
