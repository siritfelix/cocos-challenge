<!--
Sync Impact Report
==================
Version change: 1.1.1 → 1.1.2 (dependencias de Liquibase y ModelMapper aplicadas en pom.xml)
Modified principles: N/A (sin cambio semántico)
Added sections:
  - Principios Fundamentales (I–VII)
  - Restricciones Técnicas
  - Flujo de Desarrollo y Puertas de Calidad
  - Governance
Removed sections: N/A
Stack verificado contra pom.xml (portfolio):
  - Java 21, Spring Boot 4.1.2, Maven Wrapper
  - spring-boot-starter-data-jpa + spring-boot-starter-webmvc
  - PostgreSQL 42.7.4, Liquibase 4.29.2, springdoc-openapi 3.2.0
  - Lombok, ModelMapper (mapeo entre capas), Testcontainers (PostgreSQL)
Templates requiring updates:
  - .specify/templates/plan-template.md (pendiente: validar que la "Constitution Check" referencie los 7 principios)
  - .specify/templates/spec-template.md (pendiente: incluir criterios de validación de entradas y contratos HTTP)
  - .specify/templates/tasks-template.md (pendiente: agregar tareas de tests unitarios + integración por endpoint)
Follow-up TODOs:
  - Ninguno pendiente sobre el pom.xml: se eliminaron `mapstruct.version`/`lombok-mapstruct.version`,
    se agregó `org.modelmapper:modelmapper` (3.2.4) y `org.liquibase:liquibase-core` (4.29.2).
-->

# Constitución del Proyecto — cocos-challenge-backend

## Principios Fundamentales

### I. Código Limpio, Legible y Documentado

El código es el principal artefacto de comunicación del equipo y debe poder entenderse sin
explicaciones externas.

- Nombres intencionales y en inglés para el código (clases, métodos, variables, paquetes);
  la documentación funcional puede redactarse en español.
- Métodos cortos y con una única responsabilidad; se evita el anidamiento profundo y la
  duplicación (DRY) sin caer en abstracciones prematuras.
- Se prohíben los "números y strings mágicos": todo valor de negocio se expresa como constante,
  enum o configuración.
- Todo endpoint, regla de negocio no obvia y decisión de diseño relevante se documenta
  (Javadoc en APIs públicas, README y ADRs cuando corresponda).
- Los comentarios explican el **por qué**, nunca el **qué** evidente del código.
- Se aplica formato y análisis estático consistentes (checkstyle/spotless o equivalente) como
  parte del build.

**Rationale:** un challenge se evalúa también por la mantenibilidad; código legible y documentado
reduce el costo de revisión y evidencia criterio de ingeniería.

### II. Validación de Entradas Obligatoria

Ninguna entrada externa es confiable. Todo dato que cruza el borde del sistema se valida antes
de llegar al dominio.

- Los DTOs de request usan Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Positive`,
  `@DecimalMin`, restricciones custom) y se validan en el controller (`@Valid` + `@RequestBody`).
- Se validan invariantes de negocio en el dominio (por ejemplo: orden LIMIT requiere `price`,
  orden MARKET lo prohíbe; no se admiten fracciones de acciones; `size` y `amount` mutuamente
  excluyentes con al menos uno presente).
- Los errores de validación se traducen a `400 Bad Request` con detalle por campo, nunca a
  excepciones no controladas ni stack traces.
- Se validan también parámetros de path/query, paginación y ordenamientos permitidos.
- Los tipos numéricos monetarios usan `BigDecimal` con escala y redondeo explícitos; nunca
  `double`/`float`.

**Rationale:** la validación temprana protege las invariantes del dominio y garantiza respuestas
HTTP correctas y predecibles.

### III. Tests Unitarios e Integración para Cada Endpoint (NO NEGOCIABLE)

Cada endpoint entregado debe estar cubierto por tests automatizados que se ejecutan en cada build.

- **Unitarios**: cubren casos de uso, reglas de dominio y mappers, sin levantar el contexto de
  Spring (JUnit 5 + Mockito).
- **Integración**: cubren el endpoint de punta a punta (controller → servicio → persistencia)
  con `@SpringBootTest` + `MockMvc`/`WebTestClient` y una base PostgreSQL real vía Testcontainers
  (o H2 únicamente si se documenta la diferencia de comportamiento).
- Se cubren caminos felices, errores de validación (400), no encontrado (404), conflicto de
  estado (409/422) y rechazos de negocio (por ejemplo, orden rechazada por saldo insuficiente).
- El envío de órdenes exige, como mínimo, un test funcional de integración (requisito explícito
  del challenge) que verifique estado persistido y actualización de posiciones/saldo.
- Un endpoint sin tests no se considera terminado; los tests deben pasar antes del merge.
- Se apunta a una cobertura significativa de ramas en dominio y aplicación; la cobertura
  numérica no reemplaza el criterio sobre casos críticos.

**Rationale:** los tests son la especificación ejecutable del comportamiento y la red de
seguridad para refactors en un dominio con reglas de negocio sensibles (saldos, órdenes).

### IV. Persistencia con Spring Data JPA + PostgreSQL y Migraciones Liquibase

- El acceso a datos se realiza exclusivamente a través de Spring Data JPA; las entidades JPA
  viven en la capa de infraestructura y **nunca** se exponen fuera de ella.
- El esquema se gestiona exclusivamente con **Liquibase** (changelogs versionados bajo
  `src/main/resources/db/changelog`, con un master changelog y cambios idempotentes); `ddl-auto`
  queda en `validate`/`none` fuera de tests. No se usa `spring.jpa.hibernate.ddl-auto=update`.
- PostgreSQL es la base de datos de referencia; cualquier cambio de esquema respecto del modelo
  provisto (`users`, `instruments`, `orders`, `marketdata`) debe justificarse en la documentación.
- Las consultas que calculan tenencias, saldos y valor de portfolio se resuelven con consultas
  agregadas eficientes (JPQL/proyecciones o SQL nativo documentado), evitando N+1 y el cálculo
  en memoria de grandes volúmenes.
- Las operaciones que modifican estado (envío de orden, ejecución, cancelación) son transaccionales
  y consistentes; se define explícitamente la estrategia ante concurrencia cuando afecte saldos.
- Se usan migraciones y scripts idempotentes; los datos de ejemplo (`database.sql`) se cargan por
  un mecanismo reproducible.

**Rationale:** JPA + PostgreSQL es el stack elegido; migraciones versionadas y consultas
conscientes del rendimiento son requisito para un backend correcto y auditable.

### V. Arquitectura Hexagonal, Separada por Dominio y `shared`

La organización del código es por dominio, y dentro de cada dominio por capa. El dominio no
conoce frameworks ni infraestructura.

Estructura obligatoria:

```
com.<org>.challenge
├── <domain-name>            # ej. portfolio, instruments, orders, users
│   ├── application          # casos de uso, puertos de entrada/salida, DTOs de aplicación
│   ├── domain               # entidades de dominio, value objects, reglas e interfaces de repositorio
│   └── infrastructure       # adapters: controllers REST, entidades JPA, repositorios Spring Data, mappers
└── shared
    ├── application          # utilidades de aplicación transversales
    ├── domain               # tipos y errores de dominio compartidos
    └── infrastructure       # configuración, manejo global de errores, adapters comunes
```

Reglas de dependencia:

- `domain` no depende de `application` ni de `infrastructure`, ni de Spring/JPA.
- `application` depende de `domain` y define puertos; no conoce detalles de infraestructura.
- `infrastructure` depende de `application` y `domain` e implementa los puertos (adapters).
- La comunicación entre dominios se hace por interfaces/puertos, nunca por acceso directo a
  repositorios o entidades de otro dominio.
- Los controllers son adapters delgados: validan, mapean y delegan; no contienen reglas de negocio.
- El mapeo entre DTOs, objetos de dominio y entidades JPA se realiza con **ModelMapper**; los
  mapeos no triviales se configuran explícitamente y se cubren con tests. **No se utiliza MapStruct**.
- Se prohíben dependencias circulares entre dominios.

**Rationale:** la arquitectura hexagonal aísla las reglas de negocio, facilita el testing y permite
evolucionar infraestructura sin tocar el dominio.

### VI. Respuestas HTTP Consistentes y Códigos Correctos

- Todos los endpoints devuelven un contrato uniforme y versionado, con estructura de error común
  (`timestamp`, `status`, `error`, `message`, `path`, `details[]`).
- Se usan los códigos semánticamente correctos: `200`/`201` para éxito, `204` para sin contenido,
  `400` validación, `404` recurso inexistente, `409` conflicto de estado, `422` regla de negocio
  incumplida, `500` error inesperado.
- El manejo de errores es centralizado (`@RestControllerAdvice`); ninguna excepción escapa sin
  mapeo y nunca se filtran stack traces ni detalles internos.
- Los payloads usan `camelCase`, fechas ISO-8601 con zona horaria y montos serializados de forma
  explícita (escala definida).
- La API se documenta con OpenAPI/Swagger generado y mantenido como parte del entregable.
- Toda respuesta se define con DTOs; no se serializan entidades de dominio o JPA directamente.

**Rationale:** un contrato estable y códigos correctos hacen la API predecible para los clientes
y son parte de la evaluación de calidad.

### VII. Configuración Segura: Sin Datos Sensibles Hardcodeados

- Credenciales, URLs de base de datos, claves y secretos se toman de variables de entorno o de
  configuración externa; nunca se commitean en el repositorio.
- Se proveen archivos de ejemplo (`.env.example`, `application-example.yml`) sin valores reales.
- Los perfiles de Spring separan configuración local/test/producción.
- Los logs no exponen datos sensibles ni payloads completos con información personal.
- Los valores de negocio configurables (por ejemplo, comisiones o límites) se externalizan en
  `application.yml` o en base de datos, no se dispersan como literales.

**Rationale:** la seguridad y la operabilidad exigen que la configuración sea explícita, externa
y auditable.

## Restricciones Técnicas

- **Lenguaje/Runtime:** Java 21 (LTS) y Spring Boot 4.1.x (versión fijada en `pom.xml`).
- **API:** REST sobre Spring Web MVC; JSON como formato de intercambio; documentación con
  springdoc-openapi 3.2.x (Swagger UI).
- **Persistencia:** Spring Data JPA + PostgreSQL (driver 42.7.x); migraciones con Liquibase 4.29.x.
- **Mapeo:** Lombok para boilerplate y **ModelMapper** para mapear entre DTOs, dominio y entidades
  JPA; los custom mappings se configuran explícitamente. **No se usa MapStruct**.
- **Testing:** JUnit 5, Mockito, AssertJ, Testcontainers (PostgreSQL) y MockMvc, usando los
  starters de test de Spring Boot 4 (`spring-boot-starter-*-test`).
- **Build:** Maven con wrapper incluido (`mvnw`/`mvnw.cmd`); build reproducible y sin credenciales.
- **Documentación:** README con instrucciones de ejecución, decisiones de diseño/suposiciones y
  colección de requests (Postman/Insomnia/REST Client).
- **Alcance del challenge:** no se implementa autenticación de usuarios; el `userId` se recibe
  como parte del contrato y se documenta esa decisión.

## Flujo de Desarrollo y Puertas de Calidad

1. **Especificación → Plan → Tareas:** toda funcionalidad pasa por spec, plan técnico y tareas
   antes de implementarse.
2. **Constitution Check:** el plan técnico debe declarar explícitamente cómo cumple los principios
   I–VII; una violación requiere justificación y aprobación documentada.
3. **Definición de Terminado (DoD):**
   - Compila y el build pasa (`./mvnw verify` o `./gradlew build`).
   - Tests unitarios e integración del endpoint en verde.
   - Contrato OpenAPI actualizado.
   - Validaciones y mapeo de errores implementados.
   - Sin secretos ni credenciales en el código.
   - Documentación/suposiciones actualizadas.
4. **Revisiones:** todo cambio se revisa contra esta constitución; el reviewer puede bloquear el
   merge por violaciones no justificadas.

## Governance

- Esta constitución prevalece sobre prácticas informales y sobre decisiones de implementación
  que la contradigan.
- **Enmiendas:** se proponen por escrito indicando principio afectado, motivación e impacto; se
  aprueban y se registran en el Sync Impact Report.
- **Versionado (SemVer):**
  - **MAJOR:** eliminación o redefinición incompatible de un principio.
  - **MINOR:** nuevo principio o sección, o expansión material de la guía.
  - **PATCH:** aclaraciones y correcciones sin cambio semántico.
- **Cumplimiento:** cada plan y revisión verifica los principios; las excepciones deben quedar
  documentadas en el propio plan con su justificación y alcance.
- **Revisión periódica:** la constitución se revisa al inicio de cada ciclo relevante del proyecto
  o cuando cambie el stack.

**Version:** 1.1.2 | **Ratified:** 2026-11-11 | **Last Amended:** 2026-11-11
