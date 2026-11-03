# Implementation Plan: Detalle de Plato, Información Nutricional, Precios y Filtros Manuales de Catálogo

**Branch**: `011-dish-detail-manual-filters` | **Fecha**: 01/11/2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-dish-detail-manual-filters/spec.md`

**Companion artifacts**: [research.md](./research.md) · [data-model.md](./data-model.md) · [contracts/behavioral-contracts.md](./contracts/behavioral-contracts.md) · [contracts/openapi.yaml](./contracts/openapi.yaml) · [quickstart.md](./quickstart.md)

---

## 1. Summary

Implementar la extensión integral de la experiencia del comensal sobre el catálogo gastronómico de EasyEat, abordando las cuatro historias de usuario aprobadas:
1. **COM-5.1.1 (Visualizar multimedia y nombre)**: Panel superpuesto con fotografía en alta resolución, nombre comercial y descripción detallada sin abandonar la vista del catálogo. Preservación exacta de la posición de desplazamiento vertical (`window.scrollY`) al cerrar por botón o clic exterior. Degradación elegante en conexiones lentas (texto e indicador temporal primero, imagen en alta resolución después). Ausencia de fotografía sin placeholders rotos ni recuadros vacíos. Adaptación estructural con scroll interno para textos largos. Deshabilitación reactiva en $\le 3$ s ante agotamiento de stock o baja administrativa sin cerrar el panel.
2. **COM-5.1.3 (Ver precio final y subtotal provisorio)**: Presentación destacada del precio final con impuestos incluidos junto a los controles de acción. Formateo monetario regional unificado (`$ 12.500,00`). Reflejo de modificaciones de precio administrativo en la siguiente recarga o reapertura sin nuevo inicio de sesión. Selector de cantidad $N \ge 1$ con recálculo dinámico de subtotal provisorio sin tope artificial en la estimación; validación estricta de inventario contra stock remanente de cocina (descontando unidades ya en el carrito) al accionar "Agregar al Carrito".
3. **COM-5.1.2 (Ver ingredientes y alérgenos)**: Desglose completo de ingredientes y sellos identificadores de alérgenos neutros y uniformes para todos los comensales (sin alertas preventivas ni personalización por perfil dietario en esta vista informativa). Reflejo dinámico de cambios de receta en cocina. Mensaje explícito `"Lista de componentes no disponible"` ante platos sin receta registrada.
4. **COM-4.2.2 (Búsqueda por filtros manuales de catálogo)**: Barra lateral con selección múltiple de categorías (semántica disyuntiva OR) y rango de precio delimitado por mínimo y máximo (intervalo cerrado inclusivo $[P_{\min}, P_{\max}]$). Composición conjuntiva estricta (intersección AND) con la barra de búsqueda rápida por texto existente (COM-4.2.3). Visibilidad no excluyente de platos agotados (mostrados atenuados y deshabilitados). Persistencia de filtros en `sessionStorage` aislada por establecimiento (sobrevive navegación a perfil o carrito). Limpieza unificada que restablece categorías, rango de precios y término de búsqueda de texto simultáneamente.

El enfoque técnico reutiliza de manera estricta los componentes e infraestructura preexistentes: canal de sincronización por polling condicional con ETags de la especificación 008, formato regional del spec 007, buscador por texto de COM-4.2.3, contexto de tenant de spec 008, y carrito de compras de spec 009. Cero nuevas tablas o migraciones Flyway en la base de datos.

---

## 2. Technical Context

- **Language/Version**: Java 21 (LTS) en backend; TypeScript 5.6 / React 18.3.1 en frontend. *(Verificado en `easyeat/backend/pom.xml` y `easyeat/frontend/package.json`)*.
- **Primary Dependencies**:
  - *Backend*: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Spring Security, Spring Validation), Flyway Core 10.x, PostgreSQL Driver (`42.7.4`), JJWT (`0.12.5`), Lombok, Jackson Problem (`problem-spring-web` RFC 7807), Testcontainers (`1.20.1`).
  - *Frontend*: React 18.3.1, React Router v6 (`react-router-dom`), Tailwind CSS v4, Lucide React (iconos), Axios (`apiClient.ts`).
- **Storage**: PostgreSQL 16 (tablas existentes `dishes`, `dish_photos`, `dish_recipe_ingredients`, `ingredients`, `dish_dietary_restrictions`, `establishments`); `sessionStorage` para persistencia de filtros manuales (`easyeat_catalog_filters_{tenantId}`) y carrito (`easyeat_cart`). Cero nuevas migraciones requeridas (registro Flyway intacto en V10).
- **Testing**:
  - *Backend*: JUnit 5, Mockito, MockMvc, AssertJ, Testcontainers (PostgreSQL 16).
  - *Frontend*: Vitest 2, React Testing Library (RTL), jsdom, `@testing-library/user-event`.
- **Target Platform**: Servidores Linux / contenedores Docker para Spring Boot JAR; navegadores web modernos estándar (Chrome, Firefox, Safari, Edge) para el frontend SPA.
- **Project Type**: Full-stack Web Application (Spring Boot REST API Gateway + React SPA) en monorepo (`easyeat/backend`, `easyeat/frontend`).
- **Performance Goals**:
  - Invalidación de disponibilidad en panel abierto: latencia $\le 3\text{ s}$ ante variación de stock en cocina o despublicación (`SC-010`).
  - Recálculo dinámico de subtotal provisorio: $\le 50\text{ ms}$ en memoria del cliente (`SC-011`).
  - Filtrado multidimensional de catálogo: $\le 100\text{ ms}$ en cliente sobre el menú activo.
  - Preservación exacta de posición de scroll: 100% de recuperaciones sin salto visual (`SC-002`).
- **Constraints**:
  - Autenticación con rol `ROLE_COMENSAL` para acceso al catálogo.
  - Aislamiento multi-tenant estricto por `tenantId` en consultas backend y en la clave de sesión de filtros frontend.
  - Aritmética decimal exacta en centavos para subtotales provisorios y precios.
  - Prohibición de personalización de alérgenos por perfil dietario en la vista informativa (Clarification #4).
  - Prohibición de tope artificial en el selector de cantidad previo (Clarification #2).
  - Reutilización de la infraestructura existente sin introducir WebSockets, SSE ni librerías externas no declaradas.
- **Scale/Scope**: 4 historias de usuario (COM-5.1.1, COM-5.1.3, COM-5.1.2, COM-4.2.2), 20 criterios de aceptación, 22 casos borde, 17 invariantes de negocio, 24 requerimientos funcionales, 14 criterios de éxito, 11 supuestos y 5 aclaraciones formales.

---

## 3. Constitution Check

*GATE: Evaluación frente a la Constitución SDD versión 2.0.0. Aprobado antes de la fase de planificación y re-evaluado post-diseño.*

| Principio | Estado Pre-Design | Estado Post-Design | Evidencia y Justificación Técnica |
|---|---|---|---|
| **I. The Specification Is the Source of Truth** | **PASS** | **PASS** | El plan cubre con fidelidad matemática y operativa los 20 criterios de aceptación (20/20), 22 casos borde (22/22), 17 invariantes (17/17), 24 requerimientos funcionales (24/24), 14 criterios de éxito (14/14) y 5 aclaraciones de `spec.md`. No se redefine ningún requisito. |
| **II. Specification Before Implementation** | **PASS** | **PASS** | `spec.md` aprobado y ratificado sin incógnitas pendientes (`[NEEDS CLARIFICATION]`: 0). Se produjeron todos los artefactos de diseño requeridos (`research.md`, `data-model.md`, `contracts/behavioral-contracts.md`, `contracts/openapi.yaml`, `quickstart.md`, `plan.md`) antes de codificar o generar tareas. |
| **III. Layered Verification Proportional to Risk** | **PASS** | **PASS** | Estrategia de pruebas en 4 capas: (1) Unitarias de álgebra de filtros (OR, AND), aritmética de centavos e invariantes; (2) Integración backend MockMvc para contratos REST y DTOs enriquecidos; (3) Pruebas de componentes RTL para `DishDetailModal`, `DishCard`, scroll restoration y selector numérico; (4) Escenarios end-to-end ejecutables en `quickstart.md`. |
| **IV. Simple, Cohesive, and Justified Design** | **PASS** | **PASS** | Se reutiliza el canal de polling con ETags existente (`useRealtimeAvailability`) en lugar de añadir WebSockets o SSE. Se reutiliza `matchesDishQuery` y `sessionStorage`. Cero patrones innecesarios. Cohesión alta, bajo acoplamiento. |
| **V. Bounded Scope** | **PASS** | **PASS** | Alcance estrictamente delimitado a detalle de plato, información nutricional neutra, precios y filtros manuales. Ordenamiento, favoritos, recomendaciones, alérgenos excluyentes y pasarelas de pago quedan explícitamente en Out of Scope. |
| **VI. Controlled Technical Debt** | **PASS** | **PASS** | Cero impacto sobre el esquema de base de datos; no se agregan tablas ni se alteran constraints existentes (Flyway V10 preservado). Se corrigen las discrepancias previas de `DishDetailModal` (neutralidad de alérgenos y ausencia de imagen). |
| **VII. Traceability, Consistency, and Documentation** | **PASS** | **PASS** | Matriz de trazabilidad bidireccional exhaustiva (Sección 8): cada AC, EC, INV, FR, SC y Clarification está explícitamente vinculado a una decisión técnica, contrato y prueba verificable. |
| **VIII. Evidence Over Assumption and Human Escalation** | **PASS** | **PASS** | Decisiones ancladas 100% en el código fuente del repositorio (`DinerCatalogController.java`, `DishDetailModal.tsx`, `useRealtimeAvailability.ts`, `CatalogSearchFilter.tsx`) y en las 5 aclaraciones ratificadas el 01/11/2026. 11 supuestos documentados como `[SUPUESTO]`. |
| **IX. Engineering Over Convenience** | **PASS** | **PASS** | Se respetan las garantías más estrictas: latencia $\le 3$ s para invalidación, preservación milimétrica de scroll sin reseteos, y validación atómica de inventario al agregar al carrito. Formato estructurado ProblemDetail RFC 7807 para errores. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | **PASS** | **PASS** | Vertical slice full-stack completo: backend (extensión de DTOs y servicios de catálogo/alérgenos en Spring Boot) y frontend (componentes React, modal superpuesto, barra lateral de filtros, hooks de disponibilidad y contexto de sesión). |

---

## 4. Complexity Tracking

> *El diseño adopta los patrones existentes más directos y simples del repositorio. Cero violaciones a los principios de la Constitución.*

| Violación | Por qué es necesaria | Alternativa más simple rechazada porque |
|---|---|---|
| Ninguna | N/A | N/A |

---

## 5. Project Structure & Source Code Layout

### 5.1 Documentación de la Feature (este módulo)

```text
specs/011-dish-detail-manual-filters/
├── spec.md                          # Especificación funcional aprobada
├── checklists/
│   ├── requirements.md              # Checklist de calidad de requisitos
│   └── detail-and-filters-quality.md # Checklist de calidad de detalle y filtros
├── plan.md                          # Este plan técnico de implementación
├── research.md                      # Fase 0: investigación y decisiones técnicas
├── data-model.md                    # Fase 1: modelo de datos conceptual
├── quickstart.md                    # Fase 1: guía de validación ejecutable end-to-end
├── contracts/                       # Fase 1: especificación de contratos
│   ├── openapi.yaml                 # Contrato OpenAPI 3.0.3 para endpoints y DTOs
│   └── behavioral-contracts.md      # Contratos de comportamiento detallados (7 operaciones)
└── tasks.md                         # Fase 2: desglose de tareas (a ser generado por /speckit-tasks)
```

### 5.2 Estructura del Código Fuente en el Repositorio

```text
easyeat/
├── backend/
│   ├── src/
│   │   ├── main/java/com/easyeat/catalog/
│   │   │   ├── controller/
│   │   │   │   └── DinerCatalogController.java            # Expone catálogo con alérgenos neutros y availability
│   │   │   ├── dto/
│   │   │   │   ├── DishCatalogItemDto.java                # Extendido: campo allergens neutro (List<String>)
│   │   │   │   └── DinerCatalogResponse.java              # Estructura del menú categorizado
│   │   │   ├── repository/
│   │   │   │   └── DishRepository.java                    # Consultas de platos publicados por tenant
│   │   │   └── service/
│   │   │       ├── DinerCatalogService.java               # Mapeo de alérgenos neutros y recetas en catálogo
│   │   │       └── DishAvailabilityEngine.java            # Motor dinámico de disponibilidad y ETags
│   │   └── test/java/com/easyeat/catalog/
│   │       ├── controller/DinerCatalogControllerTest.java # Pruebas MockMvc de catálogo y disponibilidad
│   │       ├── service/DinerCatalogServiceTest.java       # Pruebas unitarias de recetas, alérgenos y precios
│   │       └── service/DishAvailabilityEngineTest.java    # Pruebas de stock y hash ETag
│   └── pom.xml
│
└── frontend/
    ├── src/
    │   ├── api/
    │   │   └── dinerCatalogApi.ts                         # Cliente Axios para catálogo y availability polling
    │   ├── types/
    │   │   └── dinerCatalog.ts                            # Extendido: DishCatalogItem con allergens: string[]
    │   ├── utils/
    │   │   └── currencyUtils.ts                           # Utilidad de formato regional ($ 12.500,00) y centavos
    │   ├── context/
    │   │   ├── CatalogFilterContext.tsx                   # Extendido: estado de filtros manuales y persistencia
    │   │   ├── TenantContext.tsx                          # Gestión de establecimiento activo
    │   │   └── CartContext.tsx                            # Extendido: adición de N unidades con validación
    │   ├── hooks/
    │   │   ├── useRealtimeAvailability.ts                 # Hook de polling condicional con ETags (2.500 ms)
    │   │   └── useCatalogFilter.ts                        # Hook para consumo de filtros manuales
    │   ├── components/
    │   │   └── catalog/
    │   │       ├── DishDetailModal.tsx                    # Refactorizado: imagen condicional, alérgenos neutros,
    │   │       │                                          # selector sin tope, scroll interno, invalidación real-time
    │   │       ├── DishCard.tsx                           # Tarjeta de plato con formato regional y estado sin stock
    │   │       ├── ManualFiltersSidebar.tsx               # Nuevo: barra lateral de categorías (OR) y rango de precio
    │   │       ├── CatalogSearchFilter.tsx                # Reutilizado: barra de búsqueda por texto (COM-4.2.3)
    │   │       └── EmptyFilterResultsNotice.tsx           # Nuevo: estado sin resultados y botón limpiar filtros
    │   └── pages/
    │       └── CatalogPage.tsx                            # Integración de sidebar, grilla filtrada y modal
    └── test/
        └── catalog/
            ├── DishDetailModal.test.tsx                   # Pruebas de modal, scroll, degradación e invalidación
            ├── DishCard.test.tsx                          # Pruebas de tarjetas, formato monetario y stock
            ├── ManualFiltersSidebar.test.tsx              # Pruebas de categorías OR, rango AND y validación Pmin > Pmax
            └── CatalogPage.test.tsx                       # Pruebas de integración, persistencia y limpieza unificada
```

**Decisión de Estructura**: Arquitectura web estándar en monorepo existente. El backend extiende el paquete `com.easyeat.catalog` sin añadir capas artificiales. El frontend organiza los componentes dentro de `src/components/catalog/`, integrados en `CatalogPage.tsx`.

---

## 6. Architectural & Technical Decisions

### D-001 — Invalidación Reactiva en Panel Abierto sin Cierre Intempestivo
- **Decisión**: Reutilizar el hook `useRealtimeAvailability.ts` (polling condicional cada 2.500 ms con ETags contra `GET /api/v1/establishments/{tenantId}/catalog/availability`). Ante agotamiento de insumo principal o baja administrativa (`status = CANCELLED` o `isActive = false`), el panel conmuta a estado "Plato no disponible", muestra badge "Agotado" y deshabilita los controles de cantidad y compra en un tiempo $\le 3$ s sin cerrar el modal. Ante reabastecimiento en cocina, rehabilita los controles automáticamente (EC-005). Al cerrar el modal, el catálogo subyacente se actualiza.
- **Fuente**: `spec.md §Clarifications 2026-11-01 Q5, §INV-5, §FR-007, §SC-010, §EC-004, §EC-005` + `easyeat/frontend/src/hooks/useRealtimeAvailability.ts`.
- **Cubre**: AC-1.6, EC-004, EC-005, INV-5, FR-007, SC-010, C-5.
- **Riesgo**: Latencia de red o pestaña en segundo plano.
- **Mitigación**: El polling a 2.500 ms asegura el cumplimiento estricto del SLA de 3 segundos; `document.hidden` pausa el polling y reanuda de inmediato al volver el foco.
- **Alternativa descartada**: WebSockets o Server-Sent Events; descartados por inexistencia en el repo y violación de simplicidad (Principio IV).

### D-002 — Álgebra de Filtros Manuales y Composición Conjuntiva con Búsqueda de Texto
- **Decisión**: Implementar en cliente el predicado algebraico:
  $$\text{matches}(p) \iff \left(C_{\text{sel}} = \emptyset \lor p.\text{category} \in C_{\text{sel}}\right) \land \left(P_{\min} \le p.\text{retailPrice} \le P_{\max}\right) \land \text{matchesDishQuery}(p, T_{\text{busq}})$$
  Las categorías se unen mediante OR; el rango de precio se evalúa como intervalo cerrado e inclusivo en ambos límites ($P_{\min} \le p \le P_{\max}$); el término de búsqueda por texto se evalúa mediante la función probada `matchesDishQuery` de `CatalogSearchFilter.tsx`. Ante $P_{\min} > P_{\max}$, se bloquea la aplicación y se despliega advertencia de validación.
- **Fuente**: `spec.md §Clarifications 2026-11-01 Q1, §INV-14, §FR-016 a FR-018, §EC-012 a EC-016` + `easyeat/frontend/src/components/catalog/CatalogSearchFilter.tsx`.
- **Cubre**: AC-4.1, AC-4.2, AC-4.3, EC-012, EC-013, EC-014, EC-015, EC-016, INV-14, FR-016, FR-017, FR-018, FR-022, SC-012, C-1.
- **Riesgo**: Inconsistencia entre límites de precio o filtrado insensible a acentos.
- **Mitigación**: Reutilización directa de `normalizeSearchTerm` y validación tipada de inputs numéricos con mensajes inline.
- **Alternativa descartada**: Filtrado en servidor mediante queries SQL complejas; descartado porque el catálogo ya reside completo en memoria del cliente para el restaurante activo.

### D-003 — Limpieza Unificada Simultánea de Filtros y Búsqueda
- **Decisión**: La acción "Limpiar filtros" (disponible en la barra lateral y en el estado de grilla sin resultados) ejecuta simultáneamente la deselección de todas las categorías, el vaciado de los límites de precio mínimo y máximo, y el vaciado del input de búsqueda por texto, restaurando el menú original al 100%.
- **Fuente**: `spec.md §Clarifications 2026-11-01 Q1, §INV-15, §INV-16, §FR-019, §FR-020, §SC-013, §EC-017, §EC-018`.
- **Cubre**: AC-4.4, AC-4.5, EC-017, EC-018, INV-15, INV-16, FR-019, FR-020, SC-013, C-1.
- **Riesgo**: Limpieza parcial dejando texto residual.
- **Mitigación**: Función única atómica `handleClearAllFilters()` en `CatalogPage.tsx` que actualiza todos los estados coordinadamente y purga la clave de sesión.
- **Alternativa descartada**: Botones separados para limpiar filtros manuales y limpiar búsqueda; descartado por contradecir expresamente la Clarification #1.

### D-004 — Persistencia de Filtros Manuales en Sesión Local por Establecimiento
- **Decisión**: Extender `CatalogFilterContext.tsx` respaldado por `sessionStorage` bajo la clave `'easyeat_catalog_filters_' + tenantId`. Los filtros manuales sobreviven a la navegación hacia `/perfil`, `/carrito` o recargas de pestaña, y se restablecen únicamente al accionar "Limpiar filtros" o al cambiar de restaurante en `TenantContext`.
- **Fuente**: `spec.md §Clarifications 2026-11-01 Q3, §INV-16, §FR-024` + `easyeat/frontend/src/context/TenantContext.tsx`.
- **Cubre**: INV-16, FR-024, C-3.
- **Riesgo**: Mezcla de filtros entre establecimientos con cartas diferentes.
- **Mitigación**: Clave de almacenamiento particionada estrictamente por `tenantId` (`easyeat_catalog_filters_{tenantId}`).
- **Alternativa descartada**: `localStorage` (comparte estado entre pestañas ajenas) o persistencia en PostgreSQL (sobrecarga innecesaria para estado efímero).

### D-005 — Preservación de Posición de Scroll, Cierre por Overlay y Degradación de Imagen
- **Decisión**: Al abrir `DishDetailModal.tsx`, se captura `window.scrollY`. Al cerrar el modal por el botón `modal-close-btn` o por clic en el fondo exterior (`data-testid="dish-detail-modal"` con `e.stopPropagation()` en el contenedor del modal), se restituye exactamente la coordenada previa. Platos sin foto (`photoUrl == null`) omiten completamente la sección de imagen sin dejar áreas vacías ni placeholders rotos. En redes lentas, el texto descriptivo se renderiza de inmediato mientras un skeleton animado informa la carga de la imagen hasta completarse. Textos extensos activan scroll interno vertical en el modal.
- **Fuente**: `spec.md §INV-1, §INV-2, §INV-3, §INV-4, §FR-001 a FR-006, §SC-001 a SC-005, §EC-001 a EC-003, §EC-021`.
- **Cubre**: AC-1.1, AC-1.2, AC-1.3, AC-1.4, AC-1.5, EC-001, EC-002, EC-003, EC-021, EC-022, INV-1, INV-2, INV-3, INV-4, FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, SC-001, SC-002, SC-003, SC-004, SC-005.
- **Riesgo**: Salto visual al restaurar scroll en navegadores móviles.
- **Mitigación**: Uso de `window.scrollTo({ top: scrollY, behavior: 'instant' })` tras desmontar el modal.
- **Alternativa descartada**: Forzar imagen por defecto con logo institucional; rechazada por violar taxativamente la INV-2 y FR-003.

### D-006 — Formateo Monetario Regional y Consistencia de Precio con el Carrito
- **Decisión**: Centralizar el formateo en `currencyUtils.ts` aplicando la configuración regional argentina (`$ 12.500,00` con símbolo `$ `, separador de miles `.` y coma decimal). El precio visualizado deriva exclusivamente de `dish.retailPrice` (PVP vigente con impuestos de spec 007). Platos agotados exhiben su precio de referencia con botones de compra inactivos. Actualizaciones de precio por administración impactan de inmediato al reabrir el modal o recargar sin re-login.
- **Fuente**: `spec.md §INV-9, §INV-10, §INV-11, §INV-12, §FR-011 a FR-014, §SC-007 a SC-009, §EC-009, §EC-010`.
- **Cubre**: AC-3.1, AC-3.2, AC-3.3, AC-3.4, EC-009, EC-010, INV-9, INV-10, INV-11, INV-12, FR-011, FR-012, FR-013, FR-014, SC-007, SC-008, SC-009.
- **Riesgo**: Desalineación por centavos entre modal y carrito.
- **Mitigación**: Aritmética de punto fijo basada en centavos enteros (`toCents`, `fromCents`) compartida entre catálogo y `CartContext`.
- **Alternativa descartada**: Formateo manual con expresiones regulares o dependencias externas pesadas.

### D-007 — Selector de Cantidad sin Tope Artificial y Validación al Agregar al Carrito
- **Decisión**: El selector numérico en `DishDetailModal.tsx` se inicializa en $N = 1$ y permite seleccionar libremente cualquier entero $N \ge 1$ para estimación y cálculo dinámico de subtotal provisorio en pantalla sin tope artificial. Al presionar "Agregar al Carrito", se valida el inventario frente al stock de cocina remanente:
  $$N + q_{\text{en\_carrito}} \le \text{stockDisponible}$$
  Si se supera la disponibilidad, se bloquea la incorporación y se despliega mensaje de stock insuficiente. Si se cierra el panel sin confirmar, el subtotal se descarta y el selector vuelve a 1 unidad en aperturas posteriores.
- **Fuente**: `spec.md §Clarifications 2026-11-01 Q2, §INV-13, §FR-015, §SC-011, §EC-011`.
- **Cubre**: AC-3.5, EC-011, INV-13, FR-015, SC-011, C-2.
- **Riesgo**: Sobrevendencia si el comensal estima cantidades desmedidas.
- **Mitigación**: La validación estricta de corte ocurre en el botón "Agregar al Carrito", protegiendo la integridad de la orden.
- **Alternativa descartada**: Bloquear el botón `+` del selector al stock físico restante; descartada por contradecir expresamente la Clarification #2.

### D-008 — Neutralidad de Alérgenos e Información Nutricional sin Resaltes Personalizados
- **Decisión**: El modal expone la lista de ingredientes y las etiquetas identificadoras de alérgenos de forma neutral, uniforme e idéntica para todos los comensales, eliminando bordes rojos, sellos de advertencia o dependencias del perfil dietario del usuario autenticado en esta vista informativa. Si el plato carece de alérgenos registrados, se informa que no contiene alérgenos declarados. Si carece de receta, se presenta la leyenda `"Lista de componentes no disponible"`. Cambios de receta en cocina se reflejan en la siguiente apertura del panel.
- **Fuente**: `spec.md §Clarifications 2026-11-01 Q4, §INV-6, §INV-7, §INV-8, §FR-008 a FR-010, §SC-006, §EC-006 a EC-008`.
- **Cubre**: AC-2.1, AC-2.2, AC-2.3, EC-006, EC-007, EC-008, INV-6, INV-7, INV-8, FR-008, FR-009, FR-010, SC-006, C-4.
- **Riesgo**: Reintroducir componentes de advertencia dietaria de spec 003 en el modal informativo.
- **Mitigación**: Sustitución de `AllergenWarningBadge` en `DishDetailModal.tsx` por un componente de renderizado neutral de etiquetas de alérgenos (`AllergenNeutralPill`).
- **Alternativa descartada**: Alertas personalizadas en el modal; descartadas por prohibición explícita en la Clarification #4.

---

## 7. Cumplimiento de Invariantes de Negocio

| Invariante | Capa de Enforce | Mecanismo de Enforce | Test que la Verifica | ACs / ECs / FRs / SCs Relacionados |
|---|---|---|---|---|
| **INV-1: Preservación de scroll de catálogo** | Frontend | Captura de `window.scrollY` antes de abrir y restauración con `window.scrollTo` al cerrar por botón o clic en backdrop | `DishDetailModal.test.tsx` (testScrollRestorationOnClose) | AC-1.3, EC-021, FR-004, SC-002 |
| **INV-2: Ausencia de foto sin espacios vacíos** | Frontend | Renderizado condicional en JSX: si `!photoUrl`, se omite el nodo contenedor superior de imagen | `DishDetailModal.test.tsx` (testRenderWithoutPhotoOmitsPlaceholder) | AC-1.2, EC-001, FR-003, SC-003 |
| **INV-3: Degradación elegante en red lenta** | Frontend | Renderizado inmediato de textos y renderizado condicional de skeleton sustituido por imagen en evento `onLoad` | `DishDetailModal.test.tsx` (testSlowNetworkImageDegradation) | AC-1.4, EC-002, FR-005, SC-004 |
| **INV-4: Adaptación estructural de textos largos** | Frontend | Contenedor con `max-h-[85vh]` y clases CSS Tailwind `overflow-y-auto break-words` | `DishDetailModal.test.tsx` (testLongDescriptionInternalScroll) | AC-1.5, EC-003, FR-006, SC-005 |
| **INV-5: Invalidación reactiva de disponibilidad** | Frontend / Backend / Sincronización | Hook `useRealtimeAvailability` (polling 2.500 ms con ETags); panel conmuta a "Plato no disponible" en $\le 3$ s sin cerrarse | `DishDetailModal.test.tsx` (testRealtimeAvailabilityInvalidation), `DishAvailabilityEngineTest` | AC-1.6, EC-004, EC-005, FR-007, SC-010, C-5 |
| **INV-6: Neutralidad de alérgenos e ingredientes** | Frontend / Backend | DTO `DishCatalogItemDto` expone `allergens: List<String>`; renderizado neutro e idéntico para todos los usuarios | `DishDetailModal.test.tsx` (testNeutralAllergensDisplay), `DinerCatalogServiceTest` | AC-2.1, EC-008, FR-008, C-4 |
| **INV-7: Reflejo de modificaciones de receta** | Backend / Frontend | `DinerCatalogService` resuelve dinámicamente ingredientes desde `dish_recipe_ingredients` en cada consulta | `DinerCatalogServiceTest` (testRecipeUpdateReflectedOnNextLoad) | AC-2.2, EC-006, FR-009 |
| **INV-8: Ausencia de información de ingredientes** | Frontend / Backend | Si lista de ingredientes está vacía, se expone leyenda explícita `"Lista de componentes no disponible"` | `DishDetailModal.test.tsx` (testMissingIngredientsExplicitNotice) | AC-2.3, EC-007, FR-010, SC-006 |
| **INV-9: Transparencia fiscal del precio exhibido** | Backend / Frontend | Lectura exclusiva de `retail_price` (PVP fijado por administración con impuestos); reutilización en total de carrito | `DishCard.test.tsx`, `CartDrawer.test.tsx` | AC-3.1, FR-011, SC-007 |
| **INV-10: Formateo monetario regional** | Frontend | `currencyUtils.ts` aplicando `toLocaleString('es-AR')` con símbolo `$ `, separador `.` y decimales `,00` | `currencyUtils.test.ts` (testRegionalCurrencyFormatting) | AC-3.2, FR-012, SC-007 |
| **INV-11: Actualización de precio administrativo** | Backend / Frontend | Consulta de catálogo y reapertura de panel recuperan nuevo `retail_price` sin invalidar sesión JWT | `CatalogPage.test.tsx` (testAdminPriceUpdateReflectedWithoutRelogin) | AC-3.3, EC-009, FR-013, SC-008 |
| **INV-12: Preservación de precio en platos agotados** | Frontend | Tarjeta y modal muestran precio final formateado pero aplican `disabled` a botones de incremento y compra | `DishCard.test.tsx` (testOutOfStockShowsPriceDisabledActions) | AC-3.4, EC-010, FR-014, SC-009 |
| **INV-13: Subtotal dinámico y estimación libre** | Frontend / Interacción con Carrito | Selector $N \ge 1$ sin tope artificial en modal; validación de stock al presionar "Agregar al Carrito" | `DishDetailModal.test.tsx` (testProvisionalSubtotalCalculationAndCartValidation) | AC-3.5, EC-011, FR-015, SC-011, C-2 |
| **INV-14: Álgebra de combinación de filtros y texto** | Frontend | Evaluación matemática $(C_{\text{sel}} \text{ OR}) \land [P_{\min}, P_{\max}] \land \text{matchesDishQuery}(T)$ | `ManualFiltersSidebar.test.tsx` (testFilterAlgebraConjunction) | AC-4.1, AC-4.2, AC-4.3, EC-012 a EC-016, FR-016 a FR-018, SC-012, C-1 |
| **INV-15: Manejo explícito de combinaciones sin resultados** | Frontend | Condicional `filteredDishes.length === 0`: renderiza `EmptyFilterResultsNotice` con botón limpiar filtros | `CatalogPage.test.tsx` (testEmptyFilterStateAndClearAction) | AC-4.4, EC-017, FR-019, SC-013 |
| **INV-16: Reversibilidad, persistencia y limpieza unificada** | Frontend / Sesión | Limpieza resetea categorías, precios y texto simultáneamente; filtros persisten en `sessionStorage` por tenant | `CatalogPage.test.tsx` (testUnifiedFilterResetAndPersistence) | AC-4.5, EC-018, FR-020, FR-024, SC-013, C-1, C-3 |
| **INV-17: Visibilidad de agotados bajo filtros** | Frontend | El predicado de filtrado no descarta por `isAvailable = false`; los renderiza atenuados con badge "Agotado" | `CatalogPage.test.tsx` (testOutOfStockDishesVisibleUnderFilters) | AC-4.6, EC-019, EC-020, FR-021, FR-023, SC-014 |

---

## 8. Matriz de Trazabilidad Exhaustiva

### 8.1 Acceptance Scenarios (20/20)

| Acceptance Scenario | Historia | Decisión Técnica | Contrato / Diseño | Test Verificador |
|---|---|---|---|---|
| **US1: AC-1.1** (Apertura de panel con multimedia) | COM-5.1.1 | D-005 | `OP-011-01`, `DishDetailModal.tsx` | `DishDetailModal.test.tsx` (testOpenModalWithFullDetails) |
| **US1: AC-1.2** (Plato sin fotografía) | COM-5.1.1 | D-005 | `OP-011-01`, `DishDetailModal.tsx` | `DishDetailModal.test.tsx` (testRenderWithoutPhotoOmitsPlaceholder) |
| **US1: AC-1.3** (Cierre y preservación exacta de scroll) | COM-5.1.1 | D-005 | `OP-011-01`, `DishDetailModal.tsx` | `DishDetailModal.test.tsx` (testScrollRestorationOnClose) |
| **US1: AC-1.4** (Degradación elegante en red lenta) | COM-5.1.1 | D-005 | `OP-011-01`, `DishDetailModal.tsx` | `DishDetailModal.test.tsx` (testSlowNetworkImageDegradation) |
| **US1: AC-1.5** (Adaptación de textos largos) | COM-5.1.1 | D-005 | `OP-011-01`, `DishDetailModal.tsx` | `DishDetailModal.test.tsx` (testLongDescriptionInternalScroll) |
| **US1: AC-1.6** (Invalidación reactiva stock/admin $\le 3$s) | COM-5.1.1 | D-001 | `OP-011-02`, `DishDetailModal.tsx` | `DishDetailModal.test.tsx` (testRealtimeAvailabilityInvalidation) |
| **US2: AC-3.1** (Precio final destacado con impuestos) | COM-5.1.3 | D-006 | `OP-011-01`, `DishDetailModal.tsx` | `DishDetailModal.test.tsx` (testProminentFinalPriceDisplay) |
| **US2: AC-3.2** (Formateo monetario regional) | COM-5.1.3 | D-006 | `OP-011-01`, `currencyUtils.ts` | `currencyUtils.test.ts` (testRegionalCurrencyFormatting) |
| **US2: AC-3.3** (Reflejo de cambio de precio sin re-login) | COM-5.1.3 | D-006 | `OP-011-03`, `CatalogPage.tsx` | `CatalogPage.test.tsx` (testAdminPriceUpdateReflectedWithoutRelogin) |
| **US2: AC-3.4** (Plato sin stock con precio referencial) | COM-5.1.3 | D-006 | `OP-011-01`, `DishCard.tsx` | `DishCard.test.tsx` (testOutOfStockShowsPriceDisabledActions) |
| **US2: AC-3.5** (Subtotal provisorio dinámico) | COM-5.1.3 | D-007 | `OP-011-04`, `DishDetailModal.tsx` | `DishDetailModal.test.tsx` (testProvisionalSubtotalCalculationAndCartValidation) |
| **US3: AC-2.1** (Desglose de ingredientes y alérgenos) | COM-5.1.2 | D-008 | `OP-011-01`, `DishDetailModal.tsx` | `DishDetailModal.test.tsx` (testIngredientsAndAllergensBreakdown) |
| **US3: AC-2.2** (Reflejo dinámico de receta de cocina) | COM-5.1.2 | D-008 | `OP-011-01`, `DinerCatalogService.java`| `DinerCatalogServiceTest` (testRecipeUpdateReflectedOnNextLoad) |
| **US3: AC-2.3** (Plato sin información de ingredientes) | COM-5.1.2 | D-008 | `OP-011-01`, `DishDetailModal.tsx` | `DishDetailModal.test.tsx` (testMissingIngredientsExplicitNotice) |
| **US4: AC-4.1** (Filtro por categorías semántica OR) | COM-4.2.2 | D-002 | `OP-011-05`, `ManualFiltersSidebar.tsx`| `ManualFiltersSidebar.test.tsx` (testCategoryDisjunctionOr) |
| **US4: AC-4.2** (Filtro por rango de precio cerrado) | COM-4.2.2 | D-002 | `OP-011-05`, `ManualFiltersSidebar.tsx`| `ManualFiltersSidebar.test.tsx` (testPriceRangeClosedInterval) |
| **US4: AC-4.3** (Composición conjuntiva AND dimensiones) | COM-4.2.2 | D-002 | `OP-011-05`, `CatalogPage.tsx` | `CatalogPage.test.tsx` (testConjunctiveAndComposition) |
| **US4: AC-4.4** (Aviso sin resultados y botón limpiar) | COM-4.2.2 | D-003 | `OP-011-05`, `EmptyFilterResultsNotice.tsx`| `CatalogPage.test.tsx` (testEmptyFilterStateAndClearAction) |
| **US4: AC-4.5** (Limpieza unificada de filtros y búsqueda) | COM-4.2.2 | D-003 | `OP-011-06`, `CatalogPage.tsx` | `CatalogPage.test.tsx` (testUnifiedFilterResetAndPersistence) |
| **US4: AC-4.6** (Agotados visibles bajo categorías) | COM-4.2.2 | D-002 | `OP-011-05`, `CatalogPage.tsx` | `CatalogPage.test.tsx` (testOutOfStockDishesVisibleUnderFilters) |

### 8.2 Edge Cases (22/22)

| Edge Case | Descripción | Decisión Técnica | Test Verificador |
|---|---|---|---|
| **EC-001** | Sin foto en plato → sin recuadro vacío ni ícono roto | D-005 | `DishDetailModal.test.tsx` (testRenderWithoutPhotoOmitsPlaceholder) |
| **EC-002** | Red lenta → texto inmediato + skeleton hasta carga de foto | D-005 | `DishDetailModal.test.tsx` (testSlowNetworkImageDegradation) |
| **EC-003** | Texto/descripción excesivamente largos → scroll interno | D-005 | `DishDetailModal.test.tsx` (testLongDescriptionInternalScroll) |
| **EC-004** | Agotamiento o baja administrativa en modal abierto $\le 3$s | D-001 | `DishDetailModal.test.tsx` (testRealtimeAvailabilityInvalidation) |
| **EC-005** | Reabastecimiento de insumo con modal abierto → rehabilitación | D-001 | `DishDetailModal.test.tsx` (testRestockReenablesActions) |
| **EC-006** | Reemplazo de ingredientes en cocina → reflejo en reapertura | D-008 | `DinerCatalogServiceTest` (testRecipeUpdateReflectedOnNextLoad) |
| **EC-007** | Plato sin receta → leyenda "Lista de componentes no disponible"| D-008 | `DishDetailModal.test.tsx` (testMissingIngredientsExplicitNotice) |
| **EC-008** | Plato con ingredientes pero sin alérgenos → leyenda informativa| D-008 | `DishDetailModal.test.tsx` (testIngredientsWithoutAllergensNotice) |
| **EC-009** | Modificación de precio administrativo → reflejo sin re-login | D-006 | `CatalogPage.test.tsx` (testAdminPriceUpdateReflectedWithoutRelogin) |
| **EC-010** | Apertura de plato sin stock → precio referencial + compra off | D-006 | `DishDetailModal.test.tsx` (testOutOfStockModalPriceVisibleDisabled) |
| **EC-011** | Selector N libre; validación stock en submit; reset al cerrar | D-007 | `DishDetailModal.test.tsx` (testProvisionalSubtotalCalculationAndCartValidation) |
| **EC-012** | Selección múltiple de categorías → unión lógica OR | D-002 | `ManualFiltersSidebar.test.tsx` (testCategoryDisjunctionOr) |
| **EC-013** | Filtro categorías + rango + texto búsqueda → intersección AND | D-002 | `CatalogPage.test.tsx` (testConjunctiveAndComposition) |
| **EC-014** | Rango $P_{\min} > P_{\max}$ → mensaje advertencia y bloqueo | D-002 | `ManualFiltersSidebar.test.tsx` (testPriceRangeMinGreaterThanMaxValidation) |
| **EC-015** | Rango $P_{\min} = P_{\max}$ → igualdad estricta | D-002 | `ManualFiltersSidebar.test.tsx` (testPriceRangeStrictEquality) |
| **EC-016** | Precio en extremo exacto del rango → semántica inclusiva | D-002 | `ManualFiltersSidebar.test.tsx` (testPriceRangeBoundaryInclusiveness) |
| **EC-017** | Filtros sin coincidencias → aviso explicativo + botón limpiar | D-003 | `CatalogPage.test.tsx` (testEmptyFilterStateAndClearAction) |
| **EC-018** | Limpieza simultánea de categorías, precio y texto de búsqueda | D-003 | `CatalogPage.test.tsx` (testUnifiedFilterResetAndPersistence) |
| **EC-019** | Platos agotados en categoría filtrada permanecen atenuados | D-002 | `CatalogPage.test.tsx` (testOutOfStockDishesVisibleUnderFilters) |
| **EC-020** | Variación de stock con filtros activos → actualización en grilla | D-001 | `CatalogPage.test.tsx` (testStockVariationPreservesFilters) |
| **EC-021** | Cierre por clic en fondo exterior → retorno y scroll intacto | D-005 | `DishDetailModal.test.tsx` (testBackdropClickClosesAndPreservesScroll) |
| **EC-022** | Resize de ventana o rotación con modal abierto → estabilidad | D-005 | `DishDetailModal.test.tsx` (testResizeResponsiveLayout) |

### 8.3 Functional Requirements (24/24)

| Requerimiento Funcional | Decisión Técnica | Artefacto / Diseño | Test Verificador |
|---|---|---|---|
| **FR-001** (Panel superpuesto sin salir de catálogo) | D-005 | `DishDetailModal.tsx` | `DishDetailModal.test.tsx` |
| **FR-002** (Fotografía, nombre y descripción detallada) | D-005 | `DishDetailModal.tsx` | `DishDetailModal.test.tsx` |
| **FR-003** (Sin foto omite área y no muestra rotos) | D-005 | `DishDetailModal.tsx` | `DishDetailModal.test.tsx` |
| **FR-004** (Cierre por botón o clic externo preserva scroll) | D-005 | `DishDetailModal.tsx` | `DishDetailModal.test.tsx` |
| **FR-005** (Degradación elegante en red lenta) | D-005 | `DishDetailModal.tsx` | `DishDetailModal.test.tsx` |
| **FR-006** (Scroll vertical interno para textos largos) | D-005 | `DishDetailModal.tsx` | `DishDetailModal.test.tsx` |
| **FR-007** (Invalidación reactiva $\le 3$s por stock/baja) | D-001 | `DishDetailModal.tsx`, `useRealtimeAvailability` | `DishDetailModal.test.tsx` |
| **FR-008** (Ingredientes y alérgenos neutros idénticos) | D-008 | `DishDetailModal.tsx`, `DishCatalogItemDto` | `DishDetailModal.test.tsx` |
| **FR-009** (Reflejo dinámico de cambios de receta cocina) | D-008 | `DinerCatalogService.java` | `DinerCatalogServiceTest` |
| **FR-010** (Mensaje explícito componentes no disponibles) | D-008 | `DishDetailModal.tsx` | `DishDetailModal.test.tsx` |
| **FR-011** (Precio final unitario destacado con impuestos) | D-006 | `DishDetailModal.tsx` | `DishDetailModal.test.tsx` |
| **FR-012** (Formateo monetario regional símbolo/miles/decimal) | D-006 | `currencyUtils.ts` | `currencyUtils.test.ts` |
| **FR-013** (Reflejo de precio modificado por admin sin re-login) | D-006 | `CatalogPage.tsx` | `CatalogPage.test.tsx` |
| **FR-014** (Plato sin stock mantiene precio y desactiva compra) | D-006 | `DishDetailModal.tsx`, `DishCard.tsx` | `DishCard.test.tsx` |
| **FR-015** (Selector N libre; cálculo subtotal; validación stock) | D-007 | `DishDetailModal.tsx`, `CartContext.tsx` | `DishDetailModal.test.tsx` |
| **FR-016** (Filtro categorías selección múltiple OR) | D-002 | `ManualFiltersSidebar.tsx` | `ManualFiltersSidebar.test.tsx`|
| **FR-017** (Filtro rango precios intervalo cerrado inclusivo) | D-002 | `ManualFiltersSidebar.tsx` | `ManualFiltersSidebar.test.tsx`|
| **FR-018** (Intersección AND categorías + rango + búsqueda texto) | D-002 | `CatalogPage.tsx` | `CatalogPage.test.tsx` |
| **FR-019** (Aviso sin resultados y botón para limpiar filtros) | D-003 | `EmptyFilterResultsNotice.tsx` | `CatalogPage.test.tsx` |
| **FR-020** (Limpieza unificada simultánea de todas dimensiones) | D-003 | `CatalogPage.tsx` | `CatalogPage.test.tsx` |
| **FR-021** (Agotados permanecen visibles atenuados bajo filtros) | D-002 | `CatalogPage.tsx` | `CatalogPage.test.tsx` |
| **FR-022** (Validación preventiva ante $P_{\min} > P_{\max}$) | D-002 | `ManualFiltersSidebar.tsx` | `ManualFiltersSidebar.test.tsx`|
| **FR-023** (Variaciones de stock reactivas en grilla filtrada) | D-001 | `CatalogPage.tsx` | `CatalogPage.test.tsx` |
| **FR-024** (Persistencia de filtros en sesión local por tenant) | D-004 | `CatalogFilterContext.tsx` | `CatalogPage.test.tsx` |

### 8.4 Success Criteria (14/14)

| Criterio de Éxito | Métrica Objetivo | Mecanismo de Verificación |
|---|---|---|
| **SC-001** | 100% de clics en tarjeta abren panel superpuesto | Prueba RTL: simulación de clic en tarjeta verifica renderizado de modal |
| **SC-002** | 100% de cierres de panel preservan coordenada de scroll | Prueba RTL: mock de `window.scrollY` verifica llamada a `scrollTo` con coordenada exacta |
| **SC-003** | 100% de platos sin foto omiten placeholder roto | Prueba RTL: verificación de no presencia de nodo `img` ni `placeholder` |
| **SC-004** | 100% de lecturas inmediatas de texto en red lenta | Prueba RTL: simulación de imagen no completada verifica texto visible |
| **SC-005** | 100% de textos extensos contenidos con scroll interno | Prueba RTL: inspección de clases `max-h` y `overflow-y-auto` en contenedor |
| **SC-006** | 100% de platos sin receta muestran leyenda explícita | Prueba RTL: plato sin ingredientes muestra "Lista de componentes no disponible" |
| **SC-007** | 100% de precios aplican formato regional vigente | Prueba unitaria: validación de símbolo `$ `, separador `.` y decimales `,00` |
| **SC-008** | 100% de cambios de precio reflejados sin re-login | Prueba de integración: recarga de datos actualiza el precio en pantalla |
| **SC-009** | 100% de platos agotados muestran precio referencial | Prueba RTL: verificación de precio legible y atributos `disabled` en botones |
| **SC-010** | 100% de variaciones de stock reflejadas en $\le 3$ s | Prueba con temporizadores: cambio en disponibilidad actualiza modal en $\le 3$ s |
| **SC-011** | Subtotal provisorio exacto ($P \times N$) en 100% de cambios | Prueba unitaria: variación de selector $N=1,2,5$ verifica subtotal exacto en centavos |
| **SC-012** | 100% de combinaciones respetan álgebra OR y AND | Prueba unitaria: verificación formal de predicados sobre catálogo |
| **SC-013** | 100% de estados vacíos ofrecen botón limpiar unificado | Prueba RTL: combinación restrictiva muestra aviso y limpiar restaura catálogo |
| **SC-014** | 100% de platos agotados permanecen visibles bajo filtros | Prueba RTL: categoría con platos sin stock renderiza tarjetas con badge "Agotado" |

### 8.5 Clarifications Formales (5/5)

| Clarification | Resumen del Acuerdo | Decisión Técnica Asociada |
|---|---|---|
| **C-1: AND Filtros + Búsqueda y Limpieza Unificada** | Intersección AND entre categorías, precio y texto de búsqueda; "Limpiar filtros" vacía simultáneamente las tres dimensiones. | D-002, D-003 |
| **C-2: Selector sin Tope y Validación al Agregar** | Selector $N \ge 1$ libre para estimación; validación estricta de inventario en "Agregar al Carrito" descontando lo ya en carrito. | D-007 |
| **C-3: Persistencia en Sesión por Restaurante** | Filtros se conservan en sesión local mientras se navegue en el mismo local (perfil/carrito/catálogo); reset al limpiar o cambiar local. | D-004 |
| **C-4: Alérgenos Neutros sin Perfil Dietario** | Etiquetas de alérgenos estándar y uniformes para todos; sin resaltes ni alertas preventivas por perfil en la vista informativa. | D-008 |
| **C-5: Invalidación en Panel Abierto en $\le 3$s** | Panel pasa a "Plato no disponible" y deshabilita compra en $\le 3$ s sin cerrarse; al cerrar, el catálogo se actualiza. | D-001 |

---

## 9. Phasing de Implementación

Las fases de implementación se ordenan por dependencia técnica. Cada fase cuenta con un criterio de finalización verificable (*Definition of Done*):

```
Fase 1: Utilidades y Extensión de Dominio (Formato Regional, DTOs y Contratos)
  ↓
Fase 2: Panel Superpuesto Base (Apertura, Cierre, Preservación de Scroll y Layout)
  ↓
Fase 3: Contenido Nutricional y Alérgenos Neutros
  ↓
Fase 4: Precio Final, Selector Libre y Validación de Inventario al Agregar al Carrito
  ↓
Fase 5: Barra Lateral de Filtros Manuales, Álgebra de Filtrado y Persistencia
  ↓
Fase 6: Integración con Real-Time, Limpieza Unificada y Validación End-to-End
```

- **Fase 1 — Utilidades y Extensión de Dominio**:
  - Implementar `currencyUtils.ts` (formato regional `$ 12.500,00` y aritmética de centavos).
  - Extender `DishCatalogItemDto.java` y `types/dinerCatalog.ts` con el campo `allergens: List<String>`.
  - Actualizar `DinerCatalogService.java` para poblar las etiquetas neutras de alérgenos desde `dish_dietary_restrictions`.
  - *Criterio de Done*: Tests unitarios de formateo regional y tests MockMvc de DTO de catálogo pasando al 100%.
- **Fase 2 — Panel Superpuesto Base**:
  - Refactorizar `DishDetailModal.tsx` para captura y restauración de `window.scrollY`.
  - Implementar lógica condicional de fotografía: si no posee foto, omite completamente el contenedor de imagen sin dejar espacios vacíos.
  - Implementar degradación elegante de carga de imagen con skeleton animado.
  - Implementar contención de textos largos con scroll interno (`max-h-[85vh]`, `overflow-y-auto`).
  - *Criterio de Done*: Tests de `DishDetailModal.test.tsx` certificando apertura, cierre por overlay/escape, restauración de scroll y adaptación visual.
- **Fase 3 — Contenido Nutricional y Alérgenos Neutros**:
  - Reemplazar alertas preventivas personalizadas en `DishDetailModal.tsx` por el componente de etiquetas neutras de alérgenos (`AllergenNeutralPill`).
  - Implementar mensaje explícito `"Lista de componentes no disponible"` ante platos sin receta.
  - Implementar leyenda informativa ante platos con ingredientes pero sin alérgenos.
  - *Criterio de Done*: Tests verificando presentación objetiva uniforme de alérgenos y manejo de casos sin ingredientes.
- **Fase 4 — Precios, Selector Libre y Validación al Agregar al Carrito**:
  - Renderizar precio final destacado con impuestos junto al botón de compra.
  - Implementar selector numérico previo $N \ge 1$ sin tope artificial con recálculo dinámico de subtotal provisorio.
  - Extender `addItem` en `CartContext.tsx` para validar $N + q_{\text{en\_carrito}} \le \text{stockDisponible}$, bloqueando adición ante insuficiencia de existencias.
  - Implementar descarte de estimación al cerrar el modal sin agregar.
  - *Criterio de Done*: Tests de cálculo de subtotal y validación de stock al agregar al carrito.
- **Fase 5 — Barra Lateral de Filtros Manuales, Álgebra y Persistencia**:
  - Crear componente `ManualFiltersSidebar.tsx` con checkboxes de categorías canónicas y campos de rango de precios ($P_{\min}, P_{\max}$).
  - Implementar validación preventiva ante $P_{\min} > P_{\max}$ con bloqueo de aplicación y mensaje inline.
  - Extender `CatalogFilterContext.tsx` con almacenamiento en `sessionStorage` particionado por `tenantId`.
  - Integrar predicado algebraico en `CatalogPage.tsx`: categorías (OR), rango (AND), búsqueda por texto (AND).
  - Asegurar visibilidad no excluyente de platos agotados (mostrados atenuados y deshabilitados).
  - *Criterio de Done*: Tests de filtros manuales cubriendo combinaciones OR, rango cerrado, y persistencia tras navegación a perfil/carrito.
- **Fase 6 — Integración con Real-Time, Limpieza Unificada y Validación End-to-End**:
  - Integrar hook `useRealtimeAvailability` en `DishDetailModal`: ante stock 0 o baja administrativa, actualizar a "Plato no disponible" en $\le 3$ s sin cerrar el modal; rehabilitar ante reabastecimiento.
  - Crear componente `EmptyFilterResultsNotice.tsx` para combinaciones sin resultados con botón "Limpiar filtros".
  - Implementar acción de limpieza unificada que restablece simultáneamente categorías, rango de precios y término de búsqueda de texto.
  - Ejecutar suite completa de validación end-to-end de `quickstart.md`.
  - *Criterio de Done*: 100% de escenarios ejecutables de `quickstart.md` validados satisfactoriamente.

---

## 10. Estrategia de Testing

Siguiendo la convención del repositorio (`easyeat/backend` y `easyeat/frontend`) y el Principio III de la Constitución:

1. **Backend Unit & Integration Tests (JUnit 5 + MockMvc + AssertJ)**:
   - `DinerCatalogServiceTest`: verifica que `getCatalog` devuelva `allergens` neutrales, resuelva ingredientes de receta, lea `retailPrice` con impuestos, y maneje platos sin receta.
   - `DishAvailabilityEngineTest`: verifica cálculo de disponibilidad ante variaciones de stock, insumos inactivos, stocks negativos y generación determinista de hash SHA-256 ETag.
   - `DinerCatalogControllerTest`: verifica endpoints `GET /api/v1/establishments/{tenantId}/catalog` y `GET /api/v1/establishments/{tenantId}/catalog/availability` con validación de RBAC (`ROLE_COMENSAL`), respuestas 200, 304 Not Modified y cabeceras ETag.
2. **Frontend Unit & Component Tests (Vitest + React Testing Library + jsdom)**:
   - `currencyUtils.test.ts`: pure unit tests para formateo regional (`$ 12.500,00`), aritmética de centavos (`toCents`, `fromCents`) y sumas sin deriva de punto flotante.
   - `DishDetailModal.test.tsx`:
     - Apertura y cierre por botón y clic en fondo exterior con verificación de preservación de scroll (`window.scrollTo`).
     - Renderizado sin foto omite completamente el contenedor visual.
     - Degradación en red lenta (texto inmediato, skeleton temporal).
     - Textos largos contenidos con scroll vertical interno.
     - Selector numérico previo $N \ge 1$ sin tope artificial y recálculo de subtotal provisorio en tiempo real.
     - Validación de inventario al presionar "Agregar al Carrito" descontando existencias en carrito.
     - Invalidación reactiva en $\le 3$ s ante stock 0 o baja sin cerrar el modal.
     - Rehabilitación automática ante reabastecimiento.
     - Despliegue de alérgenos neutros y mensaje explícito ante falta de receta.
   - `DishCard.test.tsx`: visualización de precio final con impuestos, estado atenuado con badge "Agotado" en platos sin stock.
   - `ManualFiltersSidebar.test.tsx`:
     - Selección múltiple de categorías aplica semántica OR.
     - Rango de precios aplica semántica cerrada inclusiva en extremos.
     - Validación ante $P_{\min} > P_{\max}$ bloquea aplicación y muestra advertencia.
     - Igualdad estricta ante $P_{\min} = P_{\max}$.
   - `CatalogPage.test.tsx`:
     - Intersección conjuntiva (AND) entre categorías, rango de precios y búsqueda por texto.
     - Limpieza unificada restablece simultáneamente categorías, rango y texto de búsqueda.
     - Persistencia de filtros en `sessionStorage` tras navegar a perfil/carrito y volver al mismo local.
     - Restablecimiento de filtros al conmutar de establecimiento.
     - Platos sin stock visibles bajo filtros activos.
     - Estado sin resultados despliega aviso y botón limpiar.
3. **End-to-End Validation (Quickstart)**:
   - Ejecución guiada de los 7 escenarios documentados en `quickstart.md`.

---

## 11. Manejo de Errores

Todos los errores operativos y de validación se gestionan conforme a las convenciones establecidas en el proyecto:
- **Backend (RFC 7807 `ProblemDetail`)**:
  - Acceso sin rol `ROLE_COMENSAL` → HTTP 401 Unauthorized (`GlobalExceptionHandler`).
  - Establecimiento inexistente → HTTP 404 Not Found (`EstablishmentNotFoundException`).
  - Establecimiento inactivo/pausado → HTTP 403 Forbidden (`EstablishmentInactiveException`).
- **Frontend (Validaciones de UI y Mensajes Estructurados)**:
  - $P_{\min} > P_{\max}$ → Mensaje de advertencia inline en la barra lateral: `"El precio mínimo no puede superar al precio máximo"`, deshabilitando el botón de aplicar rango hasta corregir valores.
  - Cantidad seleccionada excede stock disponible → Notificación en el modal: `"La cantidad seleccionada supera la disponibilidad en cocina. Disponibles: X"`, bloqueando la incorporación al carrito.
  - Plato sin ingredientes registrados → Leyenda visible: `"Lista de componentes no disponible"` (INV-8).
  - Combinación de filtros sin coincidencias → Contenedor destacado: `"No se encontraron platos que coincidan con los filtros aplicados"` junto al botón `"Limpiar filtros"` (INV-15).
  - Pérdida de conectividad con cocina → Banner amarillo de advertencia: `"Sin conexión con la cocina - reintentando..."` (heredado de spec 008 tras 3 fallos consecutivos).

---

## 12. Assumptions (Supuestos)

Se preservan íntegros los supuestos declarados en `spec.md`, cada uno marcado como `[SUPUESTO]`:
- `[SUPUESTO]` A-001: Se asume la existencia operativa previa del catálogo de platos, tarjetas de producto, panel de administración, carrito de compras de sesión, sistema de control de inventario de cocina, recetas, categorías y configuración regional de moneda, según lo establecido en las especificaciones 001 a 010.
- `[SUPUESTO]` A-002: El panel superpuesto de detalle de plato funciona como una extensión visual sobre el catálogo existente, preservando el estado de la vista de navegación subyacente.
- `[SUPUESTO]` A-003: El selector de cantidad de unidades del panel de detalle reutiliza el patrón conceptual y de interacción provisto por el módulo de carrito de compras (especificación 009).
- `[SUPUESTO]` A-004: Los precios registrados en el sistema ya contemplan la inclusión de impuestos en su valor de venta al público (especificación 007); la presente iteración se enfoca en su visualización prominente, formateo regional y cálculo del subtotal provisorio.
- `[SUPUESTO]` A-005: La configuración regional establece el símbolo monetario, el carácter separador de miles y la cantidad de decimales válidos para el establecimiento (`es-AR`).
- `[SUPUESTO]` A-006: La sincronización en tiempo real ante cambios de stock, recetas y precios refiere a la actualización observable en la interfaz del comensal sin exigir recarga manual de página, respaldada por la infraestructura preexistente y con una latencia máxima admisible de 3 segundos (en coherencia con la especificación 008).
- `[SUPUESTO]` A-007: En el filtro manual por rango de precios, la evaluación es un intervalo cerrado e inclusivo en ambos límites: $\text{precio} \in [P_{\min}, P_{\max}]$.
- `[SUPUESTO]` A-008: Si el comensal ingresa un precio mínimo estrictamente superior al máximo ($P_{\min} > P_{\max}$), el sistema muestra una advertencia de validación y bloquea la aplicación del filtro hasta que los valores sean consistentes.
- `[SUPUESTO]` A-009: La preservación de la coordenada de desplazamiento vertical en el catálogo al cerrar el panel superpuesto aplica estrictamente dentro del mismo estado de disposición visual y tamaño de ventana (layout).
- `[SUPUESTO]` A-010: Al cerrar el panel de detalle sin agregar el producto al pedido, el subtotal provisorio calculado se descarta y el selector de cantidad se reinicia en 1 unidad para la siguiente apertura.
- `[SUPUESTO]` A-011: Ante redimensionamiento de ventana o cambio de orientación con el panel abierto, el modal superpuesto adapta su tamaño responsive y la posición relativa de scroll se mantiene dentro del mismo layout visual.

---

## 13. Out of Scope (Fuera de Alcance)

Quedan formal y explícitamente fuera del alcance de la presente especificación e iteración las siguientes funcionalidades y comportamientos (preservados íntegros del spec):
- Edición de platos, recetas, categorías o precios desde el panel del comensal.
- Búsqueda por texto libre, autocompletado y búsqueda por voz (pertenecientes a otras historias y especificaciones).
- Filtros por valoración, popularidad, tiempo de preparación, distancia física o alérgenos excluyentes (excluir platos que contienen determinado alérgeno).
- Ordenamiento de resultados (por precio ascendente o descendente, por popularidad o alfabético).
- Recomendaciones personalizadas, platos similares o sección de sugerencias cruzadas ("también te puede gustar").
- Gestión de platos favoritos, historial de búsquedas y guardado de listas de comensales.
- Compartir fichas de platos en redes sociales o canales de mensajería externa.
- Carga de imágenes o archivos multimedia por parte del comensal.
- Reservas de mesa, pedidos programados diferidos y gestión logística de despacho por delivery.
- Carrito de compras integral, pasarela de pagos online, transacciones monetarias y flujo de checkout.
- Mecanismos de autenticación, registro de cuentas y asignación de permisos (cubiertos por las especificaciones 001, 002 y 003).
- Notificaciones push al dispositivo móvil, mensajes por correo electrónico o SMS.
- Soporte multi-idioma del catálogo gastronómico (más allá del idioma español neutro de la interfaz de usuario).
- Multi-moneda y conversión dinámica de tipos de cambio internacionales.
- Programas de fidelización comercial, acumulación de puntos, cupones de descuento y promociones temporales.
- Servicios de geolocalización, mapas interactivos y cálculo de distancias de entrega.
- Generación de reportes comerciales, analítica de clics y paneles de métricas para la administración.
- Modificación reactiva o vaciado del carrito de compras motivada por variaciones de stock o de precio posteriores a la incorporación del plato.

---

## 14. Riesgos y Mitigaciones

| Riesgo Técnico Identificado | Impacto | Probabilidad | Estrategia de Mitigación en el Plan |
|---|---|---|---|
| **Salto de scroll en dispositivos móviles al cerrar modal** | Medio | Media | Captura explícita de `window.scrollY` antes de abrir y restauración forzada con `window.scrollTo({ top: scrollY, behavior: 'instant' })` al desmontar. |
| **Colisión de filtros en `sessionStorage` entre locales** | Alto | Baja | Claves de almacenamiento particionadas estrictamente por `tenantId` (`easyeat_catalog_filters_{tenantId}`). |
| **Inconsistencia decimal en cálculo de subtotales** | Alto | Baja | Utilización estricta de punto fijo basado en centavos enteros (`toCents`, `fromCents`) en `currencyUtils.ts`. |
| **Pestaña inactiva acumulando peticiones de polling** | Medio | Media | El hook `useRealtimeAvailability` utiliza la Page Visibility API (`document.hidden`) para pausar el temporizador mientras la pestaña no tenga foco. |
| **Sobrevendencia concurrente de stock al agregar** | Alto | Media | Validación estricta en el botón "Agregar al Carrito" contra el stock de cocina disponible remanente descontando lo ya en carrito. |

---

## 15. Resumen Ejecutivo de Cierre

El plan técnico para la feature `011-dish-detail-manual-filters` ha sido completado y verificado en su totalidad. El stack detectado en el repositorio está constituido por Java 21 / Spring Boot 3.3.4 en el backend y TypeScript 5.6 / React 18.3.1 con Tailwind CSS en el frontend. Las decisiones arquitectónicas clave adoptadas reutilizan de forma estricta los mecanismos existentes del proyecto: canal de invalidación reactiva por polling condicional con ETags cada 2.500 ms (SLA $\le 3$ s), formateo regional centralizado (`es-AR`), álgebra conjuntiva (AND) de filtros manuales con la búsqueda por texto existente (COM-4.2.3), persistencia de filtros en `sessionStorage` aislada por establecimiento, degradación progresiva de imágenes sin recuadros vacíos, preservación milimétrica de scroll vertical, y selector numérico sin tope artificial con validación de inventario al agregar al carrito.

La cobertura respecto a la especificación aprobada es absoluta: **20/20 Criterios de Aceptación**, **22/22 Casos Borde**, **17/17 Invariantes de Negocio**, **24/24 Requerimientos Funcionales**, **14/14 Criterios de Éxito**, **11/11 Supuestos** y **5/5 Aclaraciones Formales**. No existen incógnitas técnicas pendientes (`[NEEDS CLARIFICATION]`: 0) y el Constitution Check de la Constitución SDD v2.0.0 arroja **PASS** unánime. Se confirma de manera explícita que **no se generaron tareas de implementación**, quedando la feature lista para la fase de descomposición de tareas (`/speckit-tasks`).
