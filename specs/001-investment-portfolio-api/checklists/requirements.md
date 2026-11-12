# Specification Quality Checklist: API de Inversiones — Portfolio, Instrumentos, Órdenes e Idempotencia

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-12
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Validación ejecutada el 2026-11-12: los 16 ítems pasan (16/16).
- La especificación deriva del documento de requerimientos "Challenge Backend Java" y es la **única
  funcionalidad vigente** del proyecto. Cubre de punta a punta: portfolio, instrumentos, órdenes,
  transferencias de efectivo, idempotencia e historial. Aspectos destacados del alcance:
  - Los rechazos por regla de negocio **se persisten** como `REJECTED` con motivo y responden `201 Created`.
  - Idempotencia obligatoria mediante cabecera `Idempotency-Key` y registro de claves.
  - Historial de órdenes con paginación y filtros combinables.
  - Endpoint de portfolio con efectivo total / disponible / reservado y rendimientos.
  - Identificación por `amount` (cantidad máxima de acciones enteras) para `BUY` y `SELL`.
  - Identificación de cuenta mediante la cabecera `X-AccountNumber`.
  - Transferencias de efectivo como órdenes `CASH_IN` / `CASH_OUT` sobre el instrumento ARS.
- Se resolvieron 3 clarificaciones (sesión 2026-11-12), registradas en la sección `Clarifications` de la spec:
  - **Q-01**: el efectivo total incluye compras y ventas ejecutadas, no solo `CASH_IN`/`CASH_OUT`.
    Justificación: es la única lectura coherente con §6.4 (efectivo disponible) y §11.6 (patrimonio total);
    de lo contrario comprar acciones sumaría valor al patrimonio sin haberlas pagado. Reflejado en FR-068,
    BR-008 y SC-004.
  - **Q-02**: el instrumento ARS (tipo `MONEDA`) **sí** aparece en el catálogo de instrumentos. Reflejado en
    FR-014, FR-014b y en el escenario de aceptación 1 de la User Story 4.
  - **Q-03**: un reintento idempotente devuelve `201 Created` con el mismo cuerpo y el mismo identificador de
    orden que la primera llamada. Reflejado en FR-057 y SC-006.
- La stack tecnológica (Java 21, Spring Boot 4, PostgreSQL, Liquibase, Maven, Testcontainers, Docker Compose)
  se excluyó del cuerpo de la especificación por tratarse de decisiones de implementación ya fijadas en el
  documento de requerimientos y en la constitución del proyecto; solo se conserva como alcance de la
  descripción de entrada. El plan técnico (`/speckit.plan`) resolverá esos detalles.
- Los requisitos funcionales, las reglas de negocio y los criterios de éxito están numerados
  (FR-001…FR-097, BR-001…BR-037, SC-001…SC-017) para facilitar la trazabilidad en `/speckit.plan` y
  `/speckit.tasks`.
- Sin dependencias externas pendientes: la base de datos de referencia, sus datos de ejemplo y las
  cotizaciones precargadas alcanzan para implementar y probar la funcionalidad.
