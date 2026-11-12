# Implementation Plan: API de Inversiones — Portfolio, Instrumentos, Órdenes e Idempotencia

**Branch**: `001-investment-portfolio-api` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-investment-portfolio-api/spec.md`

## Summary

Se implementa una API REST que administra un portfolio de inversiones derivado íntegramente de un ledger de
órdenes: sin tablas de saldos ni de posiciones. La especificación cubre catálogo de instrumentos, creación de
órdenes de compra/venta (`MARKET`/`LIMIT`), transferencias de efectivo (`CASH_IN`/`CASH_OUT`), historial
paginado con filtros, cancelación de órdenes pendientes, consulta del portfolio con rendimientos, e
idempotencia obligatoria en toda creación de orden.

La aproximación técnica es arquitectura hexagonal por dominio (`users`, `instruments`, `orders`, `portfolio`,
`shared`) sobre Spring Boot 4.1 / Spring Data JPA 4 / PostgreSQL 14+ con esquema gestionado por Liquibase.
La consistencia se apoya en tres pilares: **bloqueo pesimista de la fila de la cuenta** como mutex de
serialización por cuenta (adquirido como primera sentencia de la transacción, en `READ COMMITTED`), **agregados
SQL** para saldos y cantidades, y **restricciones `UNIQUE`** como red de seguridad de la idempotencia. El costo
promedio de adquisición, en cambio, **no** es un agregado: requiere un fold ordenado en Java sobre las órdenes
ejecutadas de cada par (cuenta, instrumento).

## Technical Context

**Language/Version**: Java 21 (LTS) — verificado: JDK 21.0.2 instalado

**Primary Dependencies**: Spring Boot 4.1.2 (Spring Framework 7.0.10, Spring Data JPA 4.x, Hibernate ORM 7.4.13,
Jackson 3.1) · `spring-boot-starter-webmvc` · `spring-boot-starter-validation` (**a agregar**, R-001) ·
`spring-boot-starter-data-jpa` · `spring-boot-starter-liquibase` (**a agregar**, R-002) · PostgreSQL driver
42.7.4 · springdoc-openapi-starter-webmvc-ui 3.2.0 (verificado compatible con Boot 4.1.2) · ModelMapper 3.2.8
(**subir desde 3.2.4**, R-003) · Lombok

**Storage**: PostgreSQL 14+ — esquema `public`, 5 tablas (`users`, `instruments`, `orders`, `marketdata`,
`idempotency_keys`), migraciones Liquibase, `ddl-auto: none`

**Testing**: JUnit 5 (Jupiter 6.0.3 vía Boot 4.1.2) · Mockito 5.23.0 · AssertJ 3.27.7 · MockMvc
(`spring-boot-starter-webmvc-test`) · Testcontainers 2.0.5 (`testcontainers-postgresql`,
`testcontainers-junit-jupiter`) · `spring-boot-testcontainers` para `@ServiceConnection`

**Target Platform**: Linux server / Docker; aplicación web REST sobre HTTP

**Project Type**: web-service (API REST backend)

**Performance Goals**: 95% de portfolio en < 1 s; 95% de órdenes confirmadas o rechazadas en < 2 s
(spec SC-001, SC-002). Sin requisitos de throughput sostenido: el modelo de negocio serializa por cuenta.

**Constraints**: Cero recursos negativos (efectivo disponible, acciones disponibles, posiciones) bajo concurrencia
(FR-038). Atomicidad total de orden + clave de idempotencia (FR-060). Precisión decimal exacta: sin
`double`/`float` (FR-096). Sin modificar tablas de referencia más allá de lo justificado.

**Scale/Scope**: 5 endpoints REST · 4 dominios · ~4 tablas de negocio · ~58 escenarios de aceptación ·
~37 reglas de negocio · ~17 criterios de éxito. Volumen de datos: el seed de referencia (decenas de
instrumentos, miles de cotizaciones).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Aplicación en esta feature | Veredicto |
|---|---|---|
| **I. Código limpio, legible y documentado** | Nombres en inglés, documentación funcional en español; value objects `ShareQuantity` / `CashAmount` para evitar mezclar semánticas de `size` (R-016); constantes y enums para todo valor de negocio; Javadoc en casos de uso y reglas no obvias; `checkstyle`/`spotless` como parte del build | ✅ Cumple |
| **II. Validación de entradas obligatoria** | Bean Validation en todos los DTOs de request (`@Valid` + `@RequestBody`); reglas de dominio para `LIMIT` requiere precio / `MARKET` lo prohíbe, `size` vs `amount` mutuamente excluyentes, tamaño entero positivo; errores de validación → `400` con detalle por campo; parámetros de paginación y filtros validados; `BigDecimal` con escala y redondeo explícitos | ✅ Cumple — **requiere `spring-boot-starter-validation`** (R-001) |
| **III. Tests unitarios e integración por endpoint (NO NEGOCIABLE)** | Tres niveles: unitarios (dominio, VO, hash, cálculos), integración `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers PostgreSQL real por endpoint, y concurrencia con dos peticiones simultáneas. Se cubren `200`/`201`/`400`/`404`/`409` y rechazos de negocio persistidos. Test funcional de envío de órdenes exigido por la spec (SC-015) | ✅ Cumple |
| **IV. Persistencia JPA + PostgreSQL + Liquibase** | Acceso por Spring Data JPA exclusivamente; esquema con changelog Liquibase versionado (`db/changelog/` con master + cambios idempotentes); `ddl-auto: none`; saldos y posiciones mediante **consultas agregadas** (SQL nativo con `FILTER`) evitando N+1; operaciones de escritura transaccionales y consistentes; seed de datos por changelog reproducible | ✅ Cumple — **requiere `spring-boot-starter-liquibase`** (R-002) |
| **V. Arquitectura hexagonal por dominio** | Cuatro contextos (`users`, `instruments`, `orders`, `portfolio`) + `shared`, cada uno con `application`/`domain`/`infrastructure`; el dominio no conoce Spring ni JPA; comunicación entre dominios por puertos; controllers delgados sin reglas de negocio; mapeo con ModelMapper (no MapStruct) | ✅ Cumple |
| **VI. Respuestas HTTP consistentes** | Contrato uniforme y versionado (`/api/v1`); estructura de error común (`timestamp`, `status`, `error`, `message`, `path`); códigos semánticos `200`/`201`/`400`/`404`/`409`/`500`; manejo centralizado con `@RestControllerAdvice`; sin stack traces; payloads `camelCase`; fechas ISO-8601 sin zona horaria; OpenAPI como parte del entregable (`contracts/openapi.yaml` + springdoc) | ✅ Cumple — requiere fijar 2 defaults de Jackson 3 (R-014) |
| **VII. Configuración segura, sin secretos hardcodeados** | Credenciales y URL de BD por variables de entorno con `.env.example`; perfiles `local`/`test`; sin secretos en el repo; logs sin payloads sensibles | ✅ Cumple |

### Desviaciones y puntos de atención

1. **Principio VI vs. spec FR-046 / D-021** — La constitución indica "fechas ISO-8601 **con zona horaria**"; la
   spec exige ISO-8601 **sin** zona horaria (`2026-11-12T04:30:00`, convención UTC, `LocalDateTime` +
   `TIMESTAMP WITHOUT TIME ZONE`). **La spec prevalece**: el requisito de negocio es explícito y la constitución
   cede ante la decisión de diseño confirmada D-021/D-049. Se cumple con `LocalDateTime` y
   `spring.jackson.datatype.datetime.write-dates-as-timestamps=false` (R-011).

2. **Principio VI vs. spec §14** — La constitución menciona `422` para regla de negocio incumplida; la spec
   establece que los rechazos de negocio **no son errores HTTP**: se persisten como `REJECTED` y responden
   `201 Created` (FR-042). **La spec prevalece**: `422` no se usa en esta API.

3. **Principio IV vs. modelo de datos** — La constitución lista `users`, `instruments`, `orders`, `marketdata`;
   la spec agrega `idempotency_keys` como quinta tabla. Es una extensión justificada por FR-054…FR-061 y
   D-043, y queda documentada en `data-model.md`.

4. **Principio IV vs. `database.sql` de referencia** — El esquema de referencia usa `SERIAL`, nombres de columna
   en camelCase y `date DATE`, y no coincide con la spec (BIGINT identity, `snake_case`, `TIMESTAMP`). El
   esquema autoritativo es el de la spec (R-019).

5. **Principio I vs. columnas `size`/`amount` de la request** — La spec (D-011) exige que el request lleve
   exactamente uno de `size` o `amount`. El dominio los unifica en un único concepto de "cantidad solicitada"
   con dos representaciones de entrada, resuelto en el borde por un value object (R-016).

**Veredicto global: PASS.** No hay violaciones sin justificar. Las 5 desviaciones anteriores son resueltas a
favor de la spec por ser requisitos de negocio explícitos, y ninguna requiere excepción formal.

### Re-evaluación post-diseño (Phase 1)

Tras definir el modelo de datos, el contrato HTTP y la guía de validación, se reevalúa el gate:

| Principio | Verificación post-diseño | Estado |
|---|---|---|
| I | Value objects `ShareQuantity`/`CashAmount` definidos en `data-model.md`; enums sin `toString()` sobrescrito; reglas de derivación documentadas | ✅ |
| II | DTOs de request con Bean Validation; las 6 reglas de validación de entrada enumeradas en `data-model.md` §3; `400` con detalle por campo | ✅ |
| III | Tres niveles de test definidos con sus rutas; criterios observables en `quickstart.md` §6 | ✅ |
| IV | 5 tablas Liquibase, 5 índices, consultas agregadas con `FILTER` y `COALESCE`, seed por changelog | ✅ |
| V | 4 dominios + `shared`, con la regla de dependencia `portfolio` → puerto de `orders` explícita | ✅ |
| VI | Contrato OpenAPI 3.1 con 4 paths, 11 schemas, 3 respuestas de error uniforme; 2 defaults de Jackson 3 fijados (R-014) | ✅ |
| VII | Perfiles `local`/`test` y secretos por variable de entorno, a definir en la fase de implementación | ✅ (pendiente de materializar en `application-*.yaml`) |

**Veredicto post-diseño: PASS.** Sin observaciones nuevas.

## Project Structure

### Documentation (this feature)

```text
specs/001-investment-portfolio-api/
├── plan.md              # Este archivo
├── spec.md              # Especificación funcional (entrada de /speckit.plan)
├── research.md          # Decisiones técnicas de Phase 0
├── data-model.md        # Phase 1: modelo de datos, entidades, invariantes
├── quickstart.md        # Phase 1: guía de validación end-to-end
├── contracts/
│   └── openapi.yaml     # Phase 1: contrato HTTP versionado
└── checklists/
    └── requirements.md  # Checklist de calidad de la spec
```

### Source Code (repository root)

Estructura hexagonal por dominio, conforme al Principio V de la constitución. Paquete raíz
`ar.cocos.challenge.portfolio`.

```text
src/
├── main/
│   ├── java/ar/cocos/challenge/portfolio/
│   │   ├── PortfolioApplication.java
│   │   ├── users/
│   │   │   ├── application/          # UserResolver (puerto de entrada), AccountQueryPort
│   │   │   ├── domain/               # Account, AccountRepository (interfaz), AccountNotFoundException
│   │   │   └── infrastructure/       # UserEntity, JpaUserRepository, UserRepositoryAdapter, UserMapper
│   │   ├── instruments/
│   │   │   ├── application/          # InstrumentQueryUseCase, ports
│   │   │   ├── domain/               # Instrument, InstrumentType, InstrumentRepository, LatestQuote
│   │   │   └── infrastructure/       # InstrumentEntity, MarketDataEntity, repos, mapper
│   │   ├── orders/
│   │   │   ├── application/          # CreateOrderUseCase, CancelOrderUseCase, OrderHistoryUseCase,
│   │   │   │                         #   ports, DTOs de aplicación
│   │   │   ├── domain/               # Order, OrderSide, OrderType, OrderStatus,
│   │   │   │                         #   ShareQuantity, CashAmount, RejectionReason,
│   │   │   │                         #   IdempotencyKey, OrderRepository, IdempotencyRepository,
│   │   │   │                         #   ResourceCalculator, CostBasisCalculator, errores de dominio
│   │   │   └── infrastructure/       # OrderEntity, IdempotencyKeyEntity, repos Spring Data,
│   │   │                             #   OrderMapper, RequestHasher, GlobalExceptionHandler
│   │   ├── portfolio/
│   │   │   ├── application/          # PortfolioQueryUseCase, ports de lectura
│   │   │   ├── domain/               # Portfolio, Position, CashSummary, PerformanceCalculator
│   │   │   └── infrastructure/       # adapter de lectura sobre el puerto de órdenes
│   │   └── shared/
│   │       ├── application/          # ClockProvider (UTC), Pagination, PageResponse
│   │       ├── domain/               # Money, MoneyScale, DomainException base
│   │       └── infrastructure/       # WebConfig, OpenApiConfig, ModelMapperConfig, ClockConfig
│   └── resources/
│       ├── application.yaml
│       ├── application-local.yaml
│       ├── application-test.yaml
│       └── db/changelog/
│           ├── db.changelog-master.yaml
│           ├── changes/             # 0001-schema.yaml, 0002-indexes.yaml, 0003-seed-data.yaml
│           └── data/                # instrument-seed.csv, marketdata-seed.csv, user-seed.csv
└── test/
    ├── java/ar/cocos/challenge/portfolio/
    │   ├── PortfolioApplicationTests.java
    │   ├── support/                  # TestcontainersConfiguration, IntegrationTestBase, fixtures
    │   ├── users/                    # AccountResolverTest (unit)
    │   ├── instruments/              # InstrumentQueryServiceTest (unit)
    │   ├── orders/
    │   │   ├── domain/               # ResourceCalculatorTest, CostBasisCalculatorTest, RequestHasherTest
    │   │   ├── application/          # CreateOrderUseCaseTest (Mockito)
    │   │   └── infrastructure/       # OrderControllerIT, CancelOrderIT, OrderHistoryIT
    │   ├── portfolio/                # PortfolioQueryIT, PerformanceCalculatorTest
    │   └── concurrency/              # ConcurrentOrderIT, ConcurrentIdempotencyIT
    └── resources/
        └── application-test.yaml
```

**Structure Decision**: Estructura hexagonal por dominio (`users`, `instruments`, `orders`, `portfolio`) más un
contexto transversal `shared`, tal como exige el Principio V de la constitución. `orders` es el único contexto
que escribe en `orders` e `idempotency_keys`; `portfolio` es un contexto de **solo lectura** que consume un
puerto de `orders` para obtener las órdenes ejecutadas. Se elige arquitectura hexagonal por dominio (y no por
capas) porque la spec impone dos reglas de dominio con ciclos de cambio distintos que no comparten modelo — el ledger de órdenes, la
consulta de cotizaciones y la derivación de portfolio tienen ciclos de cambio y perfiles de carga distintos
— y porque la constitución la exige de forma explícita.

## Fase 0 → Fase 1: trazabilidad

| Artefacto | Contenido |
|---|---|
| [research.md](./research.md) | 20 decisiones técnicas (R-001…R-020) con alternativas rechazadas y fuentes; resumen de cambios al `pom.xml`; riesgos asumidos |
| [data-model.md](./data-model.md) | 5 entidades, tipos, invariantes, índices, reglas de agregación y diagrama de transiciones de estado |
| [contracts/openapi.yaml](./contracts/openapi.yaml) | Contrato HTTP completo de los 5 endpoints |
| [quickstart.md](./quickstart.md) | Guía de validación end-to-end: prerequisites, arranque, escenarios verificables |

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No hay violaciones que requieran justificación. Se registran a continuación las decisiones de diseño de
complexidad intrínseca que se adopting para resolver requisitos explícitos de la spec, con su alternativa
más simple rechazada y la razón.

| Decisión | Por qué se necesita | Alternativa más simple rechazada porque |
|---|---|---|
| **Fold ordenado en Java para el costo promedio** (R-009) | La spec exige promedio ponderado donde la venta reduce el costo al promedio vigente (D-006, BR-015). Es un fold secuencial, no un conjunto-agregado. | La alternativa simple —`SUM(qty×price) / SUM(qty)` sobre compras ejecutadas— es **incorrecta** cuando hay compras y ventas intercaladas: valúa las ventas al precio de venta en vez de al costo. Contraejemplo en R-009. |
| **Bloqueo pesimista de la fila de la cuenta** (R-004) | La spec exige consistencia bajo concurrencia con saldos derivados (D-022, FR-094) y que las reservas nunca superen los disponibles (FR-037). | Sin bloqueo, dos compras simultáneas leen el mismo agregado y ambas lo consideran suficiente (lectura obsoleta bajo `READ COMMITTED`), produciendo saldo negativo. El bloqueo convierte la lectura en una sección crítica por cuenta. |
| **`SET LOCAL lock_timeout` como primera sentencia** (R-006) | Acota la espera por un lock tomado por otra transacción, evitando que una transacción bloqueada monopolice una conexión del pool. | El hint `jakarta.persistence.lock.timeout` de Hibernate 7 **no** acota la espera (se aplica después de la sentencia de bloqueo; bug spring-data-jpa#4244). Sin el `SET LOCAL`, una transacción puede esperar indefinidamente. |
| **Tabla `idempotency_keys` separada** en vez de columna en `orders` (D-043) | La spec exige que una clave de idempotencia pueda apuntar a una orden y que `UNIQUE(user_id, key)` que garantiza at-most-once (FR-055, FR-059). | Poner la clave como columna en `orders` no permitiría la unicidad por (cuenta, clave) de forma natural, ni el `UNIQUE(order_id)` exigido por la spec (sección 3.5). |
| **Cero tablas de saldos y posiciones** (D-047) | Decisión de diseño explícita de la spec: todo se deriva de las órdenes. | Materializar saldos y posiciones daría lecturas O(1), pero exigiría un segundo mecanismo de escritura sincronizado con el ledger, duplicando la fuente de verdad y contradictorias con la spec. |
