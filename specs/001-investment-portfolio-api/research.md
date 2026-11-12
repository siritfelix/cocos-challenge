# Research: API de Inversiones — Portfolio, Instrumentos, Órdenes e Idempotencia

**Feature**: 001-investment-portfolio-api | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

Todas las decisiones técnicas derivan de: (a) los principios I–VII de la [constitución del proyecto](../../.specify/memory/constitution.md),
(b) el documento de requerimientos del challenge, y (c) verificación directa contra el repositorio y Maven Central.

---

## R-001: Starter de validación de entrada

**Uncertainty**: El `pom.xml` declara `spring-boot-starter-webmvc`, que en Spring Boot 4 **no** arrastra
`spring-boot-starter-validation`. La constitución (Principio II) exige Bean Validation para todo dato que
cruza el borde del sistema.

**Decision**: Agregar `org.springframework.boot:spring-boot-starter-validation` al `pom.xml`. Se verificó el
POM de `spring-boot-starter-webmvc:4.1.2` en Maven Central: sus únicas dependencias son
`spring-boot-starter`, `spring-boot-starter-jackson`, `spring-boot-starter-tomcat`,
`spring-boot-http-converter` y `spring-boot-webmvc`. **No** incluye `jakarta.validation`.

**Rationale**: Sin el starter no existe `jakarta.validation-api` ni `hibernate-validator` en el classpath, y
todas las anotaciones `@Valid`, `@NotNull`, `@Positive` fallarían en compilación. Es un requisito de la
constitución, no una preferencia.

**Alternatives considered**:
- Declarar `jakarta.validation:jakarta.validation-api` + `org.hibernate.validator:hibernate-validator` a mano → rechazado: se pierde la gestión de versiones de Boot y la autoconfiguración.
- Usar validación manual en los controllers → rechazado: contradice el Principio II y duplica lógica.

**Sources**: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide ·
https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-webmvc/4.1.2/

---

## R-002: Starter de Liquibase y versión 4.29.2 vs 5.0.4

**Uncertainty**: El `pom.xml` declara `org.liquibase:liquibase-core:4.29.2` directamente. En Spring Boot 4 la
autoconfiguración de Liquibase vive en el módulo `spring-boot-liquibase`, que **solo** se activa si el
starter está presente. Además, Boot 4.1.2 gestiona Liquibase **5.0.4**, no 4.29.x.

**Decision**:
1. Cambiar la dependencia a `org.springframework.boot:spring-boot-starter-liquibase` (dejar la versión
   gestionada por Boot, **eliminando** la property `liquibase-core.version`).
2. Verificar la compatibilidad en el build; si `spring-boot-starter-liquibase` fuerza Liquibase 5 y el
   changelog lo requiere, evaluar el downgrade explícito a 4.29.2 **solo** si la suite de tests lo valida.

**Rationale**: Con `liquibase-core` puro, `LiquibaseAutoConfiguration` (condicional a `SpringLiquibase` +
`DatabaseChange` en el classpath) **no se registra**, y las migraciones **nunca se ejecutan** — el fallo es
silencioso y el esquema queda vacío en un esquema gestionado por Hibernate. Además, el módulo
`spring-boot-liquibase` de Boot 4 compila contra APIs de Liquibase 5.0.x (`liquibase.ui.UIServiceEnum`,
`liquibase.integration.spring.Customizer`), por lo que 4.29.2 con Boot 4.1.2 es una combinación **no
verificada por Spring**.

**Alternatives considered**:
- Mantener `liquibase-core:4.29.2` sin cambio → rechazado: no hay autoconfiguración; las migraciones no corren.
- `spring-boot-starter-liquibase` + `<version>4.29.2</version>` explícito → aceptado como fallback si el test suite lo valida, pero no es el estado inicial preferido.

**Sources**: https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/coordinates.html ·
https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-liquibase/4.1.2/ ·
https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom

---

## R-003: Versión de ModelMapper

**Uncertainty**: La constitución fija ModelMapper 3.2.4; la última versión publicada es 3.2.8.

**Decision**: Subir a `3.2.8` (parche dentro de la misma línea 3.2.x, sin cambios de API).

**Rationale**: 3.2.8 es un parche de la línea que ya usa el proyecto. Se mantiene la prohibición explícita de
MapStruct de la constitución (Principio V). ModelMapper no tiene dependencia de Spring, Jackson ni `javax.*`,
por lo que no hay conflicto técnico con Boot 4 / Jackson 3.

**Alternatives considered**:
- Mantener 3.2.4 → aceptable, pero deja de lado correcciones posteriores sin costo alguno.
- Sustituir por MapStruct → rechazado explícitamente por la constitución.

**Sources**: https://repo1.maven.org/maven2/org/modelmapper/modelmapper/ ·
https://github.com/modelmapper/modelmapper/issues/777 (bug de mapeos explícitos en 3.2.4, no específico de Boot 4)

---

## R-004: Estrategia de concurrencia — bloqueo pesimista por cuenta

**Uncertainty**: Los saldos y las posiciones son **derivados** de un agregado sobre `orders`. ¿Bloquear la fila
`users` basta para evitar que dos operaciones concurrentes consuman los mismos recursos?

**Decision**: Bloqueo pesimista sobre la fila `users` de la cuenta, adquirido **como primera operación de base
de datos** de la transacción, en nivel de aislamiento **READ COMMITTED** (el default de PostgreSQL).

Implementación: método de repositorio con `@Lock(LockModeType.PESSIMISTIC_WRITE)`.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select u from User u where u.accountNumber = :accountNumber")
Optional<User> findByAccountNumberForUpdate(@accountNumber);
```

**Rationale**: En PostgreSQL, `READ COMMITTED` toma un **snapshot por sentencia**, por lo que una vez
obtenido el bloqueo, la consulta agregada posterior ve todas las órdenes confirmadas hasta el inicio de esa
sentencia — incluyendo las del escritor anterior. La serialización la da el bloqueo de fila, no el nivel de
aislamiento.

Hibernate traduce `PESSIMISTIC_WRITE` en PostgreSQL a `FOR NO KEY UPDATE`, no a `FOR UPDATE`. Esto es
**deseable**: `FOR NO KEY UPDATE` es compatible con el `FOR KEY SHARE` que PostgreSQL toma sobre la fila
padre durante la verificación de la FK al insertar en `orders`, mientras que `FOR UPDATE` sí bloquearía esos
inserts y generaría ciclos de deadlock. `FOR NO KEY UPDATE` también se autoexcluye, por lo que dos
transacciones sobre la misma cuenta se serializan correctamente.

**Alternatives considered**:
- `SELECT ... FOR UPDATE` nativo → rechazado: más fuerte de lo necesario, bloquea los inserts con FK check y crea riesgo de deadlock documentado.
- `pg_advisory_xact_lock` → rechazado como mecanismo principal: no lo impone la base de datos; un camino que olvide tomarlo rompe el invariante en silencio. Se reserva para recursos sin fila natural.
- `REPEATABLE READ` o `SERIALIZABLE` → rechazado: `REPEATABLE READ` fija el snapshot en la primera sentencia (antes de obtener el bloqueo) y produce lecturas obsoletas o `could not serialize access`; `SERIALIZABLE` aborta de forma no determinista y obliga a un retry loop.
- Bloquear filas de `orders` → rechazado: PostgreSQL prohíbe cláusulas de bloqueo con `GROUP BY`/agregación.

**Sources**: https://www.postgresql.org/docs/current/transaction-iso.html ·
https://www.postgresql.org/docs/current/explicit-locking.html ·
https://github.com/hibernate/hibernate-orm/blob/master/hibernate-core/src/main/java/org/hibernate/dialect/PostgreSQLDialect.java

---

## R-005: Orden de adquisición de bloqueos y prevención de deadlocks

**Decision**: Orden de bloqueo fijo y codificado en la capa de servicio:

```
1. Fila `users` de la cuenta (bloqueo pesimista)  — SIEMPRE primero
2. Lectura de `idempotency_keys` (solo lectura, sin bloqueo)
3. Lecturas agregadas sobre `orders` (sin bloqueo)
4. INSERT en `orders` y en `idempotency_keys`
5. COMMIT (libera todo)
```

**Rationale**: PostgreSQL recomienda acquiring locks on multiple objects in a consistent order como defensa
principal contra deadlocks. El orden `users` → `idempotency_keys` → `orders` está fijo porque toda operación
que escribe órdenes pasa por el mismo camino. Como solo hay una cuenta por transacción (no hay transferencias
entre cuentas), no hay caso de múltiples bloqueos de `users` que ordenar.

**Alternatives considered**:
- Orden variable según el tipo de operación → rechazado: es exactamente la causa clásica de deadlock.

**Sources**: https://www.postgresql.org/docs/current/explicit-locking.html#LOCKING-DEADLOCKS

---

## R-006: Límite de espera de bloqueo (lock_timeout)

**Uncertainty**: ¿Cómo se acota la espera por un bloqueo tomado por otra transacción?

**Decision**: Emitir `SET LOCAL lock_timeout` **como primera sentencia** de la transacción, mediante
`JdbcTemplate`. Complementariamente, configurar `lock_timeout` a nivel de base de datos/rol.

**Rationale**: El hint `jakarta.persistence.lock.timeout` de Hibernate 7 aplica el timeout **después** de la
sentencia de bloqueo, por lo que **no acota la espera real**. Hay un bug abierto reportado en spring-data-jpa
(#4244) que lo confirma. `SET LOCAL` como primera sentencia sí acota la espera.

**Alternatives considered**:
- `jakarta.persistence.lock.timeout` → rechazado: por el orden de aplicación del hint, no cumple el objetivo.
- `lock_timeout` solo a nivel de base de datos → aceptado como complemento, pero no sustituye el `SET LOCAL` por transacción.

**Sources**: https://github.com/spring-projects/spring-data-jpa/issues/4244 ·
https://www.postgresql.org/docs/current/sql-set.html

---

## R-007: Idempotencia — patrón de bloqueo + restricción UNIQUE

**Uncertainty**: ¿Cómo garantizar at-most-once con reintentos concurrentes que comparten clave?

**Decision**: Tres capas, en este orden dentro de una **única** transacción `REQUIRED`:

1. **Bloqueo de la fila `users`** (ya adquirido, paso R-004) — serializa todas las peticiones de creación de
   órdenes de la misma cuenta.
2. **Lectura de `idempotency_keys`** por `(user_id, idempotency_key)` → si existe y el hash coincide,
   devolver el resultado original; si el hash difiere, `409 Conflict`.
3. **Inserción** del par (orden, clave) apoyada en `UNIQUE(user_id, idempotency_key)` y `UNIQUE(order_id)` como
   red de seguridad a nivel de base de datos.

**Rationale**: Como el bloqueo de `users` se toma **antes** de leer la clave, dos peticiones concurrentes con
la misma clave sobre la misma cuenta quedan serializadas: la segunda espera al commit de la primera y, bajo
`READ COMMITTED`, su lectura posterior (nueva sentencia → nuevo snapshot) ve la fila de idempotencia ya
confirmada. Esto convierte el reintento concurrente en un **replay limpio**, no en una excepción.

**Alternatives considered**:
- **Capturar `DataIntegrityViolationException` y releer en la misma transacción** → rechazado: Hibernate marca la transacción como *rollback-only* antes de propagar la excepción; el `catch` corre pero el commit falla con `UnexpectedRollbackException`, y el persistence context queda inutilizable. Es el anti-patrón clásico.
- **`REQUIRES_NEW`** para el insert → rechazado: rompe la atomicidad entre la orden y su clave de idempotencia, y consume una segunda conexión mientras la primera sigue bloqueada.
- **`PROPAGATION_NESTED`** (savepoints) → rechazado: `JpaTransactionManager` con Hibernate no soporta savepoints (`NestedTransactionNotSupportedException: JpaDialect does not support savepoints`).
- **`INSERT ... ON CONFLICT DO NOTHING RETURNING`** en una CTE → rechazado como mecanismo principal: la variante de CTE de una sola sentencia devuelve vacío bajo concurrencia, porque el INSERT puede ver la fila en conflicto no confirmada mientras el SELECT de la misma sentencia (mismo snapshot) no. Se evaluó `ON CONFLICT DO UPDATE ... RETURNING` (documentado en PostgreSQL), que funciona, pero es **innecesario**: el bloqueo de `users` ya serializa las peticiones de la misma cuenta.

**Sources**: https://www.postgresql.org/docs/current/index-unique-checks.html ·
https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html ·
https://www.postgresql.org/docs/current/sql-insert.html

---

## R-008: Cálculo de saldos y posiciones — agregados SQL vs iteración en memoria

**Uncertainty**: ¿Cómo derivar efectivo y cantidades?

**Decision**: **Consultas agregadas en SQL** (nativo PostgreSQL con `FILTER`), no carga de filas en memoria.

```sql
SELECT
  COALESCE(SUM(o.size * o.price) FILTER (WHERE o.side = 'BUY'),  0)
- COALESCE(SUM(o.size * o.price) FILTER (WHERE o.side = 'SELL'), 0) AS traded_amount,
  COALESCE(SUM(o.size) FILTER (WHERE o.side = 'BUY'),  0)
- COALESCE(SUM(o.size) FILTER (WHERE o.side = 'SELL'), 0)               AS net_quantity
FROM orders o
WHERE o.user_id = :userId AND o.status = 'FILLED'
```

**Rationale**: Eficiente y auditable; cumple el Principio IV (consultas agregadas, evitando N+1 y cálculo en
memoria de grandes volúmenes). El `COALESCE` es indispensable: `SUM` sobre un conjunto vacío devuelve `NULL`
en PostgreSQL, no `0`. El `FILTER` va **dentro** del `COALESCE` (`COALESCE(SUM(x) FILTER (...), 0)`); la
forma invertida es un error de sintaxis. Se usa SQL nativo porque el `COALESCE(sum(...), 0)` en JPQL choca
con el literal `0` tipado como `Integer`, y porque `FILTER` no es portable en JPQL.

**Alternatives considered**:
- JPQL con `SUM(CASE WHEN ...)` → rechazado por la fricción de tipos en `COALESCE` y por no soportar `FILTER`.
- Cargar las órdenes y calcular en Java → rechazado: transfiere todo el historial por red y escala mal.

**Sources**: https://www.postgresql.org/docs/current/functions-aggregate.html ·
https://www.postgresql.org/docs/current/sql-expressions.html#SYNTAX-AGGREGATES

---

## R-009: Costo promedio ponderado — requiere iteración ordenada

**Uncertainty**: ¿El costo promedio de adquisición es computable con agregados SQL puros?

**Decision**: **No lo es en el caso general.** Se calcula con un **fold ordenado en Java** sobre las órdenes
`FILLED` del par (cuenta, instrumento), ordenadas por `(datetime ASC, id ASC)`.

Algoritmo (promedio ponderado):
```
BUY  (q, p): qty += q ; cost += q * p
SELL (q)   : avg = cost / qty ; cost -= q * avg ; qty -= q
avg final  = cost / qty   (si qty == 0 → 0)
```

**Rationale**: Cada venta reduce el costo al **promedio vigente en ese momento**, que depende de todas las
órdenes anteriores. No es un conjunto-aggregate sino un fold secuencial.

Contraejemplo que lo demuestra (BUY y SELL intercalados):
1. BUY 10 @ $10 → qty 10, cost 100, avg 10
2. SELL 5 → qty 5, cost 50, avg 10
3. BUY 5 @ $20 → qty 10, cost 150, avg 15
4. SELL 10 → qty 0, cost 0
5. BUY 5 @ $30 → qty 5, cost 150, **avg 30**

El atajo "costo total de compras × fracción restante" (150 × 5/25 = 30) coincide por casualidad en este
ejemplo, pero el agregado `SUM(net_qty × price)/SUM(net_qty)` es **incorrecto** porque valúa las ventas al
precio de venta en vez de al costo.

**Requirements sobre la ordenación**:
- El desempate debe ser por la clave surrogata `id` (monótona), **nunca** solo por `datetime`: dos órdenes con
  el mismo timestamp deben ordenarse igual en cada lectura, o dos lecturas concurrentes podrían calcular
  costos promedio distintos para la misma posición.
- El redondeo se aplica **solo en el borde de la API**; internamente se conserva la precisión completa de
  `BigDecimal` (divisiones con `MathContext.DECIMAL128`).
- Guarda contra división por cero: `qty == 0 ⇒ avg = 0`.
- Índice de apoyo: `(user_id, instrument_id, status, datetime, id)`.

**Alternatives considered**:
- Agregados SQL puros → rechazado: demostrado incorrecto en el caso general.
- Función de ventana (`OVER (ORDER BY ...)`) → rechazado: una función de ventana no puede referenciar otro valor de ventana ya calculado en la misma sentencia; PostgreSQL no tiene funciones de ventana recursivas.
- CTE recursiva → viable pero es iteración ordenada reubicada en la base, con el coste de debugging equivalente y menos testabilidad. Se descarta.

**Sources**: https://www.postgresql.org/docs/current/functions-window.html ·
https://www.postgresql.org/docs/current/sql-select.html (WITH RECURSIVE) ·
https://www.sqlserverfunctions.com/documentation/WINDOWING_FUNCTIONS/INVENTORY_CALCULATIONS/WAC

---

## R-010: Índice de soporte para el fold de costo promedio

**Decision**: Índice compuesto sobre `orders`: `(user_id, instrument_id, status, datetime, id)`.

**Rationale**: El fold del costo promedio (R-009) lee todas las órdenes `FILLED` de un par (cuenta,
instrumento) ordenadas. Sin este índice, cada consulta de portfolio paga un sort por cuenta+instrumento
completo. El índice ya cubre el patrón de filtro y provee el orden total requerido.

**Alternatives considered**:
- Índice solo `(user_id, datetime DESC)` (ya especificado en la spec) → insuficiente: no filtra por
  instrumento ni por status.

**Sources**: https://www.postgresql.org/docs/current/indexes.html

---

## R-011: Serialización de `LocalDateTime` en Boot 4 / Jackson 3

**Uncertainty**: La spec exige ISO-8601 sin zona horaria (`2026-11-12T04:30:00`).

**Decision**: Sin configuración especial. `LocalDateTime` se serializa como texto ISO-8601 por defecto en
Jackson 3 (el `WRITE_DATES_AS_TIMESTAMPS` pasó a `DateTimeFeature` y está **deshabilitado por defecto**).
Configurar explícitamente `spring.jackson.datatype.datetime.write-dates-as-timestamps=false` para dejar la
intención documentada y blindada ante cambios de default.

**Rationale**: Es exactamente el formato requerido, sin sufijo `Z` ni offset, y sin zona horaria adjunta —
coherente con la convención UTC de la spec (FR-046). Jackson 3 es el que trae Boot 4.

**Advertencia documentada**: la propiedad `spring.jackson.serialization.write-dates-as-timestamps` **ya no
existe** en Boot 4 (el feature se movió a `DateTimeFeature`), por lo que usarla provocaría un fallo de
binding al arrancar. No debe usarse.

**Alternatives considered**:
- `@JsonFormat` en cada DTO → rechazado: config global explícita es más mantenible.
- Configurar `spring.jackson.time-zone` → rechazado e irrelevante: `LocalDateTime` no lleva zona.

**Sources**: https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0 ·
https://docs.spring.io/spring-boot/4.1/reference/features/json.html

---

## R-012: Arranque de la suite de tests en Boot 4

**Uncertainty**: ¿Qué cambia en los tests con Boot 4?

**Decision**: Usar `@SpringBootTest` + `@AutoConfigureMockMvc` (MockMvc **ya no** lo provee `@SpringBootTest`
en Boot 4) y `@MockitoBean` / `@MockitoSpyBean` en lugar de los eliminados `@MockBean` / `@SpyBean`. Los
starters de test del proyecto (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`) ya son
los correctos para Boot 4 y arrastran `spring-boot-starter-test` transitivamente.

**Rationale**: Verificado en el POM de `spring-boot-starter-webmvc-test:4.1.2`, que incluye `spring-boot-test`
(MockMvc), `jsonassert`, `json-path`, JUnit Jupiter y Mockito. El `@ServiceConnection` de Testcontainers
mantiene su paquete (`org.springframework.boot.testcontainers.service.connection`) y requiere
`spring-boot-testcontainers`, ya presente.

**Sources**: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide ·
https://docs.spring.io/spring-boot/4.1/reference/testing/test-modules.html

---

## R-013: Artefactos de Testcontainers renombrados en la línea 2.x

**Uncertainty**: Boot 4.1.2 gestiona Testcontainers **2.0.5**, que renombró artefactos.

**Decision**: El `pom.xml` ya usa los nombres correctos (`org.testcontainers:testcontainers-postgresql` y
`org.testcontainers:testcontainers-junit-jupiter`). No se requiere cambio. Verificar el paquete de
`PostgreSQLContainer`: existe `org.testcontainers.postgresql.PostgreSQLContainer` (nuevo) y
`org.testcontainers.containers.PostgreSQLContainer` (legado, aún presente).

**Rationale**: Los artefactos actuales del `pom.xml` ya son los de la línea 2.x; el scaffold inicial del
proyecto acertó.

**Sources**: https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/coordinates.html

---

## R-014: Jackson 3 — cambios de defaults que afectan al contrato

**Uncertainty**: Jackson 3 cambió defaults que pueden alterar el JSON emitido y romper aserciones.

**Decision**: Fijar explícitamente en `application.yaml`:

- `spring.jackson.mapper.sort-properties-alphabetically=false` — Jackson 3 lo habilita por defecto; se
  desactiva para que el orden de las propiedades del JSON sea el declarado en los DTOs, que es lo que espera
  el contrato OpenAPI y las aserciones de tests.
- `spring.jackson.datatype.enum.write-enums-using-to-string=false` — Jackson 3 serializa enums vía
  `toString()` por defecto. Se desactiva para garantizar que los enums (`BUY`, `SELL`, `CASH_IN`,
  `CASH_OUT`, `MARKET`, `LIMIT`, `NEW`, `FILLED`, `REJECTED`, `CANCELLED`, `ACCIONES`, `MONEDA`) se expongan
  por su `name()`, tal como exige la spec.

**Rationale**: Sin estas fijaciones, el contrato HTTP dependería de defaults de la librería y cambiaría entre
versiones. Los enums del dominio no sobrescriben `toString()`, por lo que hoy el resultado coincidiría, pero
fijarlo hace el contrato explícito y estable (Principio VI).

**Alternatives considered**:
- Aceptar los defaults de Jackson 3 → rechazado: acopla el contrato HTTP a la versión de la librería.
- Sobrescribir `toString()` en los enums para que devuelvan el `name()` → rechazado: `toString()` es para presentación, no para el contrato de la API.

**Sources**: https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0 ·
https://docs.spring.io/spring-boot/4.1/appendix/application-properties/index.html

---

## R-015: Modelo de dominio y aislamiento entrebounded contexts

**Uncertainty**: ¿Cómo se modela el dominio dado que posiciones y saldos son derivados?

**Decision**: Arquitectura hexagonal por dominio (Principio V de la constitución), con cuatro contextos:

| Contexto | Responsabilidad |
|---|---|
| `users` | Resolución de la cuenta por `account_number` y **bloqueo pesimista** de la fila. |
| `instruments` | Catálogo y búsqueda; **última cotización** por instrumento. |
| `orders` | Creación, validación de recursos, reservas, idempotencia, cancelación e historial. |
| `portfolio` | Derivación de efectivo, posiciones, costo promedio y rendimientos. |

Reglas de dependencia:
- `domain` no depende de `application` ni de `infrastructure`; no conoce Spring ni JPA.
- `application` define puertos de entrada/salida; no conoce infraestructura.
- `infrastructure` implementa los puertos: adapters REST, entidades JPA, repositorios Spring Data, mappers.
- `portfolio` **consume** los datos de `orders` a través de puertos (interfaz), nunca accediendo a los
  repositorios de `orders` directamente.
- `orders` es el único contexto que escribe en `orders` e `idempotency_keys`.

**Rationale**: Los saldos y posiciones son **vistas derivadas**, no entidades. Modelarlos como un contexto de
lectura separado evita duplicar la lógica de agregación dentro de `orders` y mantiene el principio de
comunicación por puertos.

**Sources**: [.specify/memory/constitution.md](../../.specify/memory/constitution.md) (Principio V)

---

## R-016: Dominio de modelado de órdenes — semántica unificada de `size`

**Uncertainty**: La spec define `orders.size` con doble semántica: cantidad de acciones en `BUY`/`SELL`, e
importe en pesos en `CASH_IN`/`CASH_OUT`. Esto puede inducir errores de cálculo.

**Decision**: El campo `size` se conserva **único** en el esquema (así lo fija la spec y el modelo de datos de
referencia), pero la capa de dominio lo tipa con **value objects distintos** para evitar mezclas:
`ShareQuantity` (acciones, entera) e `CashAmount` (pesos, 2 decimales). El mapeo entre la entidad y el
dominio convierte según el `side`. La regla de conversión del importe a cantidad (`cantidad = ⌊importe /
precio⌋`) vive en un value object `ShareQuantity.fromAmount(...)`.

**Rationale**: La spec (D-045) fija `size` como columna única, pero el Principio I (código limpio, sin
magia) y el II (validación de entradas) favorecen tipar el dominio. La tabla sigue teniendo una sola columna
`NUMERIC(10,2)`; la distinción vive en el código, no en el esquema.

**Alternatives considered**:
- Columnas separadas `size` y `amount` en `orders` → rechazado: contradice D-045 de la spec.
- Solo `BigDecimal` plano en el dominio → rechazado: permite errores de mezcla de semántica.

**Sources**: [spec.md](./spec.md) (D-045, FR-016, FR-030)

---

## R-017: Cálculo del hash de idempotencia

**Decision**: SHA-256 sobre una **canonicalización explícita** del contenido semántico, construida con
separadores inequívocos y campos en orden fijo:

```
ticker | side | type | size|amount (normalizado) | price (normalizado)
```

Normalizaciones:
- `ticker`: `trim()` + mayúsculas.
- `size` / `amount`: `BigDecimal.stripTrailingZeros().toPlainString()`.
- `price`: `null` si no viene; si viene, `stripTrailingZeros().toPlainString()`.
- `side` / `type`: mayúsculas.

**Rationale**: La spec exige que el hash sea **independiente del orden de las propiedades JSON** (FR-056) y que
considere ticker, lado, tipo, cantidad o importe y precio. Construir la canonicalización a mano —en vez de
serializar el objeto— garantiza que el orden de llegada de los campos no influye, y que `10` y `10.00`
produzcan la misma huella.

**Alternatives considered**:
- Hash del JSON serializado → rechazado: el orden de las propiedades JSON no está garantizado.
- Hash de la entidad JPA → rechazado: mezcla campos que no son parte del contenido semántico.

**Sources**: [spec.md](./spec.md) (FR-054…FR-061)

---

## R-018: Estrategia de testing

**Decision**: Tres niveles, alineados con el Principio III (obligatorio, no negociable):

1. **Unitarios** (JUnit 5 + Mockito): casos de uso, reglas de dominio, value objects, canonicalización del
   hash, cálculo de costo promedio y rendimientos. Sin contexto de Spring.
2. **Integración** (`@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers PostgreSQL): cada endpoint de
   punta a punta, contra una base PostgreSQL real. Cubre `200`, `201`, `400`, `404`, `409` y los rechazos de
   negocio persistidos.
3. **Concurrencia** (Testcontainers): dos peticiones simultáneas compitiendo por el mismo saldo / las mismas
   acciones; dos peticiones simultáneas con la misma clave de idempotencia. Se verifica que nunca haya saldo
   ni posición negativos y que exista una sola orden.

Datos de prueba: se carga un dataset de **seed determinista** vía Liquibase (`db/changelog/data/`) o SQL de
test, **no** el `database.sql` de referencia (que usa `SERIAL`, nombres en camelCase y no tiene
`idempotency_keys`).

**Rationale**: La spec (SC-015) exige pruebas funcionales automatizadas del envío de órdenes, y el Principio III
exige tests por endpoint. Los escenarios de concurrencia (SC-008) no pueden validarse sin una base real.

**Sources**: [.specify/memory/constitution.md](../../.specify/memory/constitution.md) (Principio III) ·
[spec.md](./spec.md) (SC-015, SC-008)

---

## R-019: Datos de seed y esquema de referencia

**Uncertainty**: El `database.sql` de referencia tiene un esquema distinto al de la spec (nombres de columna en
camelCase, `SERIAL` en vez de `BIGINT GENERATED BY DEFAULT AS IDENTITY`, `date DATE` en vez de
`datetime TIMESTAMP`, sin `idempotency_keys`, `users.email` sin `NOT NULL`/`UNIQUE`).

**Decision**: El esquema autoritativo es el de la **spec** (sección 3), materializado en Liquibase. El
`database.sql` se conserva como referencia de datos de mercado y se **adapta** a un changelog de seed con el
nombre de columnas del esquema autoritativo. El `ARS` se agrega con `name = 'Peso argentino'` (la spec lo
define así; el `database.sql` dice `'PESOS'`) y `type = 'MONEDA'`.

**Rationale**: La spec es la fuente de verdad del modelo de datos. La constitución (Principio IV) exige que
los datos de ejemplo se carguen por un mecanismo **reproducible** — un changelog de seed de Liquibase lo es;
un `database.sql` suelto no.

**Alternatives considered**:
- Usar `database.sql` tal cual → rechazado: el esquema no coincide con la spec ni con el modelo de la constitution (BIGINT identity, timestamps).
- Cargar el seed con un runner SQL fuera de Liquibase → rechazado: menos reproducible y no se ejecuta en los tests.

**Sources**: [spec.md](./spec.md) (sección 3) · [.specify/memory/constitution.md](../../.specify/memory/constitution.md) (Principio IV)

---

## R-020: Documentación de la API

**Decision**: OpenAPI 3 vía `springdoc-openapi-starter-webmvc-ui` (ya en el `pom.xml` en 3.2.0, **verificado
compatible** con Boot 4.1.2: su CHANGELOG declara explícitamente el upgrade a Spring Boot 4.1.2). El contrato
se versiona además en `specs/001-investment-portfolio-api/contracts/openapi.yaml` como artefacto revisable.

**Rationale**: El Principio VI exige "API documentada con OpenAPI/Swagger generado y mantenido como parte del
entregable". El `openapi.yaml` versionado permite revisarlo en el diff del PR, algo imposible con el
OpenAPI generado en runtime.

**Alternatives considered**:
- Solo OpenAPI generado en runtime → insuficiente: no es revisable en el control de versiones.

**Sources**: https://springdoc.org/faq.html ·
https://github.com/springdoc/springdoc-openapi/blob/main/CHANGELOG.md

---

## Resumen de cambios al `pom.xml`

| Cambio | Motivo | Referencia |
|---|---|---|
| `+ spring-boot-starter-validation` | `jakarta.validation` no viene en `spring-boot-starter-webmvc`; lo exige el Principio II | R-001 |
| `~ liquibase-core` → `spring-boot-starter-liquibase` | Sin el starter no hay autoconfiguración y las migraciones no se ejecutan | R-002 |
| `~ liquibase-core.version` 4.29.2 → versión gestionada (5.0.4) | Boot 4.1.2 compila contra APIs de Liquibase 5 | R-002 |
| `~ modelmapper` 3.2.4 → 3.2.8 | Parche de la misma línea, sin cambios de API | R-003 |
| `+ spring-boot-starter-data-jpa-test` (ya presente) | correcto para Boot 4 | R-012 |
| `+ spring-boot-testcontainers` (ya presente) | requerido por `@ServiceConnection` | R-012 |

## Riesgos asumidos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Liquibase 5.0 adopta licencia FSL (Community) | Restricciones de uso en distribución comercial | Solo se usan change types del core de Liquibase; sin extensiones ni features Pro |
| Costo promedio requiere iteración O(n) por posición | Escalado del endpoint de portfolio | Índice de soporte (R-010) y volumen acotado por el scope del challenge; el modelo de "sin tablas de posiciones" es una decisión explícita de la spec (D-047) |
| El bloqueo por cuenta limita el throughput por cuenta a ~1/duración de transacción | Contención en una misma cuenta | Aceptable: el modelo de negocio serializa por cuenta y es el trade-off explícito de D-022 |
| El protocolo de bloqueo depende de que todo escritor pase por el servicio | Una escritura directa a `orders` rompería el invariante | Regla de código: `orders` solo se escribe desde el caso de uso de creación;auditado en code review |
