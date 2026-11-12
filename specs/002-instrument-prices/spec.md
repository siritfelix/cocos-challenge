# Feature Specification: Precios en la consulta de instrumentos

**Feature Branch**: `002-instrument-prices`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "Mejora funcional: Incorporar precios en la consulta de instrumentos. Extender el endpoint `GET /api/v1/instruments` para que, además de los datos básicos de cada instrumento, devuelva información de precios obtenida del registro de cotizaciones (último precio disponible y precio de cierre anterior), sin necesidad de realizar una segunda llamada. No se crean endpoints ni tablas nuevas, no se modifica la estructura de cotizaciones ni las reglas de negocio."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar el catálogo con precios (Priority: P1)

Un cliente de la API consulta el catálogo de instrumentos y, en la misma respuesta, recibe para cada instrumento su último precio disponible y el precio de cierre anterior. Ya no necesita encadenar una segunda llamada a otro recurso para conocer los precios.

**Why this priority**: Es el valor central de la mejora: una sola llamada entrega catálogo + precios. Sin esto, la funcionalidad no existe.

**Independent Test**: Se consulta el catálogo completo y se verifica que cada instrumento con cotización disponible muestre su último precio y su cierre anterior, coincidentes con la cotización más reciente del instrumento.

**Acceptance Scenarios**:

1. **Given** el catálogo de instrumentos cargado, **When** el cliente consulta el listado completo, **Then** cada instrumento con cotización disponible incluye su último precio disponible y el precio de cierre anterior.
2. **Given** un instrumento con varias cotizaciones registradas, **When** el cliente consulta el listado, **Then** el precio informado corresponde a la cotización **más reciente** (no a una anterior).
3. **Given** un instrumento sin ninguna cotización registrada, **When** el cliente consulta el listado, **Then** la respuesta contiene al instrumento con sus datos básicos intactos y los campos de precio sin valor, y la solicitud responde exitosamente.

---

### User Story 2 - Precios también en los resultados de búsqueda (Priority: P2)

Un cliente que busca instrumentos por texto (ticker o nombre, sin distinguir mayúsculas de minúsculas) recibe en cada resultado los mismos campos de precio que en el listado completo, de modo que cualquier vista de catálogo o de búsqueda muestre precios de forma homogénea.

**Why this priority**: Complementa el flujo principal: la búsqueda es el mecanismo de acceso existente y debe comportarse igual que el listado.

**Independent Test**: Se consulta el catálogo con un término de búsqueda y se verifica que los resultados coinciden con los del mecanismo actual (mismos instrumentos) y que cada resultado incluye los campos de precio.

**Acceptance Scenarios**:

1. **Given** el mecanismo de búsqueda vigente, **When** el cliente busca por ticker o por nombre, **Then** los resultados son los mismos que los actuales, con los campos de precio incluidos.
2. **Given** una búsqueda sin coincidencias, **When** el cliente consulta con un término que no matchea nada, **Then** la respuesta es un listado vacío exitoso (sin cambios respecto del comportamiento actual).

---

### User Story 3 - Comportamiento y estructura conservados (Priority: P3)

El cliente existente no se ve afectado: la estructura general de la respuesta se mantiene (los campos básicos actuales no cambian de nombre ni de significado) y el comportamiento de errores es el mismo que hoy. La mejora es aditiva: agrega campos de precio, no reemplaza ni renombra nada.

**Why this priority**: Es una garantía de compatibilidad, no una funcionalidad nueva: protege a los consumidores actuales durante el despliegue.

**Independent Test**: Se repiten las consultas y búsquedas existentes y se verifica que los campos actuales responden igual que antes de la mejora y que los casos de error (consultas inválidas) siguen respondiendo con los mismos códigos y estructura de error.

**Acceptance Scenarios**:

1. **Given** clientes existentes del catálogo, **When** consumen el endpoint después de la mejora, **Then** los campos básicos actuales (identificador, ticker, nombre, tipo) llegan con el mismo formato que antes.
2. **Given** una consulta inválida (por ejemplo, término de búsqueda vacío), **When** el cliente consulta, **Then** la respuesta sigue siendo el mismo código de error con la estructura de error unificada.

---

### Edge Cases

- ¿Qué pasa cuando un instrumento no tiene ninguna cotización registrada? La respuesta lo incluye con sus datos básicos y los campos de precio sin valor; no falla ni lo excluye del listado.
- ¿Qué pasa cuando un instrumento tiene varias cotizaciones? Solo la más reciente alimenta los campos de precio; las anteriores se ignoran para esta consulta.
- ¿Qué pasa cuando el instrumento de efectivo (que no se negocia a precio) aparece en el listado? Se lo trata igual que cualquier instrumento sin cotización aplicable: datos básicos + campos de precio sin valor, sin error.
- ¿Qué pasa con la búsqueda vacía o con solo espacios? Se mantiene el comportamiento actual de validación (error de solicitud con estructura de error unificada).
- ¿Qué pasa si la cotización más reciente de un instrumento no tiene precio utilizable? El campo de precio correspondiente se informa sin valor; el instrumento no se excluye.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST devolver, en la consulta del catálogo de instrumentos, el último precio disponible de cada instrumento obtenido de su cotización más reciente registrada.
- **FR-002**: El sistema MUST devolver, en la misma respuesta, el precio de cierre anterior correspondiente a la cotización más reciente de cada instrumento.
- **FR-003**: El sistema MUST incluir ambos campos de precio en el listado completo y en cada resultado de búsqueda, con el mismo significado en ambos casos.
- **FR-004**: El sistema MUST mantener el mecanismo de búsqueda vigente (filtrado por texto sobre ticker y/o nombre, sin distinguir mayúsculas de minúsculas, resultados sin duplicados): los resultados y las validaciones NO cambian respecto del comportamiento actual.
- **FR-005**: El sistema MUST responder exitosamente ante un instrumento sin cotización registrada, informando sus datos básicos con los campos de precio sin valor.
- **FR-006**: El sistema MUST exponer los campos de precio como información aditiva: la estructura general de la respuesta del endpoint se conserva (los campos actuales no se renombran ni se eliminan).
- **FR-007**: El sistema NO MUST alterar ninguna regla de negocio existente: la creación de órdenes, los cálculos del portfolio y el comportamiento de errores del catálogo permanecen sin cambios.
- **FR-008**: El sistema NO MUST crear endpoints nuevos, tablas nuevas ni modificar la estructura del registro de cotizaciones.
- **FR-009**: El contrato publicado de la API (documentación del endpoint) MUST reflejar los nuevos campos de precio y quedar consistente con el comportamiento real del endpoint.

### Key Entities *(include if feature involves data)*

- **Instrumento**: elemento del catálogo identificable por ticker, con nombre y tipo (acción o moneda). Es lo que el cliente recibe, ahora con precios adjuntos.
- **Cotización**: registro de precios de un instrumento en un instante, del cual se obtienen el último precio disponible (cierre) y el precio de cierre anterior. Para esta consulta, solo la cotización más reciente de cada instrumento es relevante.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un cliente obtiene el catálogo completo con precios en **una sola llamada** al endpoint de instrumentos (0 llamadas adicionales, comparado con las 2 que hoy requieren catálogo + cotizaciones).
- **SC-002**: El 100% de los instrumentos con cotización disponible en el listado informa su último precio y su cierre anterior coincidentes con la cotización más reciente de cada instrumento.
- **SC-003**: El 100% de los instrumentos sin cotización disponible aparecen en la respuesta con datos básicos y campos de precio sin valor, sin que la solicitud falle.
- **SC-004**: Los resultados de búsqueda son idénticos a los del comportamiento actual (mismos instrumentos, mismo orden) para cualquier término de búsqueda, con los campos de precio agregados.
- **SC-005**: Las consultas inválidas existentes (término vacío u otros casos de validación vigentes) responden con los mismos códigos y la misma estructura de error que hoy.
- **SC-006**: El tiempo de respuesta del catálogo se mantiene dentro del orden de magnitud actual (sin degradación perceptible para el volumen del catálogo en producción).

## Assumptions

- **Último precio disponible** es el cierre de la cotización más reciente registrada del instrumento; **precio de cierre anterior** es el valor de cierre anterior que esa misma cotización referencia. Ambos provienen del mismo registro, que es la única fuente de verdad de precios.
- El instrumento de efectivo puede no tener cotizaciones aplicables; se trata como instrumento sin cotización (campos de precio sin valor).
- No se agrega paginación al catálogo: se mantiene el listado completo actual.
- No se calculan rendimientos ni variaciones porcentuales en esta mejora: solo se exponen los dos valores de precio pedidos.
- Los clientes actuales del endpoint consumen la respuesta como JSON con los campos básicos actuales; agregar campos es compatible con ese consumo.
