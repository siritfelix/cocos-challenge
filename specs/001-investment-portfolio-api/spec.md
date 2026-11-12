# Feature Specification: API de Inversiones — Portfolio, Instrumentos, Órdenes e Idempotencia

**Feature Branch**: `001-investment-portfolio-api`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "Desarrollar una API REST para administrar un portfolio de inversiones: consultar instrumentos, crear órdenes de compra (BUY) y venta (SELL), realizar ingresos (CASH_IN) y retiros (CASH_OUT) de efectivo, consultar el historial de órdenes, cancelar órdenes pendientes y calcular saldos disponibles, posiciones y rendimientos. La identificación de cuenta se realiza mediante el header `X-AccountNumber`; la creación de órdenes exige header `Idempotency-Key`; los rechazos por regla de negocio se persisten como `REJECTED` con motivo; las posiciones y saldos se calculan a partir de las órdenes registradas, sin tablas adicionales. Stack: Java 21, Spring Boot 4, Spring Web, Spring Data JPA, Hibernate, PostgreSQL, Liquibase, Maven, JUnit/Mockito, Testcontainers, Docker Compose."

## Clarifications

### Sesión 2026-11-12

- **Q-01**: La sección 11.1 del documento fuente indica que el efectivo total se calcula solo a partir de `CASH_IN` y `CASH_OUT` ejecutados, mientras que la sección 6.4 incluye las compras `BUY FILLED` al calcular el efectivo disponible. → **A**: El efectivo total incluye el efecto de las compras y ventas ejecutadas: `efectivoTotal = CASH_IN − CASH_OUT − compras + ventas`. Es la única lectura coherente con la sección 6.4 y con el patrimonio total (sección 11.6); de lo contrario, comprar acciones sumaría valor al patrimonio sin haberlas pagado.
- **Q-02**: El documento no indica si el instrumento de efectivo `ARS` (tipo `MONEDA`) debe aparecer en el catálogo de instrumentos. → **A**: Sí se incluye. La sección 5 pide "todos los instrumentos disponibles" y `ARS` es un instrumento del modelo con ticker, nombre y tipo.
- **Q-03**: La sección 7.1 indica que un reintento idempotente debe "devolver el resultado original" sin especificar el código HTTP. → **A**: Devuelve `201 Created` con el mismo cuerpo y el mismo identificador de orden que la primera llamada, de modo que el cliente puede tratar el reintento de forma idéntica a la original.

### Alcance consolidado

Esta especificación es la **única vigente** del proyecto: consolida el alcance completo del challenge
backend en una sola funcionalidad, incluyendo portfolio, instrumentos, órdenes, transferencias de efectivo,
idempotencia e historial.

En particular, los rechazos por regla de negocio **se persisten** con estado `REJECTED` y motivo de
rechazo, y responden `201 Created`; a diferencia de versiones previas descartadas del documento, un rechazo
de negocio ya no se descarta silenciosamente.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enviar órdenes de compra y venta (Priority: P1)

Como inversor, quiero enviar órdenes de compra y de venta sobre un instrumento, a precio de mercado o con
un precio límite, para construir mi posición con la cantidad o el importe que yo decida.

**Why this priority**: Es la operación central del negocio: es la única fuente de movimientos que alimentan
el efectivo y las posiciones. Sin ella no hay portfolio que consultar.

**Independent Test**: Crear órdenes `MARKET` y `LIMIT` de compra y de venta sobre una cuenta con efectivo y
acciones precargadas; verificar estado persistido, precio aplicado, impacto en efectivo y posiciones, y el
comportamiento ante rechazos por saldo, acciones o cotización insuficientes.

**Acceptance Scenarios**:

1. **Given** una cuenta con efectivo suficiente y un instrumento cotizado, **When** envía una compra `MARKET` con una cantidad entera, **Then** la orden queda `FILLED` usando el último cierre como precio, el efectivo disminuye por cantidad × precio y la posición aumenta.
2. **Given** una cuenta con acciones suficientes y un instrumento cotizado, **When** envía una venta `MARKET` con una cantidad entera, **Then** la orden queda `FILLED` usando el último cierre como precio, el efectivo aumenta por cantidad × precio y la posición disminuye.
3. **Given** una cuenta con efectivo suficiente, **When** envía una compra `LIMIT` con precio límite, **Then** la orden queda `NEW`, el patrimonio no cambia y el efectivo disponible queda reservado por el importe de la orden.
4. **Given** una cuenta con acciones suficientes, **When** envía una venta `LIMIT` con precio límite, **Then** la orden queda `NEW`, la tenencia no cambia y la cantidad disponible de acciones queda reservada.
5. **Given** una cuenta con efectivo insuficiente para la compra, **When** envía la orden, **Then** la orden se persiste como `REJECTED` con motivo, el efectivo y las posiciones no cambian y la respuesta es `201 Created`.
6. **Given** una cuenta con acciones insuficientes para la venta, **When** envía la orden, **Then** la orden se persiste como `REJECTED` con motivo y las posiciones y el efectivo no cambian.
7. **Given** un instrumento sin cotización disponible, **When** envía una orden `MARKET`, **Then** la orden se persiste como `REJECTED` con motivo y no se modifican los recursos de la cuenta.
8. **Given** una orden `LIMIT`, **When** la envía sin precio, **Then** la solicitud es inválida, no se persiste ninguna orden y la respuesta es `400`.
9. **Given** una orden `MARKET`, **When** la envía con precio, **Then** la solicitud es inválida, no se persiste ninguna orden y la respuesta es `400`.
10. **Given** una orden de compra o venta, **When** la envía con cantidad fraccionaria, cero o negativa, **Then** la solicitud es inválida y no se persiste ninguna orden.
11. **Given** una cuenta con efectivo suficiente, **When** envía una compra indicando un importe en vez de una cantidad, **Then** el sistema calcula la cantidad máxima de acciones enteras cuyo valor no supere el importe, ejecuta esa cantidad y deja el importe sobrante disponible en la cuenta.
12. **Given** un importe que no alcanza para una sola acción, **When** envía la orden indicando ese importe, **Then** la orden se persiste como `REJECTED` con motivo.
13. **Given** un importe y un tipo `LIMIT`, **When** envía la orden, **Then** la cantidad se calcula tomando como referencia el precio límite informado.
14. **Given** una cuenta con exactamente el efectivo necesario, **When** envía la compra, **Then** la orden se ejecuta y el efectivo disponible queda en cero.
15. **Given** una orden `LIMIT` sin recursos suficientes, **When** la envía, **Then** la orden se persiste como `REJECTED` con motivo y no genera reservas.

---

### User Story 2 - Mover efectivo (Priority: P1)

Como inversor, quiero ingresar y retirar efectivo en pesos para poder operar y disponer de liquidez.

**Why this priority**: Sin efectivo cargado no se puede comprar ni vender; es condición de posibilidad del flujo
principal. Es además una necesidad de negocio independiente y demostrable por sí sola.

**Independent Test**: Registrar un ingreso y un retiro sobre una cuenta, verificar que el efectivo total y el
disponible suben y bajan en el importe correspondente, y que un retiro sin saldo suficiente queda `REJECTED`
sin alterar el saldo.

**Acceptance Scenarios**:

1. **Given** una cuenta válida, **When** registra un ingreso de efectivo por un importe positivo, **Then** la operación queda `FILLED`, el efectivo total y el disponible aumentan en ese importe y no genera posición de acciones.
2. **Given** una cuenta con efectivo suficiente, **When** registra un retiro de efectivo por un importe positivo, **Then** la operación queda `FILLED` y el efectivo total y el disponible disminuyen en ese importe.
3. **Given** una cuenta sin efectivo suficiente, **When** registra un retiro, **Then** la operación se persiste como `REJECTED` con motivo y el efectivo no cambia.
4. **Given** una cuenta con compras `LIMIT` pendientes que reservan efectivo, **When** registra un retiro por un importe que solo cubre el saldo total, **Then** el retiro se rechaza porque el efectivo reservado no puede retirarse.
5. **Given** un movimiento de efectivo, **When** lo envío indicando un importe en lugar del campo de tamaño, **Then** la solicitud es inválida y no se persiste ninguna operación.
6. **Given** un movimiento de efectivo con precio o tipo distinto de `MARKET`, **When** lo envía, **Then** la solicitud es inválida y no se persiste ninguna operación.
7. **Given** un movimiento de efectivo con importe cero o negativo, **When** lo envía, **Then** la solicitud es inválida y no se persiste ninguna operación.
8. **Given** un movimiento de efectivo ya registrado, **When** consulta el historial, **Then** aparece junto a las órdenes de compra y venta, con su estado e importe.

---

### User Story 3 - Consultar mi portfolio (Priority: P1)

Como inversor, quiero ver en un solo lugar cuánto efectivo tengo, cuánto está comprometido y qué posesiones
tengo, con su valor y rendimiento, para conocer mi situación patrimonial.

**Why this priority**: Es la vista de resultado de toda la operatoria y la que el usuario consulta constantemente.
Se entrega con valor propio incluso sin ability de operar.

**Independent Test**: Consultar el portfolio de una cuenta vacía, de una cuenta con efectivo, de una cuenta con
posiciones compradas y vendidas, y de una cuenta con órdenes `NEW` pendientes; verificar efectivo total,
disponible y reservado, cantidades, valores, costo promedio y rendimientos.

**Acceptance Scenarios**:

1. **Given** una cuenta sin operaciones, **When** consulta su portfolio, **Then** recibe efectivo total, disponible y reservado en cero, lista de posiciones vacía y valor total del portfolio en cero.
2. **Given** una cuenta con ingresos de efectivo, **When** consulta su portfolio, **Then** el efectivo total y el disponible reflejan esos ingresos aunque no tenga posiciones.
3. **Given** una cuenta con compras y ventas ejecutadas, **When** consulta su portfolio, **Then** cada posición informa cantidad total, cantidad disponible, cantidad reservada, precio actual, valor de mercado, costo promedio, rendimiento total y rendimiento diario.
4. **Given** una cuenta con compras `LIMIT` pendientes, **When** consulta su portfolio, **Then** el efectivo total no disminuye, el efectivo disponible sí, y el importe comprometido figura como efectivo reservado.
5. **Given** una cuenta con ventas `LIMIT` pendientes, **When** consulta su portfolio, **Then** la cantidad total de la posición no cambia y la cantidad disponible disminuye por las acciones reservadas.
6. **Given** un instrumento con cierre actual y cierre anterior, **When** consulta su portfolio, **Then** el rendimiento diario de esa posición refleja la variación entre ambos cierres.
7. **Given** un instrumento sin cierre anterior o con cierre anterior en cero, **When** consulta su portfolio, **Then** el rendimiento diario se informa como no disponible y el resto de la posición se calcula normalmente.
8. **Given** una posición sin costo de adquisición, **When** consulta su portfolio, **Then** el rendimiento total se informa como no disponible, sin provocar error.
9. **Given** una cuenta con posiciones, **When** consulta su portfolio, **Then** el valor total del portfolio es igual al efectivo total más la suma del valor de mercado de todas las posiciones.

---

### User Story 4 - Consultar instrumentos disponibles (Priority: P2)

Como inversor, quiero ver el catálogo de instrumentos y buscarlo por ticker o por nombre, para decidir qué operar.

**Why this priority**: Mejora el descubrimiento y la experiencia de operar, pero no bloquea el flujo principal si
ya conozco el instrumento que quiero usar.

**Independent Test**: Consultar el catálogo completo y ejecutar búsquedas parciales por ticker y por nombre en
mayúsculas y minúsculas; verificar resultados sin duplicados, búsqueda sin coincidencias y búsqueda vacía.

**Acceptance Scenarios**:

1. **Given** instrumentos cargados, **When** consulto el catálogo, **Then** recibo todos los instrumentos con su ticker, nombre y tipo.
2. **Given** instrumentos cargados, **When** busco por una parte del ticker, **Then** recibo los instrumentos cuyo ticker coincide, sin distinguir mayúsculas de minúsculas.
3. **Given** instrumentos cargados, **When** busco por una parte del nombre, **Then** recibo los instrumentos cuyo nombre coincide.
4. **Given** un instrumento que coincide a la vez por ticker y por nombre, **When** realizo la búsqueda, **Then** el resultado no contiene duplicados.
5. **Given** una búsqueda sin coincidencias, **When** la ejecuto, **Then** recibo una lista vacía y no un error.
6. **Given** una búsqueda vacía o compuesta solo por espacios, **When** la ejecuto, **Then** la solicitud es inválida y la respuesta es `400`.
7. **Given** una búsqueda con caracteres especiales, **When** la ejecuto, **Then** no produce error interno.

---

### User Story 5 - Consultar el historial de órdenes (Priority: P2)

Como inversor, quiero ver todas mis órdenes, con paginación y filtros, para auditar qué operé y en qué estado
queda cada cosa.

**Why this priority**: Es una necesidad de consulta y auditoría que se apoya en datos ya existentes; valuable
pero no bloquea la operatoria.

**Independent Test**: Consultar el historial vacío, con operaciones, paginado, y filtrando por estado, lado e
instrumento, combinados entre sí; verificar orden descendente por fecha y los conteos de paginación.

**Acceptance Scenarios**:

1. **Given** una cuenta sin operaciones, **When** consulto el historial, **Then** recibo una página vacía con los totales en cero.
2. **Given** una cuenta con operaciones de distintos estados, **When** consulto el historial, **Then** recibo todas las órdenes de la cuenta —`NEW`, `FILLED`, `REJECTED` y `CANCELLED`— ordenadas de la más reciente a la más antigua.
3. **Given** una cuenta con más órdenes que el tamaño de página, **When** consulto con paginación, **Then** recibo la página solicitada con el contenido, total de elementos, total de páginas, página actual y tamaño.
4. **Given** operaciones de distintos estados, **When** filtro por estado, **Then** recibo solo las órdenes de ese estado.
5. **Given** operaciones de distintos lados, **When** filtro por lado, **Then** recibo solo las órdenes de ese lado.
6. **Given** operaciones sobre distintos instrumentos, **When** filtro por ticker, **Then** recibo solo las órdenes de ese instrumento.
7. **Given** operaciones que cumplen varios filtros a la vez, **When** los combino, **Then** recibo solo las que cumplen todos.
8. **Given** un filtro de estado o de lado con un valor inválido, **When** lo envío, **Then** la solicitud es inválida y la respuesta es `400`.
9. **Given** una página que no existe, **When** la solicito, **Then** recibo una página vacía con respuesta `200`.
10. **Given** una cuenta, **When** consulto el historial con filtros, **Then** nunca recibo órdenes de otra cuenta.

---

### User Story 6 - Cancelar una orden pendiente (Priority: P3)

Como inversor, quiero cancelar una orden límite que todavía no se ejecutó, para desistir de esa operación y
recuperar los recursos que tenía comprometidos.

**Why this priority**: Es una operación de soporte sobre órdenes ya creadas; aporta control al usuario pero
puede entregarse después del flujo de consulta, instrumentos y órdenes.

**Independent Test**: Crear una orden `NEW`, cancelarla y verificar que pasa a `CANCELLED` y que los recursos
reservados se liberan; intentar cancelar órdenes en estados terminales y verificar el rechazo sin cambios.

**Acceptance Scenarios**:

1. **Given** una orden de la cuenta en estado `NEW`, **When** la cancelo, **Then** pasa a `CANCELLED`, se liberan los recursos reservados y no se altera el patrimonio.
2. **Given** una orden en estado `FILLED`, **When** intento cancelarla, **Then** la solicitud entra en conflicto, la orden conserva su estado y la respuesta es `409`.
3. **Given** una orden en estado `REJECTED`, **When** intento cancelarla, **Then** la solicitud entra en conflicto y la orden conserva su estado.
4. **Given** una orden en estado `CANCELLED`, **When** intento cancelarla, **Then** la solicitud entra en conflicto y la orden conserva su estado.
5. **Given** un identificador de orden inexistente, **When** intento cancelarla, **Then** la respuesta es `404`.
6. **Given** una orden de otra cuenta, **When** intento cancelarla, **Then** la respuesta es `404` y no se revela que la orden existe.
7. **Given** una transferencia de efectivo, **When** intento cancelarla, **Then** la solicitud entra en conflicto porque las transferencias no son cancelables.

---

### Edge Cases

- **Cuenta sin operaciones**: el portfolio se devuelve con ceros y lista de posiciones vacía; el historial devuelve una página vacía.
- **Cabecera de cuenta ausente**: la solicitud se rechaza antes de tocar datos de negocio.
- **Cabecera de cuenta con cuenta inexistente**: la solicitud se rechaza por recurso inexistente.
- **Orden `MARKET` sin cotización disponible**: la orden se persiste como `REJECTED` con motivo.
- **Operación `LIMIT` sin cotización disponible**: se admite, porque no necesita cotización para validar su precio límite.
- **Importe insuficiente para una sola acción**: la cantidad calculada es cero y la orden se persiste como `REJECTED` con motivo.
- **Importe con resto**: el sobrante permanece disponible en la cuenta y no se pierde.
- **`size` y `amount` enviados juntos**: la solicitud es inválida y no se persiste ninguna orden.
- **Ni `size` ni `amount` enviados**: la solicitud es inválida y no se persiste ninguna orden.
- **Transferencia con `amount`**: la solicitud es inválida; las transferencias se expresan con el campo de tamaño.
- **Transferencia con precio**: la solicitud es inválida; el precio almacenado queda vacío.
- **Transferencia con tipo distinto de `MARKET`**: la solicitud es inválida.
- **Operación de compra o venta sobre el instrumento de efectivo**: la solicitud es inválida; el efectivo solo se mueve por transferencias.
- **Transferencia sobre un instrumento distinto del de efectivo**: la solicitud es inválida.
- **Instrumento inexistente**: la solicitud se rechaza por recurso inexistente.
- **Reservas de órdenes `NEW`**: el efectivo y las acciones comprometidos no pueden usarse en nuevas operaciones ni retirarse.
- **Cancelación de una orden `NEW`**: libera las reservas y restituye el efectivo o las acciones disponibles.
- **Cancelación repetida**: la segunda cancelación de una orden ya `CANCELLED` entra en conflicto.
- **Reintento con la misma clave de idempotencia y el mismo contenido**: devuelve el resultado original sin crear una segunda orden.
- **Reintento con la misma clave de idempotencia y contenido distinto**: entra en conflicto (`409`).
- **Reintento concurrente con la misma clave**: como máximo queda una orden.
- **Fallo durante la creación de una orden**: ni la orden ni su clave de idempotencia quedan persistidas.
- **Órdenes `NEW`, `REJECTED` y `CANCELLED`**: no alteran la cantidad total de posiciones ni el patrimonio total.
- **Dos operaciones simultáneas sobre la misma cuenta**: nunca dejan efectivo ni posiciones negativos.
- **Posición con cantidad total en cero**: no se informa en el listado de posiciones.
- **Rendimiento total sin costo de adquisición**: se informa como no disponible, sin división por cero.
- **Rendimiento diario sin cierre anterior válido**: se informa como no disponible, sin división por cero.
- **Búsqueda de instrumentos con coincidencia simultánea por ticker y nombre**: no produce duplicados.
- **Listado de instrumentos sin instrumentos cargados**: devuelve una lista vacía, no un error.
- **Valores no numéricos en los filtros de historial**: la solicitud es inválida.

## Requirements *(mandatory)*

### Functional Requirements

**Identificación de cuentas**

- **FR-001**: Toda operación que dependa de una cuenta MUST requerir un identificador de cuenta en la cabecera `X-AccountNumber`.
- **FR-002**: El sistema MUST rechazar con `400 Bad Request` toda solicitud sin la cabecera de cuenta.
- **FR-003**: El sistema MUST rechazar con `404 Not Found` toda solicitud cuya cuenta no exista.
- **FR-004**: El sistema MUST ejecutar todas las operaciones en el contexto de la cuenta identificada y MUST NOT exponer ni modificar datos de otra cuenta.
- **FR-005**: El sistema MUST funcionar sin autenticación ni autorización.

**Instrumentos**

- **FR-006**: El sistema MUST permitir consultar el catálogo de instrumentos disponibles con su ticker, nombre y tipo.
- **FR-007**: El catálogo MUST limitar los tipos de instrumento a acciones y a moneda.
- **FR-008**: El instrumento de efectivo MUST ser el de ticker `ARS`, nombre `Peso argentino`, tipo moneda.
- **FR-009**: El sistema MUST permitir buscar instrumentos por coincidencia parcial de ticker y/o de nombre, sin distinguir mayúsculas de minúsculas.
- **FR-010**: El sistema MUST devolver resultados sin duplicados cuando un instrumento coincide a la vez por ticker y por nombre.
- **FR-011**: El sistema MUST rechazar con `400 Bad Request` una búsqueda vacía o compuesta solo por espacios.
- **FR-012**: El sistema MUST devolver una lista vacía, con respuesta `200`, cuando la búsqueda no tiene coincidencias.
- **FR-013**: El sistema MUST tolerar caracteres especiales en la búsqueda sin producir errores internos.
- **FR-014**: El catálogo MUST incluir el instrumento de efectivo, que no es operable como compra ni como venta.
- **FR-014b**: El catálogo MUST listar tanto los instrumentos de tipo acciones como el de tipo moneda.

**Órdenes: solicitud y validación de entrada**

- **FR-015**: El sistema MUST permitir crear órdenes de compra (`BUY`), de venta (`SELL`), de ingreso de efectivo (`CASH_IN`) y de retiro de efectivo (`CASH_OUT`).
- **FR-016**: La solicitud MUST contener exactamente uno de los campos de cantidad o de importe; enviar ambos o ninguno MUST ser un error de validación sin persistencia.
- **FR-017**: Para compras y ventas, la cantidad MUST ser un número entero mayor que cero.
- **FR-018**: El importe MUST ser mayor que cero.
- **FR-019**: Las compras y las ventas MUST admitir indistintamente cantidad o importe.
- **FR-020**: Las transferencias de efectivo MUST expresarse con el campo de cantidad e MUST NOT admitir el campo de importe.
- **FR-021**: Las compras y las ventas MUST operar únicamente sobre instrumentos de tipo acciones.
- **FR-022**: Las transferencias MUST operar únicamente sobre el instrumento de efectivo.
- **FR-023**: Una orden `MARKET` MUST NOT admitir precio en la solicitud; la presencia de precio MUST ser un error de validación sin persistencia.
- **FR-024**: Una orden `LIMIT` MUST exigir un precio mayor que cero.
- **FR-025**: Las transferencias MUST ser de tipo `MARKET` y MUST NOT admitir precio; su precio almacenado MUST quedar vacío.
- **FR-026**: Ante un error de validación, el sistema MUST NOT persistir ninguna orden y MUST responder con `400 Bad Request`.

**Órdenes: resolución de precio y cantidad**

- **FR-027**: Para una orden `MARKET`, el precio de ejecución MUST ser el último cierre disponible del instrumento y MUST quedar registrado en la orden.
- **FR-028**: Si una operación `MARKET` no tiene cotización disponible, el sistema MUST persistir la orden como `REJECTED` con su motivo.
- **FR-029**: Las órdenes `LIMIT` MUST poder crearse sin datos de mercado, usando el precio límite informado por el usuario.
- **FR-030**: Cuando la solicitud usa importe, el sistema MUST calcular la cantidad máxima de acciones enteras cuyo valor no supere el importe indicado, tomando como precio de referencia el precio de ejecución de la orden.
- **FR-031**: El importe sobrante MUST permanecer disponible en la cuenta.
- **FR-032**: Si el importe no alcanza para una sola acción, la cantidad calculada MUST ser cero y la orden MUST persistirse como `REJECTED` con su motivo.

**Órdenes: validación de recursos y reservas**

- **FR-033**: El sistema MUST calcular el efectivo disponible de una cuenta a partir de ingresos de efectivo ejecutados, retiros de efectivo ejecutados, compras ejecutadas y reservas de compras pendientes, descontando los retiros y las compras.
- **FR-034**: El sistema MUST calcular las acciones disponibles de un instrumento a partir de compras ejecutadas y ventas ejecutadas, descontando las ventas y las reservas de ventas pendientes.
- **FR-035**: Una compra MUST requerir efectivo disponible suficiente para el costo total (cantidad por precio); si no lo hay, la orden MUST persistirse como `REJECTED` con su motivo, sin modificar efectivo ni posiciones.
- **FR-036**: Una venta MUST requerir acciones disponibles suficientes para la cantidad solicitada; si no las hay, la orden MUST persistirse como `REJECTED` con su motivo, sin modificar efectivo ni posiciones.
- **FR-037**: Las órdenes `NEW` MUST reservar el efectivo (compras: cantidad por precio límite) o las acciones (ventas: cantidad) comprometidos, y esas reservas MUST descontarse de los recursos disponibles.
- **FR-038**: El efectivo y las acciones disponibles MUST NOT quedar nunca en valores negativos, incluso ante operaciones concurrentes.

**Órdenes: ejecución y estados**

- **FR-039**: Las órdenes `MARKET` MUST ejecutarse de inmediato y quedar en estado `FILLED` cuando las validaciones se cumplen.
- **FR-040**: Las órdenes `LIMIT` MUST quedar en estado `NEW` y MUST permanecer en ese estado hasta que sean canceladas.
- **FR-041**: El sistema MUST NOT implementar ejecución automática de órdenes `LIMIT` ni motor de emparejamiento.
- **FR-042**: Los rechazos por regla de negocio MUST persistirse con estado `REJECTED` y un motivo legible, MUST NOT modificar los recursos de la cuenta y MUST responder `201 Created`.
- **FR-043**: Las transferencias de efectivo MUST ejecutarse de inmediato y quedar en estado `FILLED` cuando las validaciones se cumplen.
- **FR-044**: Las transferencias MUST NOT poder quedar en estado `NEW` ni `CANCELLED`.
- **FR-045**: El sistema MUST persistir cada orden con cuenta, instrumento, lado, tipo, cantidad, precio cuando exista, estado, motivo de rechazo cuando aplique y fecha y hora de creación.
- **FR-046**: El sistema MUST asignar la fecha y hora de la orden en el servidor, en convención UTC, y expresarla en formato ISO-8601 sin zona horaria, offset ni sufijo `Z`.
- **FR-047**: La respuesta de creación de órdenes MUST incluir identificador, ticker, lado, tipo, cantidad, precio, estado, motivo de rechazo y fecha y hora.
- **FR-048**: La respuesta de creación de órdenes MUST ser `201 Created` tanto para órdenes `FILLED`, `NEW` como `REJECTED`.
- **FR-049**: Los estados `FILLED`, `REJECTED` y `CANCELLED` MUST ser inmutables.

**Transferencias de efectivo**

- **FR-050**: Un ingreso de efectivo MUST aumentar el efectivo de la cuenta en el importe registrado.
- **FR-051**: Un retiro de efectivo MUST disminuir el efectivo de la cuenta en el importe registrado y MUST validarse contra el efectivo disponible, descontadas las reservas de compras pendientes.
- **FR-052**: Un retiro sin efectivo disponible suficiente MUST persistirse como `REJECTED` con su motivo, sin alterar el saldo.
- **FR-053**: Las transferencias MUST NOT generar posiciones de acciones.

**Idempotencia**

- **FR-054**: Toda solicitud de creación de órdenes MUST requerir una cabecera `Idempotency-Key`; su ausencia MUST ser un error de validación.
- **FR-055**: La clave de idempotencia MUST ser única por cuenta.
- **FR-056**: El sistema MUST calcular una huella criptográfica del contenido semántico de la solicitud —ticker, lado, tipo, cantidad o importe y precio— de forma independiente del orden en que los campos se reciben.
- **FR-057**: Una solicitud con una clave ya usada y la misma huella MUST devolver el resultado original —mismo cuerpo, mismo identificador de orden y misma respuesta `201 Created`— sin crear una segunda orden.
- **FR-058**: Una solicitud con una clave ya usada y una huella distinta MUST responder `409 Conflict`.
- **FR-059**: Solicitudes concurrentes con la misma clave MUST NOT generar órdenes duplicadas.
- **FR-060**: La creación de la orden y el registro de su clave de idempotencia MUST ocurrir en una misma unidad transaccional: si la operación falla, ninguno de los dos MUST quedar persistido.
- **FR-061**: Las claves de idempotencia MUST conservarse mientras las órdenes asociadas deban seguir siendo consultables de forma idempotente.

**Cancelación de órdenes**

- **FR-062**: El sistema MUST permitir cancelar únicamente órdenes en estado `NEW`.
- **FR-063**: Al cancelar una orden `NEW`, el sistema MUST cambiarla a `CANCELLED`, liberar los recursos reservados por ella y MUST NOT alterar el patrimonio.
- **FR-064**: El sistema MUST responder `409 Conflict` al intentar cancelar una orden `FILLED`, `REJECTED` o `CANCELLED`.
- **FR-065**: El sistema MUST responder `404 Not Found` al intentar cancelar una orden inexistente o perteneciente a otra cuenta, sin revelar la existencia de órdenes de otras cuentas.
- **FR-066**: La cancelación MUST ejecutarse de forma transaccional y bajo el mismo criterio de bloqueo que las demás operaciones sobre la cuenta.

**Portfolio**

- **FR-067**: El sistema MUST devolver el efectivo total, el efectivo disponible y el efectivo reservado de la cuenta.
- **FR-068**: El efectivo total MUST derivarse de las operaciones de efectivo ejecutadas y también de las compras y ventas ejecutadas, restando el importe de las compras y sumando el importe de las ventas; el efectivo reservado MUST corresponder al importe comprometido en compras pendientes; el efectivo disponible MUST ser el total menos el reservado.
- **FR-069**: El sistema MUST devolver, por cada instrumento con posición, el ticker, la cantidad total, la cantidad disponible, la cantidad reservada, el precio actual, el valor de mercado, el costo promedio, el rendimiento total y el rendimiento diario.
- **FR-070**: Las cantidades total y disponible de las posiciones MUST derivarse de las compras y ventas ejecutadas; la cantidad reservada MUST corresponder a las ventas pendientes.
- **FR-071**: El precio actual de una posición MUST ser el último cierre disponible del instrumento y el valor de mercado MUST ser la cantidad total por ese precio.
- **FR-072**: El costo promedio MUST calcularse por promedio ponderado: las compras ejecutadas incrementan cantidad y costo acumulado, y las ventas ejecutadas reducen la cantidad al costo promedio vigente. Las órdenes no ejecutadas MUST NOT alterar el costo promedio.
- **FR-073**: El rendimiento total MUST calcularse como `((valor actual − costo de adquisición) / costo de adquisición) × 100`, usando como valor actual la cantidad total por el último cierre.
- **FR-074**: El rendimiento diario MUST calcularse como `((cierre actual − cierre anterior) / cierre anterior) × 100`, usando la cotización más reciente del instrumento.
- **FR-075**: Cuando el costo de adquisición o el cierre anterior sean inexistentes o cero, el rendimiento MUST informarse como no disponible, MUST NOT provocar división por cero y MUST NOT alterar el resto de los cálculos.
- **FR-076**: El valor total del portfolio MUST ser el efectivo total más la suma del valor de mercado de todas las posiciones.
- **FR-077**: Las órdenes pendientes MUST NOT modificar el patrimonio total ni las cantidades totales; solo reducen la disponibilidad.
- **FR-078**: Una cuenta sin operaciones MUST recibir un portfolio válido con ceros y lista de posiciones vacía.
- **FR-079**: Los instrumentos sin posición MUST NOT aparecer en el listado de posiciones.

**Historial de órdenes**

- **FR-080**: El sistema MUST devolver todas las órdenes de la cuenta, incluyendo los estados `NEW`, `FILLED`, `REJECTED` y `CANCELLED`, ordenadas de la más reciente a la más antigua.
- **FR-081**: El historial MUST admitir paginación con página y tamaño, con valores por defecto de página `0` y tamaño `20`.
- **FR-082**: El tamaño de página MUST estar entre `1` y `100`; valores fuera de rango, negativos o no numéricos MUST producir `400 Bad Request`.
- **FR-083**: La respuesta paginada MUST incluir contenido, total de elementos, total de páginas, página actual y tamaño de página.
- **FR-084**: El historial MUST admitir filtros opcionales por estado, por lado y por ticker, combinables entre sí.
- **FR-085**: El filtro por estado MUST admitir `NEW`, `FILLED`, `REJECTED` y `CANCELLED`; el filtro por lado MUST admitir `BUY`, `SELL`, `CASH_IN` y `CASH_OUT`; valores fuera de esos conjuntos MUST producir `400 Bad Request`.
- **FR-086**: El filtro por ticker MUST realizar coincidencia exacta sobre el ticker del instrumento de la orden.
- **FR-087**: Los filtros MUST aplicarse siempre dentro del conjunto de órdenes de la cuenta identificada.
- **FR-088**: Una página sin resultados MUST devolver `200 OK` con una lista vacía.

**Manejo de errores y contrato**

- **FR-089**: Toda respuesta de error MUST usar una estructura uniforme con marca de tiempo, código de estado, descripción del error, mensaje y ruta.
- **FR-090**: El sistema MUST usar los códigos `400` (solicitud inválida), `404` (recurso inexistente), `409` (conflicto de estado o de idempotencia) y `500` (error inesperado).
- **FR-091**: El sistema MUST NOT exponer trazas, detalles internos ni información sensible en las respuestas de error.
- **FR-092**: El motivo de rechazo MUST ser un texto legible que identifique la regla de negocio incumplida.

**Consistencia, concurrencia y atomicidad**

- **FR-093**: Toda operación que modifique los recursos de una cuenta MUST ejecutarse dentro de una transacción.
- **FR-094**: El sistema MUST tomar un bloqueo pesimista de la cuenta antes de validar los recursos disponibles y MUST mantenerlo durante toda la operación, de modo que dos operaciones concurrentes no puedan consumir los mismos recursos.
- **FR-095**: La validación de recursos, la creación de la orden, la actualización lógica de los recursos y el registro de idempotencia MUST ocurrir en una misma transacción; ninguna operación MUST quedar parcialmente persistida.
- **FR-096**: Todos los importes, precios y cantidades MUST representarse con precisión decimal exacta y MUST NOT usar punto flotante.

**Fuera de alcance**

- **FR-097**: El sistema MUST NOT implementar autenticación, autorización, registro de usuarios, carga o actualización de cotizaciones, motor de emparejamiento, ejecución automática de órdenes `LIMIT`, integración con mercados o brokers, transferencias bancarias reales, tablas persistidas de posiciones o saldos, ni procesamiento asíncrono.

### Key Entities *(include if feature involves data)*

- **Cuenta**: Persona titular de una cuenta habilitada para operar. Se identifica por un número de cuenta único y tiene una dirección de correo única. Agrupa órdenes, claves de idempotencia y, por derivación, efectivo y posiciones.
- **Instrumento**: Valor negociable del catálogo, con ticker único, nombre y tipo (acciones o moneda). El instrumento de efectivo, de ticker `ARS` y tipo moneda, no es operable como compra ni venta.
- **Orden**: Operación registrada por una cuenta. Guarda instrumento, lado (`BUY`, `SELL`, `CASH_IN`, `CASH_OUT`), tipo (`MARKET`, `LIMIT`), cantidad, precio (vacío cuando no hubo precio de ejecución), estado (`NEW`, `FILLED`, `REJECTED`, `CANCELLED`), motivo de rechazo y fecha y hora. La misma columna de cantidad representa el número de acciones en compras y ventas, o el importe en pesos de una transferencia. Es la fuente de verdad del efectivo y de las posiciones.
- **Cotización**: Registro de precios de un instrumento en un instante, con apertura, máximo, mínimo, cierre y cierre anterior. El cierre más reciente es el precio de referencia para ejecutar y valuar; el cierre anterior permite el rendimiento diario.
- **Clave de idempotencia**: Registro que asocia una cuenta, una clave de idempotencia, la huella del contenido de la solicitud y la orden resultante. La combinación de cuenta y clave es única y cada orden está vinculada a, como máximo, una clave.
- **Posición** (derivada): Vista por cuenta e instrumento calculada a partir de las órdenes ejecutadas, que expone cantidad total, cantidad disponible, cantidad reservada, precio actual, valor de mercado, costo promedio ponderado, rendimiento total y rendimiento diario.
- **Portfolio** (derivado): Vista por cuenta que consolida el efectivo total, el efectivo reservado, el efectivo disponible, las posiciones y el valor total.

## Business Rules *(mandatory)*

### Ciclo de vida de las órdenes

- **BR-001**: Una orden `MARKET` de compra o venta se ejecuta de inmediato al crearse y pasa a `FILLED`.
- **BR-002**: Una orden `LIMIT` se crea en estado `NEW` y permanece allí hasta que se cancela.
- **BR-003**: Solo una orden en estado `NEW` puede pasar a `CANCELLED`.
- **BR-004**: `FILLED`, `REJECTED` y `CANCELLED` son estados terminales e inmutables.
- **BR-005**: Una orden rechazada por regla de negocio se persiste con estado `REJECTED` y motivo, no modifica los recursos de la cuenta y responde `201 Created`.
- **BR-006**: Una orden con datos de entrada inválidos no se persiste y responde `400 Bad Request`.
- **BR-007**: Las transferencias de efectivo se ejecutan de inmediato, quedan `FILLED` y no son cancelables.

### Cálculos de recursos

- **BR-008**: `efectivoTotal = Σ(CASH_IN ejecutados) − Σ(CASH_OUT ejecutados) − Σ(compras ejecutadas) + Σ(ventas ejecutadas)`.
- **BR-009**: `efectivoReservado = Σ(cantidad × precio límite) de compras en estado NEW`.
- **BR-010**: `efectivoDisponible = efectivoTotal − efectivoReservado`.
- **BR-011**: `cantidadTotal = Σ(compras ejecutadas) − Σ(ventas ejecutadas)` por instrumento.
- **BR-012**: `cantidadReservada = Σ(cantidad) de ventas en estado NEW` por instrumento; `cantidadDisponible = cantidadTotal − cantidadReservada`.
- **BR-013**: `valorTotalCuenta = efectivoTotal + Σ(valorDePosición)`.
- **BR-014**: `valorDePosición = cantidadTotal × último cierre disponible`.
- **BR-015**: `costoPromedio = Σ(costo de compras ejecutadas) / Σ(cantidad comprada)`; una venta reduce la cantidad manteniendo el costo promedio vigente.
- **BR-016**: `rendimientoTotal = (valorDePosición − costoDeAdquisición) / costoDeAdquisición × 100`.
- **BR-017**: `rendimientoDiario = (cierreActual − cierreAnterior) / cierreAnterior × 100`.
- **BR-018**: Si el costo de adquisición o el cierre anterior no existen o son cero, el rendimiento correspondiente se informa como no disponible.
- **BR-019**: Las órdenes `NEW` solo generan reservas; las `REJECTED` y las `CANCELLED` no generan reservas ni modifican recursos.
- **BR-020**: El efectivo disponible, las acciones disponibles y las posiciones no pueden quedar nunca en valores negativos.

### Reglas de las operaciones

- **BR-021**: El precio de ejecución de una orden `MARKET` es el último cierre disponible; si no existe o no es un valor positivo, la orden se persiste como `REJECTED`.
- **BR-022**: El precio de una orden `LIMIT` es el precio límite informado por el usuario.
- **BR-023**: Una compra se rechaza si `cantidad × precio > efectivoDisponible`.
- **BR-024**: Una venta se rechaza si `cantidad > cantidadDisponible` del instrumento.
- **BR-025**: Una orden `LIMIT` en estado `NEW` reserva efectivo (compras: `cantidad × precio límite`) o acciones (ventas: `cantidad`); la reserva reduce los disponibles pero no el patrimonio total.
- **BR-026**: Un ingreso de efectivo requiere un importe positivo y aumenta el efectivo en ese importe.
- **BR-027**: Un retiro requiere un importe positivo y se rechaza si supera el efectivo disponible, descontadas las reservas de compras pendientes.
- **BR-028**: Al usar importe, `cantidad = ⌊importe / precioDeReferencia⌋`; el sobrante queda disponible. Si la cantidad resulta cero, la orden se persiste como `REJECTED`.
- **BR-029**: Toda operación que modifique recursos es atómica y consistente ante concurrencia.

### Idempotencia

- **BR-030**: La huella de una solicitud se calcula sobre ticker, lado, tipo, cantidad o importe y precio, y es independiente del orden de los campos recibidos.
- **BR-031**: La clave de idempotencia se resuelve dentro de la cuenta identificada.
- **BR-032**: Ante una clave repetida con la misma huella se devuelve el resultado original; ante la misma clave con huella distinta se responde `409 Conflict`.

### Alcance funcional

- **BR-033**: El sistema no implementa autenticación ni autorización; la cuenta se identifica con la cabecera `X-AccountNumber`.
- **BR-034**: El sistema no implementa motor de empajamiento ni ejecución automática de órdenes `LIMIT`.
- **BR-035**: El efectivo se representa con el instrumento de tipo moneda y no genera posiciones de acciones.
- **BR-036**: Las posiciones y los saldos no se persisten; se derivan de las órdenes registradas.
- **BR-037**: Las cotizaciones se cargan por anticipado y su actualización queda fuera del alcance.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las consultas de portfolio se muestran en menos de 1 segundo desde que el usuario las solicita.
- **SC-002**: El 95% de las órdenes creadas se confirman o rechazan en menos de 2 segundos.
- **SC-003**: El 100% de las órdenes rechazadas por regla de negocio quedan persistidas con estado `REJECTED` y un motivo legible, y dejan el efectivo y las posiciones exactamente como estaban antes de la operación.
- **SC-004**: El valor total del portfolio coincide al centavo con la suma del efectivo total y el valor de mercado de las posiciones en el 100% de los casos verificados.
- **SC-005**: El 100% de las entradas inválidas recibe una respuesta de error clara y ningún caso termina en error interno del servidor.
- **SC-006**: El 100% de los reintentos con la misma clave de idempotencia y el mismo contenido devuelven el resultado original y generan una sola orden.
- **SC-007**: El 100% de los reintentos con la misma clave y contenido distinto reciben un conflicto y no generan una orden nueva.
- **SC-008**: En el 100% de los escenarios concurrentes probados, el efectivo total, el efectivo disponible y las cantidades total y disponible permanecen no negativos, y la suma de reservas nunca supera los recursos disponibles.
- **SC-009**: El 100% de las órdenes `MARKET` ejecutadas usa el último cierre disponible como precio, verificable en los valores registrados.
- **SC-010**: El 100% de las cancelaciones sobre órdenes que no están en `NEW` se rechaza sin alterar el estado original, el efectivo ni las posiciones.
- **SC-011**: El 100% de las cancelaciones de órdenes `NEW` libera los recursos reservados, y el efectivo o las acciones disponibles quedan restituidos.
- **SC-012**: El 100% de los listados y búsquedas de instrumentos devuelve resultados sin duplicados.
- **SC-013**: El 100% de las búsquedas sin coincidencias devuelve una lista vacía con respuesta de éxito, y el 100% de las búsquedas vacías se rechazan con un error de validación.
- **SC-014**: El 100% de los listados paginados del historial devuelve únicamente órdenes de la cuenta consultada y un total de elementos consistente con los filtros aplicados.
- **SC-015**: Existe una prueba funcional automatizada del envío de órdenes que cubre, como mínimo, una compra `MARKET` ejecutada, una compra `LIMIT` pendiente, una venta por importe y un rechazo por saldo insuficiente persistido como `REJECTED`; la prueba pasa en cada validación del entregable.
- **SC-016**: Un usuario puede completar el recorrido registrar efectivo → consultar instrumentos → enviar una orden → consultar el portfolio, sin asistencia manual y viendo reflejados sus cambios.
- **SC-017**: El 100% de las consultas de un instrumento sin cotización o sin cierre anterior válido devuelven un resultado válido con los renders no disponibles, sin errores ni divisiones por cero.

## Assumptions

- **Cuentas preexistentes**: las cuentas ya están registradas en la base de datos; no se implementa alta, baja ni modificación de cuentas.
- **Sin autenticación**: la cuenta se identifica con la cabecera `X-AccountNumber`; no se validan credenciales ni roles.
- **Cotizaciones precargadas**: los datos de mercado ya están cargados; la carga y la actualización de cotizaciones quedan fuera del alcance.
- **Precisión monetaria**: todos los importes y precios están expresados en pesos argentinos con dos decimales; las cantidades de acciones son enteras.
- **Precios de mercado**: se usa el cierre más reciente disponible; un cierre nulo, cero o negativo se considera no disponible.
- **Cálculo de cantidad por importe**: cuando la orden usa importe, la cantidad se calcula con el precio de ejecución de la orden —el último cierre en `MARKET`, el precio límite en `LIMIT`—, de forma análoga a la regla del challenge original.
- **Reservas**: las órdenes `NEW` reservan efectivo o acciones y las reservas solo reducen la disponibilidad, nunca el patrimonio total.
- **Valores no disponibles**: los rendimientos y precios sin dato válido se informan como no disponibles, no como cero.
- **Fecha y hora**: la asigna el servidor al recibir la operación, en convención UTC, y se expone en formato ISO-8601 sin zona horaria.
- **Alcance excluido**: autenticación, autorización por roles, carga de cotizaciones, motor de emparejamiento, ejecución automática de órdenes `LIMIT`, comisiones, impuestos, multi-moneda, acciones fraccionarias, integración con brokers y transferencias bancarias reales.
- **Detalle individual de órdenes fuera de alcance**: la consulta de una orden por identificador no forma parte de esta versión.
- **Alcance consolidado**: esta especificación es la única vigente del proyecto y cubre de punta a punta el challenge backend: portfolio, instrumentos, órdenes, transferencias, idempotencia e historial.

## Out of Scope

- Autenticación, autorización, roles y registro de usuarios.
- Alta, baja y modificación de cuentas, instrumentos y cotizaciones.
- Carga o actualización automática de cotizaciones.
- Motor de emparejamiento y simulación de mercado.
- Ejecución automática de órdenes `LIMIT`.
- Integración con mercados reales, brokers o transferencias bancarias reales.
- Tablas persistidas de posiciones y de saldos.
- Endpoint de detalle individual de órdenes.
- Comisiones, impuestos, multi-moneda y acciones fraccionarias.
- Procesamiento asíncrono de órdenes.
