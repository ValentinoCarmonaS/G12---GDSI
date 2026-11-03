# Implementation Plan: Umbral de Stock Crítico en Inventario y Pausa Manual de Platos por Chef

**Branch**: `013-critical-stock-chef-pause` | **Fecha**: 01/11/2026 | **Spec**: [spec.md](./spec.md)

**Input**: Especificación funcional aprobada de la iteración 013 (`specs/013-critical-stock-chef-pause/spec.md`).

**Artefactos complementarios generados**:
- [research.md](./research.md) — Fase 0: Decisiones arquitectónicas y resolución técnica de incógnitas.
- [data-model.md](./data-model.md) — Fase 1: Modelo conceptual, tres causas de indisponibilidad y migración Flyway V11.
- [contracts/behavioral-contracts.md](./contracts/behavioral-contracts.md) — Fase 1: 10 contratos de comportamiento operacional.
- [contracts/openapi.yaml](./contracts/openapi.yaml) — Fase 1: Especificación OpenAPI 3.0.3 de los endpoints expuestos.
- [quickstart.md](./quickstart.md) — Fase 1: Guía ejecutable de validación end-to-end con 7 escenarios prácticos.

---

## 1. Summary

La iteración 013 expande la plataforma gastronómica multi-tenant EasyEat integrando tres capacidades operativas críticas:
1. **ADM-4.1.3 (Definir umbral de stock crítico individual)**: Permite al Administrador asignar y modificar en el módulo de inventario un valor numérico entero individual ($\ge 0$) por ingrediente, estableciendo una cota de referencia para alertas de reposición. Chef no autorizado.
2. **ADM-4.1.4 (Visualizar alertas de reabastecimiento en estado crítico)**: Resalta visualmente en la tabla de inventario del Back-Office a todo insumo con $\text{cantidad\_disponible} < \text{umbral\_critico}$ (estricto). Incorpora un filtro rápido de criticidad en cabecera para aislar faltantes de inmediato. La alerta es exclusivamente visual (cero correos, SMS, push u órdenes automáticas). La condición crítica suspende su emisión si el insumo está descontinuado (`DISCONTINUED`) y no bloquea las ventas de platos con stock $> 0$.
3. **CHF-2.2.5 (Pausa manual de platos por el chef)**: Provee al Chef un control de alternancia (toggle) individual para ocultar inmediatamente platos del catálogo del comensal ante contingencias de cocina (plancha averiada, demoras), sin alterar stock, precio, receta ni alertas. El toggle es permanentemente operable en cocina (incluso ante falta de stock o desactivación administrativa). Los cambios se propagan colaborativamente entre todos los chefs del local y al catálogo en $\le 3\text{ s}$. En el carrito del comensal, un plato pausado se preserva exhibiendo la advertencia `"Pausado por cocina"` y bloqueando el checkout; en el modal de detalle abierto, se deshabilita la compra reactivamente en $\le 3\text{ s}$ sin cerrar el panel.

**Principio Arquitectónico Central (INV-10)**: El sistema desacopla de manera ortogonal y consultable por separado las tres fuentes de indisponibilidad (`sin_stock`, `pausa_chef`, `desactivacion_admin`), prohibiendo su colapso en un flag genérico. La solución reutiliza la infraestructura preexistente de polling condicional con HTTP ETags (`304 Not Modified`) cada $2500\text{ ms}$ (specs 008 y 011), asegurando coherencia plena sin introducir dependencias externas.

---

## 2. Technical Context

- **Language / Version**: Java 21 (LTS) en backend; TypeScript 5.6 / React 18.3.1 en frontend. *(Verificado en `easyeat/backend/pom.xml` y `easyeat/frontend/package.json`)*.
- **Primary Dependencies**:
  - *Backend*: Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`), Flyway Core 10.x, PostgreSQL Driver (`42.7.4`), JJWT (`0.12.5`), Lombok, Jackson Problem (`problem-spring-web` RFC 7807), Testcontainers (`1.20.1`).
  - *Frontend*: React 18.3.1, React Router v6 (`react-router-dom`), Tailwind CSS v4, Lucide React (íconos), Axios (`apiClient.ts`).
- **Storage & Relational Schema**: PostgreSQL 16.
  - Tabla `ingredients` extendida con columna `critical_stock_threshold INTEGER NULL` con constraint `CHECK (critical_stock_threshold IS NULL OR critical_stock_threshold >= 0)`.
  - Tabla `dishes` extendida con columna `paused_by_chef BOOLEAN NOT NULL DEFAULT FALSE`.
  - Migración secuencial Flyway: `V11__critical_stock_and_chef_pause.sql` (sucede a `V10__support_admin_dish_cancellation.sql`).
  - Sesión del comensal: `sessionStorage` (`'easyeat_cart'`).
- **Testing Frameworks**:
  - *Backend*: JUnit 5, Mockito, MockMvc, AssertJ, Testcontainers (PostgreSQL 16 real).
  - *Frontend*: Vitest 2, React Testing Library (RTL), jsdom, `@testing-library/user-event`.
- **Target Platform**: Servidores Linux / contenedores Docker para Spring Boot; navegadores web modernos estándar (Chrome, Firefox, Safari, Edge) para la SPA React.
- **Project Type**: Full-stack Monorepo Web Application (`easyeat/backend`, `easyeat/frontend`).
- **Performance Goals**:
  - Latencia de sincronización de pausa del chef al catálogo del comensal: $\le 3\text{ s}$ (`INV-15`, `SC-006`, `SC-007`).
  - Sincronización colaborativa entre múltiples chefs: $\le 3\text{ s}$ (`INV-18`, `FR-022`, `EC-025`).
  - Respuesta de evaluación condicional ETag (`304 Not Modified`): $\le 50\text{ ms}$.
  - Actualización reactiva del modal de detalle ante pausa: $\le 3\text{ s}$ (`FR-016`, `EC-017`).
- **Constraints**:
  - Aislamiento multi-tenant estricto mediante `tenant_id` en todas las consultas y mutaciones.
  - Exclusividad visual de alertas en Back-Office; prohibición de correos, SMS, push u órdenes automáticas (`INV-5`, `FR-008`).
  - Prohibición de anulación o override mutuo entre Admin y Chef sobre sus respectivos mecanismos (`INV-10`, `Out of Scope`).
  - Validación numérica entera $\ge 0$ estricta para umbrales (`INV-2`, `FR-002`).
  - Reutilización de mecanismos preexistentes sin WebSockets, SSE ni librerías no declaradas.
- **Scale / Scope**: 3 historias de usuario P1, 15 acceptance scenarios, 25 edge cases, 19 invariantes, 23 requerimientos funcionales, 15 success criteria, 12 supuestos y 12 clarifications.

---

## 3. Constitution Check

*GATE: Evaluación rigurosa frente a la Constitución SDD versión 2.0.0. Pre-design: PASS. Post-design: PASS.*

| Principio | Estado Pre | Estado Post | Evidencia y Justificación Técnica |
|---|---|---|---|
| **I. The Specification Is the Source of Truth** | **PASS** | **PASS** | El plan técnico, data model y contratos derivan estrictamente de los 23 FRs, 19 INVs, 25 ECs y 15 SCs de `spec.md`. No se añade ni reinterpreta ningún requisito. |
| **II. Specification Before Implementation** | **PASS** | **PASS** | La especificación fue completada y auditada previamente (`checklists/critical-stock-and-pause-quality.md`). Artefactos de diseño (`research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `plan.md`) formalizados antes de cualquier código o tarea. |
| **III. Layered Verification Proportional to Risk** | **PASS** | **PASS** | Estrategia de pruebas en 4 capas: unitarias de dominio e invariantes (INV-1..INV-19), integración MockMvc + Testcontainers, pruebas de componentes RTL (modales, grillas, toggles) y validaciones end-to-end en `quickstart.md`. |
| **IV. Simple, Cohesive, and Justified Design** | **PASS** | **PASS** | Se reutiliza el canal de polling con ETags existente (`useRealtimeAvailability`) en lugar de añadir WebSockets o SSE. Cohesión alta en bounded contexts `inventory`, `dish` y `catalog`. Cero sobreingeniería. |
| **V. Bounded Scope** | **PASS** | **PASS** | Alcance rigurosamente acotado a las 3 historias aprobadas. Alertas externas, órdenes automáticas, umbrales globales, temporizadores de pausa y motivos obligatorios quedan excluidos en estricto cumplimiento de Out of Scope. |
| **VI. Controlled Technical Debt** | **PASS** | **PASS** | Se incorpora la migración limpia `V11__critical_stock_and_chef_pause.sql` siguiendo secuencialmente a `V10`, sin modificar tablas de manera destructiva ni alterar scripts históricos. |
| **VII. Traceability, Consistency, and Documentation** | **PASS** | **PASS** | Matriz de trazabilidad exhaustiva en Sección 12 vinculando el 100% de ACs (15/15), ECs (25/25), INVs (19/19), FRs (23/23), SCs (15/15) y Clarifications (12/12) a decisiones, contratos y pruebas. |
| **VIII. Evidence Over Assumption and Human Escalation** | **PASS** | **PASS** | Todas las decisiones técnicas se basan en evidencia verificable del repositorio (`Ingredient.java`, `Dish.java`, `DishAvailabilityEngine.java`, `useRealtimeAvailability.ts`) y aclaraciones ratificadas. Cero supuestos no documentados. |
| **IX. Engineering Over Convenience** | **PASS** | **PASS** | Separación ortogonal de las tres causas de indisponibilidad (INV-10) en lugar de un flag conveniente pero destructivo. Formato estructurado ProblemDetail RFC 7807 para errores. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | **PASS** | **PASS** | Entrega un vertical slice full-stack integrado: backend (migración Flyway `V11`, entidades JPA, repositorios, servicios, endpoints REST) y frontend (componentes React, páginas de inventario y cocina, modal de detalle, carrito y hooks). |

---

## 4. Complexity Tracking

> *El diseño adopta los patrones existentes más directos y simples del repositorio. Cero violaciones a los principios de la Constitución.*

| Violación | Por qué es necesaria | Alternativa más simple rechazada porque |
|---|---|---|
| Ninguna | N/A | N/A |

---

## 5. Project Structure & Source Code Layout

### 5.1 Documentación de la Feature

```text
specs/013-critical-stock-chef-pause/
├── spec.md                          # Especificación funcional aprobada
├── checklists/
│   ├── requirements.md              # Checklist de calidad general de requisitos
│   └── critical-stock-and-pause-quality.md # Checklist de auditoría temática
├── plan.md                          # Este plan técnico de implementación
├── research.md                      # Fase 0: investigación y decisiones técnicas
├── data-model.md                    # Fase 1: modelo conceptual y esquema relacional
├── quickstart.md                    # Fase 1: guía de validación ejecutable end-to-end
├── contracts/                       # Fase 1: contratos de interfaz y comportamiento
│   ├── openapi.yaml                 # Contrato OpenAPI 3.0.3 para endpoints y DTOs
│   └── behavioral-contracts.md      # Contratos de comportamiento detallados (10 operaciones)
└── tasks.md                         # Fase 2: desglose de tareas (generado por /speckit-tasks)
```

### 5.2 Estructura del Código Fuente en el Repositorio

```text
easyeat/
├── backend/
│   ├── src/main/resources/db/migration/
│   │   └── V11__critical_stock_and_chef_pause.sql          # Migración Flyway: critical_stock_threshold y paused_by_chef
│   └── src/main/java/com/easyeat/
│       ├── inventory/
│       │   ├── controller/AdminIngredientController.java   # Extendido: soporta criticalOnly=true
│       │   ├── dto/CreateIngredientRequest.java            # Extendido: criticalStockThreshold (Integer >= 0)
│       │   ├── dto/UpdateIngredientRequest.java            # Extendido: criticalStockThreshold (Integer >= 0)
│       │   ├── dto/AdminIngredientDetailResponse.java      # Extendido: criticalStockThreshold, isCritical
│       │   ├── entity/Ingredient.java                      # Extendido: criticalStockThreshold, isCritical()
│       │   ├── repository/IngredientRepository.java        # Extendido: consulta con filtro de criticidad
│       │   └── service/IngredientService.java              # Extendido: lógica de umbral y filtro rápido
│       ├── dish/
│       │   ├── controller/DishController.java              # Extendido: PATCH /{id}/chef-pause, GET /operational-status
│       │   ├── dto/ToggleChefPauseRequest.java             # Nuevo: payload { paused: boolean }
│       │   ├── dto/DishChefPauseResponse.java              # Nuevo: response { dishId, pausedByChef, updatedAt }
│       │   ├── dto/ChefDishesOperationalStatusResponse.java # Nuevo: response { items: [...] } con ETag
│       │   └── service/DishService.java                    # Extendido: toggleChefPause, getOperationalStatus
│       └── catalog/
│           ├── domain/Dish.java                            # Extendido: pausedByChef (Boolean)
│           ├── dto/DishAvailabilityDetailsResponse.java    # Nuevo: 3 causas independientes (INV-10)
│           ├── repository/DishRepository.java              # Extendido: consultas considerando pausedByChef
│           ├── service/DishAvailabilityEngine.java         # Extendido: cálculo de ETag considerando pausedByChef
│           └── service/DinerCatalogService.java            # Extendido: filtrado de platos pausados para comensal
└── frontend/
    └── src/
        ├── api/
        │   ├── inventoryApi.ts                             # Extendido: soporte de criticalOnly en listIngredients
        │   └── dishApi.ts                                  # Extendido: toggleChefPause, getOperationalStatus
        ├── components/
        │   ├── inventory/
        │   │   ├── IngredientCatalogTable.tsx              # Extendido: insignia visual "Stock crítico" y filtro rápido
        │   │   └── IngredientFormModal.tsx                 # Extendido: campo "Stock mínimo/crítico" con validación
        │   ├── chef/
        │   │   └── ChefDishPauseToggle.tsx                 # Nuevo: control de alternancia interactivo para chef
        │   ├── catalog/
        │   │   ├── DishCard.tsx                            # Oculta plato pausado según visibilidad
        │   │   └── DishDetailModal.tsx                     # Extendido: reflejo reactivo en <= 3 s sin cierre forzado
        │   └── cart/
        │       ├── CartDrawer.tsx                          # Extendido: aviso "Pausado por cocina", bloqueo checkout
        │       └── CartItemWithBadge.tsx                   # Extendido: inhabilitación de incremento en platos pausados
        ├── context/
        │   └── CartContext.tsx                             # Extendido: detección de platos pausados en sesión
        ├── hooks/
        │   ├── useRealtimeAvailability.ts                  # Reutilizado: actualiza catálogo y carrito en <= 3 s
        │   └── useChefDishesSync.ts                        # Nuevo: sincronización colaborativa entre chefs (<= 3 s)
        ├── pages/
        │   ├── InventoryManagementPage.tsx                 # Extendido: integración de filtro rápido
        │   └── ChefDishesPage.tsx                          # Extendido: columna de estado operativo y toggle de pausa
        └── types/
            ├── inventory.ts                                # Extendido: criticalStockThreshold, isCritical
            └── dish.ts                                     # Extendido: pausedByChef, ChefDishOperationalStatus
```

---

## 6. Architectural & Technical Decisions

### D-001 — Persistencia del Umbral Crítico Individual en `ingredients`
- **Decisión**: Añadir la columna `critical_stock_threshold INTEGER NULL` con restricción `CHECK (critical_stock_threshold IS NULL OR critical_stock_threshold >= 0)` en `ingredients`.
- **Fuente**: `spec.md §FR-001..FR-003`, `§INV-1`, `§INV-2`, `easyeat/backend/src/main/resources/db/migration/V4__create_ingredients_and_stock_audit.sql`.
- **Cubre**: US1 AC-1.1, AC-1.2, AC-1.3, AC-1.4, FR-001, FR-002, FR-003, INV-1, INV-2, SC-001, SC-002, EC-001, EC-002, EC-022, C-3, C-6.
- **Reutiliza**: Entidad y tabla `ingredients` creada en spec 004.
- **Riesgo**: Insumos legados con valor nulo.
- **Mitigación**: Tratamiento de `null` como insumo sin umbral configurado (`isCritical = false`), garantizando backward compatibility estricta (INV-7, EC-002).
- **Alternativa descartada**: Tabla separada 1:1 `ingredient_thresholds`. Rechazada por complejidad accidental para un atributo atómico del insumo.

### D-002 — Cálculo Dinámico de Criticidad y Alerta Exclusiva en Back-Office
- **Decisión**: Calcular el estado de criticidad en tiempo de lectura como `isCritical = (status == ACTIVE) && (threshold != null) && (currentStock < threshold)`.
- **Fuente**: `spec.md §FR-004..FR-008`, `§INV-3..INV-6`, `§SC-003..SC-005`, Clarifications C-1, C-2.
- **Cubre**: US2 AC-2.1..AC-2.5, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009, FR-010, INV-3, INV-4, INV-5, INV-6, SC-003, SC-004, SC-005, SC-011, SC-012, SC-013, EC-003..EC-010.
- **Reutiliza**: DTO `AdminIngredientDetailResponse` y vista `IngredientCatalogTable.tsx`.
- **Riesgo**: Confusión con insumos en el límite ($= \text{umbral}$).
- **Mitigación**: Regla matemática estricta: si $\text{stock} = \text{umbral}$, `isCritical = false` (INV-3, C-1).
- **Alternativa descartada**: Disparar emails o tareas automáticas de compra. Terminantemente descartado por INV-5 y Out of Scope.

### D-003 — Filtro Rápido en Cabecera de Inventario
- **Decisión**: Exponer `GET /api/v1/admin/ingredients?criticalOnly=true` y añadir un toggle de filtrado rápido en la barra de herramientas de `IngredientCatalogTable.tsx`.
- **Fuente**: `spec.md §FR-020`, `§SC-015`, Clarification C-9.
- **Cubre**: US2 AC-2.6, FR-020, SC-015, C-9.
- **Reutiliza**: Patrón de filtros de `IngredientCatalogTable.tsx` (que ya compone búsqueda por texto, categoría y estado).
- **Riesgo**: Colisión con el filtro de estado descontinuado.
- **Mitigación**: Los ingredientes descontinuados tienen `isCritical = false`; el filtro rápido opera exclusivamente sobre los activos por debajo de su umbral.
- **Alternativa descartada**: Vista separada para órdenes de compra. Rechazada por redundancia UI.

### D-004 — Modelo de Tres Causas de Indisponibilidad Separadas (INV-10)
- **Decisión**: Representar de forma ortogonal e independiente: (1) `sin_stock` (derivado de recetas e inventario), (2) `paused_by_chef` (columna booleana en `dishes`), (3) `is_active`/`status` (control administrativo).
- **Fuente**: `spec.md §INV-10`, `§INV-12`, `§INV-13`, `§FR-015`, `§SC-008`, `§SC-014`, Clarification C-4.
- **Cubre**: FR-015, INV-10, INV-12, INV-13, SC-008, SC-014, EC-011..EC-014, C-4.
- **Reutiliza**: Entidad `Dish.java` y `DishAvailabilityEngine.java`.
- **Riesgo**: Colapso accidental de flags en endpoints de catálogo.
- **Mitigación**: Endpoints de personal devuelven el desglose de las tres causas; el catálogo del comensal solo consume la disponibilidad resultante.
- **Alternativa descartada**: Colapsar en un único atributo `dishes.is_available`. Prohibido taxativamente por INV-10.

### D-005 — Operabilidad Preventiva Permanente del Toggle del Chef (INV-19)
- **Decisión**: El endpoint `PATCH /api/v1/dishes/{id}/chef-pause` acepta `{ paused: boolean }` sin validar si el plato tiene stock o está activo comercialmente.
- **Fuente**: `spec.md §FR-011`, `§FR-023`, `§INV-19`, Clarification C-12.
- **Cubre**: US3 AC-3.1, AC-3.2, AC-3.4, FR-011, FR-023, INV-19, EC-011, EC-012, C-12.
- **Reutiliza**: `DishController.java` y seguridad `@PreAuthorize("hasRole('CHEF')")`.
- **Riesgo**: Intentos de uso por parte de administradores.
- **Mitigación**: Control de autorización RBAC estricto que rechaza al Admin con HTTP 403 (INV-16, FR-018, SC-010, EC-019).
- **Alternativa descartada**: Bloquear el toggle si el plato está agotado. Descartada: el chef debe poder fijar pausas preventivas por fallas de equipamiento.

### D-006 — Sincronización en Tiempo Real ($\le 3$ s) para Catálogo y Cocina
- **Decisión**: Reutilizar el polling condicional HTTP basado en ETag cada $2500\text{ ms}$:
  - Comensales: vía `useRealtimeAvailability.ts` sobre `GET /api/v1/establishments/{tenantId}/catalog/availability`.
  - Chefs: vía nuevo hook `useChefDishesSync.ts` sobre `GET /api/v1/dishes/operational-status`.
- **Fuente**: `spec.md §INV-15`, `§INV-18`, `§FR-012`, `§FR-022`, `§SC-006`, `§SC-007`, Clarification C-11.
- **Cubre**: FR-012, FR-013, FR-022, INV-15, INV-18, SC-006, SC-007, EC-025, C-11.
- **Reutiliza**: Motor de hashing SHA-256 de `DishAvailabilityEngine.java` y arquitectura stateless de spec 008.
- **Riesgo**: Carga de consultas en el servidor.
- **Mitigación**: Consultas indexadas y respuesta liviana `304 Not Modified` sin payload ni serialización cuando el estado no cambia.
- **Alternativa descartada**: Implementar WebSockets. Rechazada por violación del principio KISS (Constitución §IV) y falta de soporte previo en el repo.

### D-007 — Comportamiento del Carrito de Compras ante Pausa (FR-019)
- **Decisión**: Adaptar `CartContext.tsx` y `CartDrawer.tsx` para detectar platos con `paused_by_chef = true`: mantener el ítem en la orden, mostrar advertencia `"Pausado por cocina"`, deshabilitar botón `+` y bloquear el botón de confirmación de pedido.
- **Fuente**: `spec.md §FR-019`, `§EC-023`, Clarification C-8.
- **Cubre**: FR-019, EC-023, C-8.
- **Reutiliza**: Mecanismo de advertencias y bloqueo de checkout de spec 009 (FR-014a).
- **Riesgo**: Pérdida de la orden si el usuario no comprende el bloqueo.
- **Mitigación**: Mensaje informativo claro indicando que retirando el plato pausado o esperando a cocina se desbloquea el avance.
- **Alternativa descartada**: Eliminar automáticamente el plato del carrito. Descartada por mala UX y violación de C-8.

### D-008 — Comportamiento del Modal de Detalle Abierto ante Pausa (EC-017)
- **Decisión**: Al sincronizar `useRealtimeAvailability`, si el plato abierto en `DishDetailModal.tsx` pasa a pausado, mostrar distintivo `"Pausado por cocina"` y deshabilitar controles de compra sin cerrar el modal. Al cerrar, el plato desaparece del catálogo.
- **Fuente**: `spec.md §FR-016`, `§EC-017`, `spec 011 Clarification #5`.
- **Cubre**: FR-016, EC-017, INV-15.
- **Reutiliza**: Patrón de actualización reactiva de disponibilidad en `DishDetailModal.tsx` de spec 011.
- **Riesgo**: Cierre intempestivo mientras el comensal lee ingredientes.
- **Mitigación**: El panel permanece visible en modo informativo; la exclusión de grilla ocurre recién al cerrar el modal voluntariamente.
- **Alternativa descartada**: Cerrar el modal forzadamente. Descartada por violación de spec 011 Clarification #5.

### D-009 — Suspensión de Alertas en Insumos Descontinuados
- **Decisión**: Excluir insumos con `status == DISCONTINUED` del cómputo de criticidad y del resaltado de alerta, conservando el valor histórico de `critical_stock_threshold` en base de datos.
- **Fuente**: `spec.md §FR-021`, `§INV-17`, `§EC-024`, Clarification C-10.
- **Cubre**: FR-021, INV-17, EC-024, C-10.
- **Reutiliza**: Ciclo de vida de insumos de spec 004 (`ACTIVE`, `DISCONTINUED`).
- **Riesgo**: Olvido de reactivación si el insumo vuelve a usarse.
- **Mitigación**: Al transicionar a `ACTIVE`, la evaluación se reanuda automáticamente de inmediato (INV-17).
- **Alternativa descartada**: Anular el campo a `null` al descontinuar. Descartada: destruiría el parámetro fijado por el administrador.

### D-010 — Ubicación Visual del Toggle del Chef Diferida a UI/UX (C-5)
- **Decisión**: El plan declara que el toggle se integra como control interactivo en la tabla de platos del Chef (`ChefDishesPage.tsx`), postergando estilos específicos y detalles de layout a la fase de maquetación, conforme a Clarification C-5.
- **Fuente**: `spec.md Clarification C-5`, `§Assumptions`.
- **Cubre**: Clarification C-5, C-12, FR-011.
- **Reutiliza**: Estilos de Tailwind CSS y diseño sobrio de `ChefDishesPage.tsx`.
- **Riesgo**: Prescribir maquetación rígida en spec.
- **Mitigación**: Adhesión estricta a C-5: el plan solo norma el comportamiento observable.
- **Alternativa descartada**: Diseñar vistas modales accesorias para pausar. Descartada: un toggle inline en la tabla es lo más ágil para cocina en vivo.

---

## 7. Data Model Overview

*Consulte [data-model.md](./data-model.md) para el detalle exhaustivo de atributos, DDL Flyway `V11` y mapeos JPA.*

- **Entidades Principales**:
  - `Ingredient` extendido con `criticalStockThreshold` (`Integer >= 0`, nullable) y método transient `isCritical()`.
  - `Dish` extendido con `pausedByChef` (`Boolean NOT NULL DEFAULT FALSE`).
  - Tres causas de indisponibilidad independientes: `sin_stock` (dinámico de recetas), `paused_by_chef` (columna boolean), `is_active`/`status` (admin).
- **Migración Flyway**: `V11__critical_stock_and_chef_pause.sql` con constraints `CHECK` e índices especializados (`idx_ingredients_critical_alert`, `idx_dishes_catalog_visibility`).

---

## 8. Behavioral & Interface Contracts Overview

*Consulte [contracts/behavioral-contracts.md](./contracts/behavioral-contracts.md) y [contracts/openapi.yaml](./contracts/openapi.yaml).*

1. `SET_INGREDIENT_CRITICAL_STOCK_THRESHOLD`: Configuración/edición de umbral individual (Admin, entero $\ge 0$).
2. `GET_INGREDIENT_CRITICALITY_STATE`: Consulta de estado de criticidad (Admin).
3. `LIST_INVENTORY_WITH_CRITICAL_ALERTS_AND_FILTER`: Listado con alertas visuales y filtro rápido `criticalOnly` (Admin).
4. `TOGGLE_CHEF_DISH_PAUSE`: Conmutación de pausa manual de plato (Chef, operable siempre).
5. `GET_CHEF_DISHES_OPERATIONAL_STATUS`: Consulta de estado operativo de cocina con ETag (Chef).
6. `SUBSCRIBE_CHEF_COLLABORATIVE_SYNC`: Sincronización colaborativa entre chefs en $\le 3\text{ s}$.
7. `SUBSCRIBE_DINER_CATALOG_REALTIME_AVAILABILITY`: Sincronización de catálogo en $\le 3\text{ s}$ ante pausa.
8. `GET_DISH_THREE_TIER_AVAILABILITY`: Consulta de las tres causas de indisponibilidad separadas (Personal).
9. `CART_HOLD_AND_LOCKOUT_ON_CHEF_PAUSE`: Preservación con bloqueo en carrito ante pausa del comensal.
10. `DISH_DETAIL_MODAL_CHEF_PAUSE_HANDLING`: Deshabilitación reactiva en modal abierto en $\le 3\text{ s}$ sin cierre forzado.

---

## 9. Testing Strategy

### 9.1 Backend Testing (JUnit 5 + Mockito + Testcontainers)
- **Unit Tests**:
  - `IngredientCriticalStockTest`: validación de entero $\ge 0$, rechazo de negativos y decimales, cálculo estricto $\text{cantidad} < \text{umbral}$, caso $= \text{umbral}$ no crítico (INV-3), caso umbral nulo (INV-7), caso umbral 0 (EC-001), umbral alto (EC-022), suspensión en descontinuado (INV-17).
  - `DishChefPauseTest`: conmutación de toggle, operabilidad con plato sin stock (EC-011), operabilidad con plato desactivado administrativamente (EC-012), desacoplamiento de stock y precios (INV-11).
  - `DishThreeTierAvailabilityTest`: coexistencia de las 3 causas separadas sin colapsar (INV-10, SC-014).
- **Security Tests (MockMvc)**:
  - `InventorySecurityTest`: Chef bloqueado al configurar umbral con HTTP 403 (INV-16, SC-009, EC-018); Admin bloqueado al operar toggle de pausa con HTTP 403 (INV-16, SC-010, EC-019).
- **Integration Tests (Testcontainers PostgreSQL 16)**:
  - `IngredientRepositoryCriticalFilterTest`: validación de DDL `V11` y filtro rápido `criticalOnly` (FR-020, SC-015).
  - `ChefPauseConcurrenceIntegrationTest`: simulación de dos chefs alternando el toggle casi simultáneamente; verificación de "última confirmación prevalece" (EC-025, EC-015).
  - `DinerCatalogAvailabilityPollingTest`: verificación de cambio de ETag y respuesta 304 ante mutaciones de pausa (INV-15).

### 9.2 Frontend Testing (Vitest + React Testing Library)
- **Component Tests**:
  - `IngredientFormModal.test.tsx`: renderizado de campo "Stock mínimo/crítico", validación de enteros $\ge 0$ en cliente.
  - `IngredientCatalogTable.test.tsx`: renderizado de insignia "⚠️ Stock crítico", funcionamiento de filtro rápido `criticalOnly`, ausencia de alertas en descontinuados.
  - `ChefDishesPage.test.tsx`: renderizado de toggle de pausa, conmutación optimista, ausencia de toggle en vistas de administrador.
  - `DishDetailModal.test.tsx`: recepción de evento de pausa en $\le 3\text{ s}$, inhabilitación de botones sin cierre de modal (EC-017).
  - `CartDrawer.test.tsx`: presencia de ítem pausado con aviso "Pausado por cocina", bloqueo de checkout, inhabilitación de incremento (FR-019, EC-023).
- **Hook Tests**:
  - `useChefDishesSync.test.ts`: polling cada 2500 ms con ETag y actualización de estado en cocina.
  - `useRealtimeAvailability.test.ts`: propagación de pausa al catálogo del comensal.

---

## 10. Error Handling & RFC 7807 Mapping

Todos los errores siguen la convención establecida en el backend de Spring Boot mediante `ProblemDetail` (RFC 7807):

| Condición de Error | Código HTTP | `title` | `detail` | Campo Asociado |
|---|---|---|---|---|
| Umbral crítico negativo | 400 Bad Request | Constraint Violation | El umbral de stock crítico debe ser un número entero mayor o igual a cero | `criticalStockThreshold` |
| Umbral con decimales o texto | 400 Bad Request | Type Mismatch | Formato numérico inválido. Se requiere un número entero | `criticalStockThreshold` |
| Chef intenta editar umbral | 403 Forbidden | Access Denied | Únicamente los administradores pueden configurar umbrales de stock | N/A |
| Admin intenta operar toggle chef | 403 Forbidden | Access Denied | Únicamente el personal con rol Chef puede accionar la pausa manual | N/A |
| Insumo o plato no encontrado | 404 Not Found | Resource Not Found | No se encontró el recurso solicitado en el establecimiento activo | `id` |
| Plato en tenant ajeno | 404 Not Found | Resource Not Found | Acceso denegado a recursos de otro establecimiento | `id` |

---

## 11. Invariant Enforcement Matrix

| Invariante | Descripción Funcional | Capa de Enforce | Mecanismo de Aplicación | Test que la Verifica |
|---|---|---|---|---|
| **INV-1** | Individualidad estricta del umbral crítico | Persistencia / Dominio | Columna por insumo en `ingredients`; sin parámetros globales | `IngredientCriticalStockTest#testIndividualThreshold` |
| **INV-2** | Dominio entero $\ge 0$ de umbral | Backend (DTO/DB) + Frontend | Constraint CHECK DB + `@Min(0)` Jakarta Validation + Form validation | `IngredientValidationTest#testNegativeAndDecimalRejection` |
| **INV-3** | Condición crítica estricta ($\text{stock} < \text{umbral}$) | Backend (Dominio) + DB | Evaluación booleana estricta; $\text{stock} = \text{umbral}$ no es crítico | `IngredientCriticalStockTest#testStrictlyLessThanThreshold` |
| **INV-4** | Resaltado visual en módulo de inventario | Frontend UI | Insignia visible prominente condicional a `isCritical = true` | `IngredientCatalogTableTest#testHighlightCriticalIngredients` |
| **INV-5** | Exclusividad visual en panel Back-Office | Backend / Integración | Ausencia de servicios de correo, SMS, push u órdenes automáticas | `InventoryNotificationTest#testNoExternalNotificationsEmitted` |
| **INV-6** | No bloqueo de venta comercial por umbral | Backend (Catálogo) | Platos se mantienen disponibles mientras el stock real sea $> 0$ | `DishAvailabilityEngineTest#testCriticalStockDoesNotBlockSale` |
| **INV-7** | Insumo sin umbral no emite alertas | Backend / Frontend | Valor `null` asigna `isCritical = false` por defecto | `IngredientCriticalStockTest#testNullThresholdEmitsNoAlert` |
| **INV-8** | Plato pausado oculto para el comensal | Backend (Catálogo) + Frontend | Platos con `paused_by_chef = true` excluidos de grilla comensal | `DinerCatalogServiceTest#testPausedDishHiddenFromCatalog` |
| **INV-9** | Reversibilidad de la pausa del chef | Backend (Dominio) | `paused_by_chef = false` restaura visibilidad (sujeto a stock y admin) | `DishServiceTest#testUnpauseRestoresVisibility` |
| **INV-10** | Distinción formal de las 3 causas (Invariante Central) | Backend (Modelo/DTO) + Frontend | Estados independientes: `sin_stock`, `paused_by_chef`, `is_active` | `DishThreeTierAvailabilityTest#testDistinctCauses` |
| **INV-11** | Desacoplamiento de stock, precios y recetas | Backend (Servicio) | Toggle no altera `ingredients`, `dish_recipe_ingredients` ni precios | `DishChefPauseTest#testToggleDoesNotMutateStockOrPrice` |
| **INV-12** | Coexistencia e independencia de causas | Backend (Dominio) | Despausar plato sin stock lo deja despausado pero no comprable | `DishThreeTierAvailabilityTest#testCoexistenceSinStockAndPause` |
| **INV-13** | Reversibilidad sin bloqueo mutuo | Backend (Servicio) | Desactivación admin no impide conmutar pausa del chef | `DishServiceTest#testAdminDeactivationDoesNotBlockChefToggle` |
| **INV-14** | Recálculo reactivo ante actualización | Backend (Servicio) | Modificar umbral o stock recalcula criticidad en siguiente consulta | `IngredientServiceTest#testThresholdUpdateRecalculatesImmediately` |
| **INV-15** | Propagación a comensal en $\le 3\text{ s}$ | Frontend / Polling ETag | Polling condicional cada $2500\text{ ms}$ en `useRealtimeAvailability` | `DinerCatalogAvailabilityPollingTest#testPropagationLatency` |
| **INV-16** | Autorización estricta por rol (RBAC) | Backend (Spring Security) | `@PreAuthorize`: Admin para umbral; Chef para pausa de plato | `InventorySecurityTest#testRbacSeparationAdminVsChef` |
| **INV-17** | Inmunidad de alertas en insumos descontinuados | Backend / Frontend | `status == DISCONTINUED` suprime alerta y conserva umbral | `IngredientCriticalStockTest#testDiscontinuedSuspendsAlert` |
| **INV-18** | Sincronización entre chefs en $\le 3\text{ s}$ | Frontend / Polling ETag | Polling condicional cada $2500\text{ ms}$ en `useChefDishesSync` | `ChefDishesSyncTest#testCollaborativeSyncBetweenChefs` |
| **INV-19** | Autonomía operativa permanente del toggle | Backend (Controlador/Servicio) | Toggle habilitado siempre, aun con `sin_stock` o desactivo por admin | `DishChefPauseTest#testToggleAlwaysOperable` |

### Tratamiento Especial de INV-10 (Matriz de Tres Causas)

```
+---------------------+-------------------------------+-----------------------------------+---------------------------------------+
| Dimensión           | 1. Falta de Stock (sin_stock) | 2. Pausa Manual Chef (pausa_chef) | 3. Desactivación Admin (desact_admin) |
+---------------------+-------------------------------+-----------------------------------+---------------------------------------+
| Naturaleza          | Automática / Reactiva stock   | Manual / Operativa / Temporal     | Manual / Comercial / Permanente       |
| Actor con Autoridad | Sistema (inventario recetas)  | Chef                              | Administrador                         |
| Motivo Operativo    | Insumos agotados en cocina    | Contingencia / Demora en cocina   | Retiro comercial / Fuera de carta     |
| Vía de Recuperación | Reabastecimiento de insumos   | Despausa voluntaria vía toggle    | Reactivación manual en Back-Office    |
| Efecto en Comensal  | Visible pero atenuado "Agotado"| Totalmente oculto del catálogo    | Totalmente oculto del catálogo        |
| Intercambiabilidad  | No forzable por personal      | Admin NO opera toggle del chef    | Chef NO opera activación comercial    |
+---------------------+-------------------------------+-----------------------------------+---------------------------------------+
```

---

## 12. Full Traceability Matrix

### 12.1 Acceptance Scenarios (15/15)

| AC | Descripción Resumida | Decisión / Diseño | Contrato / Artefacto | Test de Verificación |
|---|---|---|---|---|
| **US1 AC-1.1** | Admin define umbral entero $\ge 0$ en creación | D-001 | Contrato 1 (`POST /ingredients`) | `IngredientCreationTest#testWithValidThreshold` |
| **US1 AC-1.2** | Admin edita umbral sin afectar otros insumos | D-001 | Contrato 1 (`PUT /ingredients/{id}`) | `IngredientUpdateTest#testIndividualThresholdIsolation` |
| **US1 AC-1.3** | Rechazo de valores negativos, decimales o texto | D-001 | Contrato 1 (ProblemDetail 400) | `IngredientValidationTest#testNegativeAndDecimalRejection` |
| **US1 AC-1.4** | Chef bloqueado al intentar definir umbral | D-003, D-001 | Contrato 1 (HTTP 403) | `InventorySecurityTest#testChefCannotSetThreshold` |
| **US2 AC-2.1** | Venta cruza umbral $\to$ resalta visualmente | D-002 | Contrato 2, 3 (`isCritical=true`) | `IngredientAlertIntegrationTest#testSaleTriggersAlert` |
| **US2 AC-2.2** | Ajuste manual reduce stock $\to$ resalta | D-002 | Contrato 2, 3 (`isCritical=true`) | `IngredientAlertIntegrationTest#testAdjustmentTriggersAlert` |
| **US2 AC-2.3** | Reposición $\ge \text{umbral} \to$ remueve alerta | D-002 | Contrato 2, 3 (`isCritical=false`)| `IngredientAlertIntegrationTest#testReplenishmentRemovesAlert` |
| **US2 AC-2.4** | Alerta confinada a Back-Office sin emails/SMS | D-002 | Contrato 3 (No side-effects) | `InventoryNotificationTest#testNoExternalAlerts` |
| **US2 AC-2.5** | Múltiples insumos críticos resaltan juntos | D-002 | Contrato 3 (Grilla inventario) | `IngredientCatalogTableTest#testMultipleAlertsRendered` |
| **US2 AC-2.6** | Filtro rápido aísla insumos críticos | D-003 | Contrato 3 (`criticalOnly=true`) | `IngredientRepositoryCriticalFilterTest#testCriticalOnly` |
| **US3 AC-3.1** | Chef apaga toggle $\to$ plato desaparece | D-004, D-006 | Contrato 4, 7 (`paused=true`) | `ChefDishPauseIntegrationTest#testPauseHidesFromDiner` |
| **US3 AC-3.2** | Chef enciende toggle $\to$ plato reaparece | D-004, D-006 | Contrato 4, 7 (`paused=false`) | `ChefDishPauseIntegrationTest#testUnpauseRestoresToDiner` |
| **US3 AC-3.3** | Pausa no altera stock, recetas ni precios | D-005, D-009 | Contrato 4 (Desacoplado) | `DishChefPauseTest#testToggleDoesNotMutateStockOrPrice` |
| **US3 AC-3.4** | Despausar plato sin stock lo deja no comprable | D-004, D-005 | Contrato 4, 8 (`sin_stock=true`) | `DishThreeTierAvailabilityTest#testCoexistenceSinStockAndPause` |
| **US3 AC-3.5** | Admin no opera toggle de pausa del chef | D-003, D-005 | Contrato 4 (HTTP 403) | `InventorySecurityTest#testAdminCannotOperateChefToggle` |

### 12.2 Edge Cases (25/25)

| Edge Case | Descripción Resumida | Decisión / Mecanismo | Test Verificador |
|---|---|---|---|
| **EC-001** | Umbral configurado en 0 | D-001: Alerta solo si stock $< 0$; en stock normal no alerta | `IngredientCriticalStockTest#testZeroThreshold` |
| **EC-002** | Insumo sin umbral (nulo) | D-001: `criticalStockThreshold = null` $\to$ `isCritical = false` | `IngredientCriticalStockTest#testNullThreshold` |
| **EC-003** | Umbral fijado mayor a stock actual | D-002: Insumo entra de inmediato en estado crítico | `IngredientAlertIntegrationTest#testHigherThresholdTriggersAlert` |
| **EC-004** | Stock igual a 0 con umbral $> 0$ | D-002: Satisface $0 < \text{umbral} \to$ alerta activa | `IngredientCriticalStockTest#testZeroStockWithPositiveThreshold` |
| **EC-005** | Cruce de umbral por venta de platos | D-002: Recálculo reactivo en siguiente consulta | `IngredientAlertIntegrationTest#testSaleTriggersAlert` |
| **EC-006** | Cruce de umbral por merma/ajuste | D-002: Ajuste manual activa estado crítico inmediatamente | `IngredientAlertIntegrationTest#testAdjustmentTriggersAlert` |
| **EC-007** | Reabastecimiento supera umbral | D-002: Estado crítico pasa a falso; se retira alerta | `IngredientAlertIntegrationTest#testReplenishmentRemovesAlert` |
| **EC-008** | Insumo crítico en múltiples platos | D-002: Alerta solo en inventario; platos no alertan ni bloquean | `DishAvailabilityEngineTest#testCriticalStockDoesNotBlockSale` |
| **EC-009** | Insumo crítico sin recetas | D-002: Alerta visible en inventario independientemente de platos | `IngredientCriticalStockTest#testIngredientWithoutRecipesAlerts` |
| **EC-010** | Cambio de umbral durante navegación | D-002: Evento interno; no interrumpe comensales | `DinerCatalogServiceTest#testThresholdChangeDoesNotAffectDiner` |
| **EC-011** | Chef pausa plato agotado | D-004, D-005: Coexisten causas; al despausar sigue no comprable | `DishThreeTierAvailabilityTest#testCoexistenceSinStockAndPause` |
| **EC-012** | Chef pausa plato desactivo por admin | D-004, D-005: Prevalece desactivación comercial; no visible | `DishThreeTierAvailabilityTest#testCoexistenceAdminDeactivatedAndPause` |
| **EC-013** | Admin desactiva plato pausado | D-004: Ambos estados coexisten; reactivar admin no despausa chef | `DishThreeTierAvailabilityTest#testAdminReactivationKeepsChefPause` |
| **EC-014** | Chef despausa plato sin stock | D-004: Despausa exitosa; catálogo presenta distintivo "Agotado" | `DishThreeTierAvailabilityTest#testUnpauseOutOfStockRendersAgotado` |
| **EC-015** | Alternancia rápida del toggle | D-005: Transacción atómica; último valor confirmado prevalece | `ChefPauseConcurrenceIntegrationTest#testRapidToggle` |
| **EC-016** | Chef pausa categoría completa | D-005: Pausas individuales por plato; sin acciones masivas | `ChefDishesPageTest#testIndividualTogglesOnly` |
| **EC-017** | Comensal con modal abierto al pausar | D-008: Reflejo en $\le 3\text{ s}$, deshabilita compra sin cerrar | `DishDetailModalTest#testReactivePauseWithoutClosing` |
| **EC-018** | Chef intenta configurar umbral | D-003: HTTP 403 Forbidden | `InventorySecurityTest#testChefCannotSetThreshold` |
| **EC-019** | Admin intenta accionar toggle chef | D-003: HTTP 403 Forbidden; control ausente en UI de admin | `InventorySecurityTest#testAdminCannotOperateChefToggle` |
| **EC-020** | Múltiples insumos entran en crítico | D-002: Todos resaltan simultáneamente sin degradación | `IngredientCatalogTableTest#testMultipleAlertsRendered` |
| **EC-021** | Múltiples platos pausados por chef | D-005: Cada plato se oculta individualmente del menú | `ChefDishPauseIntegrationTest#testMultipleDishesPaused` |
| **EC-022** | Umbral fijado en valor muy alto | D-001: Aceptado sin topes máximos artificiales | `IngredientValidationTest#testArbitrarilyHighThresholdAccepted` |
| **EC-023** | Plato en carrito al momento de pausar | D-007: Preservación con advertencia y bloqueo de checkout | `CartDrawerTest#testHoldAndLockoutOnChefPause` |
| **EC-024** | Insumo descontinuado con stock bajo | D-009: Alerta suspendida; se reactiva si vuelve a Active | `IngredientCriticalStockTest#testDiscontinuedSuspendsAlert` |
| **EC-025** | Concurrencia entre chefs sobre toggle | D-005, D-006: Última confirmación prevalece; sync en $\le 3\text{ s}$ | `ChefPauseConcurrenceIntegrationTest#testMultiChefConcurrence` |

### 12.3 Invariantes (19/19)
Cubiertas en la matriz de la Sección 11 con 100% de trazabilidad individual.

### 12.4 Functional Requirements (23/23)
- `FR-001..FR-003`: D-001, Contrato 1, `Ingredient.java`.
- `FR-004..FR-007`: D-002, Contrato 2, 3, `isCritical()`.
- `FR-008`: D-002, Contrato 3, Exclusividad visual Back-Office.
- `FR-009`: D-002, No bloqueo de venta comercial por stock crítico.
- `FR-010`: D-002, Recálculo automático de criticidad.
- `FR-011..FR-014`: D-004, D-005, Contrato 4, `paused_by_chef`.
- `FR-015`: D-004, Contrato 8, Tres causas independientes (INV-10).
- `FR-016`: D-008, Contrato 10, Modal abierto deshabilitado en $\le 3\text{ s}$ sin cierre forzado.
- `FR-017, FR-018`: D-003, Contrato 1 y 4, Control RBAC `@PreAuthorize`.
- `FR-019`: D-007, Contrato 9, Carrito preservado con bloqueo checkout.
- `FR-020`: D-003, Contrato 3, Filtro rápido `criticalOnly`.
- `FR-021`: D-009, Contrato 2, Suspensión de alerta en `DISCONTINUED`.
- `FR-022`: D-006, Contrato 5, 6, Sincronización colaborativa entre chefs en $\le 3\text{ s}$.
- `FR-023`: D-005, Contrato 4, Operabilidad preventiva permanente del toggle.

### 12.5 Success Criteria (15/15)
- `SC-001..SC-005`: D-001, D-002, Contrato 1..3, Tests de umbral y alerta visual en inventario.
- `SC-006..SC-008`: D-004..D-006, Contrato 4..7, Tests de catálogo, visibilidad y panel del chef.
- `SC-009, SC-010`: D-003, Tests de RBAC y segregación de interfaces.
- `SC-011..SC-013`: D-002, Tests de ventas no bloqueadas y reactividad de alertas ante reposición.
- `SC-014`: D-004, Test de coexistencia de las tres causas de indisponibilidad.
- `SC-015`: D-003, Test de reducción de lista por filtro rápido `criticalOnly`.

### 12.6 Clarifications (12/12)
- `C-1`: Condición estrictamente menor ($\text{stock} < \text{umbral}$). Reflejado en D-002 e INV-3.
- `C-2`: Alerta exclusivamente visual en Back-Office. Reflejado en D-002 e INV-5.
- `C-3`: Umbral individual por insumo, sin umbral global. Reflejado en D-001 e INV-1.
- `C-4`: Tres causas distinguibles e independientes. Reflejado en D-004 e INV-10.
- `C-5`: Spec no prescribe posición ni estilo visual del toggle. Reflejado en D-010.
- `C-6`: Umbral en inventario desacoplado de platos. Reflejado en D-001.
- `C-7`: Umbral no altera ventas ni visibilidad mientras stock $> 0$. Reflejado en D-002 e INV-6.
- `C-8`: Carrito ante pausa: preservación con bloqueo. Reflejado en D-007 y Contrato 9.
- `C-9`: Filtro rápido de criticidad en inventario. Reflejado en D-003 y Contrato 3.
- `C-10`: Suspensión de alerta en `DISCONTINUED`. Reflejado en D-009 e INV-17.
- `C-11`: Sincronización colaborativa entre chefs en $\le 3\text{ s}$. Reflejado en D-006 y Contrato 6.
- `C-12`: Toggle del chef permanentemente operable. Reflejado en D-005 e INV-19.

---

## 13. Assumptions

- `[SUPUESTO]` Existe un módulo de inventario con ingredientes administrables (crear/editar) gobernado por el rol Administrador, según lo definido en la especificación 004.
- `[SUPUESTO]` Existe un módulo de catálogo gastronómico con platos publicados y un mecanismo de visualización para el comensal, según lo establecido en las especificaciones 007 y 008.
- `[SUPUESTO]` Existe un registro de stock por ingrediente que varía de forma dinámica por efecto de ventas comerciales y de ajustes manuales de inventario, conforme a las especificaciones 004, 008 y 011.
- `[SUPUESTO]` Existen roles de usuario diferenciados ("Administrador" y "Chef") con mecanismos de autenticación y autorización preexistentes, según las especificaciones 001, 002 y 004.
- `[SUPUESTO]` Existe infraestructura de sincronización en tiempo real para propagar cambios de disponibilidad y visibilidad en el catálogo del comensal con una latencia objetivo no mayor a 3 segundos, conforme a la especificación 008.
- `[SUPUESTO]` Existen recetas técnicas que vinculan insumos del inventario con platos del catálogo, según lo especificado en las especificaciones 005 y 006.
- `[SUPUESTO]` Existe el estado de "sin stock" de un plato derivado de manera automática del inventario de insumos de cocina, conceptual y operativamente diferenciado de la pausa manual del chef, de acuerdo con la especificación 008.
- `[SUPUESTO]` Existe la facultad y autoridad del administrador para desactivar y despublicar platos de manera permanente o comercial del catálogo, preexistente e independiente de la pausa de servicio del chef, conforme a las especificaciones 007 y 008.
- `[SUPUESTO]` La interpretación operativa de "caer por debajo" del umbral de stock crítico en el criterio AC-2.1 es estrictamente menor ($\text{cantidad} < \text{umbral}$). Valores iguales al umbral se consideran dentro del margen de seguridad.
- `[SUPUESTO]` Si el administrador define un umbral de stock crítico igual a 0, se asume que el ingrediente solo alertará si la cantidad física llega a registrarse negativamente por un desajuste manual de inventario; bajo operación estándar sin valores negativos, el umbral 0 desactiva efectivamente la alerta.
- `[SUPUESTO]` Un ingrediente recién creado o sin configuración previa de umbral crítico asume un estado sin umbral de referencia, no emitiendo alertas hasta que el administrador defina un valor explícito.
- `[SUPUESTO]` No se imponen límites superiores arbitrarios al valor del umbral de stock crítico, admitiéndose cualquier número entero mayor o igual a cero que el administrador considere adecuado para su operación.
- `[SUPUESTO]` La ubicación, estilo visual, paleta de colores y disposición del control de alternancia (toggle) en la interfaz del chef constituyen decisiones de diseño de interfaz de usuario que corresponden a la fase de planificación técnica, manteniéndose esta especificación puramente orientada al comportamiento funcional observable.

---

## 14. Out of Scope

Quedan formal y explícitamente fuera del alcance de la presente especificación e iteración las siguientes funcionalidades y comportamientos:
- Envío de correos electrónicos, mensajes SMS, notificaciones push o comunicaciones externas dirigidas al chef, administrador o proveedores ante alertas de stock crítico o pausas de platos.
- Generación automatizada de órdenes de compra, integración con proveedores externos, intercambio electrónico de datos (EDI) o portales marketplace B2B.
- Configuración de umbrales críticos globales, únicos o por categoría de producto; la configuración se limita estrictamente a la modalidad individual por insumo.
- Múltiples niveles escalonados de umbrales de stock (por ejemplo: advertencia preventiva, nivel medio y crítico) por ingrediente.
- Algoritmos de pronóstico de consumo, estimación proyectada de agotamiento o sugerencias automáticas de cantidades a comprar.
- Programación diferida de pausas por fecha u horario, pausas automáticas por franjas del día o desactivaciones por temporada.
- Temporizadores de auto-expiración o duración máxima predeterminada para la pausa manual del chef.
- Campos de texto libre o selectores de motivos obligatorios asociados a la activación de la pausa manual del chef.
- Notificaciones directas o avisos emergentes dirigidos al comensal cuando un plato se pausa o despausa durante su navegación.
- Mecanismos de autenticación, creación de cuentas o gestión de permisos de roles (funcionalidades preexistentes provistas por las especificaciones 001, 002 y 004).
- Facultad del administrador para anular o conmutar la pausa manual del chef desde el mecanismo específico del chef (el administrador utiliza su propio canal de desactivación administrativa).
- Edición o alteración de recetas, insumos o fichas técnicas por parte del chef desde el control de pausa.
- Modificación de precios comerciales de venta al público por parte del chef.
- Generación de reportes de auditoría de umbrales, tableros de analítica de criticidad o historiales de cambios de umbral y pausas.
- Afectación de los filtros de catálogo manuales por la pausa (los filtros operan sobre platos visibles; la pausa oculta el plato antes de la evaluación de filtros).
- Gestión multi-sucursal, depósitos distribuidos o umbrales diferenciados por proveedor.
- Historial acumulado de pausas por plato o registros históricos de reposición en esta pantalla.
- Alertas visibles dirigidas al comensal sobre el estado de reposición de los ingredientes de cocina.
- Umbrales de stock mínimo configurados a nivel de plato terminado (la gestión se restringe exclusivamente al inventario de ingredientes).

---

## 15. Risks and Mitigations

| Riesgo Técnico / Operativo | Impacto | Probabilidad | Estrategia de Mitigación |
|---|---|---|---|
| Colisión entre el toggle del chef y la reactivación administrativa | Alto | Baja | Modelo desacoplado (INV-10): `paused_by_chef` e `is_active` son campos distintos; despausar no reactiva lo administrativo y viceversa. |
| Saturación de peticiones de polling en el backend | Medio | Media | Uso estricto de HTTP 304 Not Modified con hashing SHA-256 de ETags; consultas indexadas en base de datos. |
| Inconsistencia en la orden del carrito si el comensal no nota la pausa | Medio | Media | Preservación del producto con advertencia visible explícita `"Pausado por cocina"` y bloqueo del checkout (FR-019, C-8). |
| Alternancia rápida de toggle por chefs concurrentes | Bajo | Media | Actualizaciones atómicas en base de datos; resolución "el último cambio confirmado prevalece" (EC-015, EC-025). |
| Falsa alarma de stock crítico en insumos descontinuados | Bajo | Baja | Regla de dominio que suspende alertas cuando `status == DISCONTINUED` (INV-17, FR-021). |

---

## 16. Phasing (Fases de Implementación)

1. **Fase 1: Modelo de Umbral Crítico, Migración Flyway V11 y Autorización Admin**
   - Ejecución de migración `V11__critical_stock_and_chef_pause.sql`.
   - Extensión de entidad `Ingredient`, DTOs de creación/edición y validación Bean Validation $\ge 0$.
   - Enforcement de seguridad `@PreAuthorize` en `AdminIngredientController`.
   - *Criterio de Done*: Tests unitarios de validación y persistencia de umbral pasando al 100%.

2. **Fase 2: Cálculo de Criticidad, Resaltado Visual, Filtro Rápido y Suspensión en Descontinuado**
   - Lógica de dominio `isCritical()` en backend y extensión de `AdminIngredientDetailResponse`.
   - Soporte de parámetro `criticalOnly` en repositorio y controlador.
   - Componentes frontend: insignia visual destacada en `IngredientCatalogTable.tsx` y toggle de filtro rápido en cabecera.
   - *Criterio de Done*: Escenario 1 y 2 de `quickstart.md` verificados con tests de integración.

3. **Fase 3: Modelo de Tres Causas, Toggle del Chef y Sincronización entre Chefs**
   - Extensión de entidad `Dish` con `paused_by_chef`.
   - Endpoint `PATCH /api/v1/dishes/{id}/chef-pause` y `GET /api/v1/dishes/operational-status` con soporte ETag.
   - Componente `ChefDishPauseToggle` en `ChefDishesPage.tsx` y hook `useChefDishesSync` (polling 2500 ms).
   - *Criterio de Done*: Escenarios 3, 4 y 5 de `quickstart.md` verificados con tests de concurrencia.

4. **Fase 4: Propagación al Catálogo del Comensal, Carrito y Modal de Detalle**
   - Integración en `DinerCatalogService` y `DishAvailabilityEngine` para ocultar platos pausados del menú.
   - Adaptación de `CartContext.tsx` y `CartDrawer.tsx` para advertencia `"Pausado por cocina"` y bloqueo de checkout.
   - Adaptación reactiva de `DishDetailModal.tsx` ante pausa en vivo sin cierre de modal.
   - *Criterio de Done*: Escenarios 6 y 7 de `quickstart.md` verificados en suite RTL.

5. **Fase 5: Validación Integral End-to-End y Cierre de Calidad**
   - Ejecución completa de suites de integración Testcontainers y Vitest.
   - Verificación de cumplimiento de las 19 invariantes y 15 success criteria.
   - *Criterio de Done*: Suite completa verde sin regresiones en specs previas.

---

## 17. Executive Summary

El presente plan técnico formaliza el diseño e integración de la feature 013 (`critical-stock-chef-pause`) sobre el stack establecido de EasyEat (Spring Boot 3.3.4, Java 21, PostgreSQL 16 con Flyway, React 18, TypeScript 5.6 y Tailwind CSS). Las decisiones clave garantizan: (1) la persistencia individual de umbrales críticos ($\ge 0$) gobernados por el Administrador con alertas estrictamente visuales en el Back-Office y filtro rápido, (2) la preservación ortogonal de las tres causas de indisponibilidad (`sin_stock`, `paused_by_chef`, `desactivacion_admin`) sin colapsos de estado, (3) la operabilidad permanente del toggle del chef con propagación en $\le 3\text{ s}$ tanto para comensales como para la brigada de cocina mediante polling condicional HTTP con ETags, y (4) el tratamiento robusto del carrito (preservación con bloqueo) y del modal de detalle en vivo. Cobertura completa y verificable: 15/15 ACs, 25/25 ECs, 19/19 INVs, 23/23 FRs, 15/15 SCs y 12/12 Clarifications. **Confirmación explícita: NO se generaron tareas de implementación (`tasks.md` es competencia de `/speckit-tasks`).**

