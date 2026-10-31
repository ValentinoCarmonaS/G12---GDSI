# Implementation Plan: Shopping Cart Management & Diner Profile Demographics

**Branch**: `009-shopping-cart` | **Fecha**: 31/10/2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/009-shopping-cart/spec.md`

---

## Summary

Implementar la extensión integral full-stack del carrito de compras y del perfil demográfico del comensal para la plataforma web gastronómica EasyEat. Esta iteración abarca cuatro áreas funcionales rigurosamente acordadas:
1. **COM-6.1.1 (Agregar plato al pedido)**: Incorporación de un selector numérico previo de cantidad en la vista de detalle del plato (`DishDetailModal`), permitiendo añadir de 1 a $N$ unidades a la sesión local del comensal en una única acción, con acumulación automática para platos repetidos y notificación visual de éxito.
2. **COM-6.1.2 (Ver acumulado del carrito)**: Despliegue de un botón flotante interactivo (`FloatingCartButton`) en la esquina inferior derecha, visible de forma biunívoca exclusivamente en las vistas del catálogo cuando la orden contiene $\ge 1$ ítem. El botón exhibe la cantidad total de unidades y el subtotal monetario consolidado en tiempo real sin recargas de página, desplegando al interactuar un modal/drawer (`CartDrawer`) con el desglose íntegro. Todos los cálculos se rigen por aritmética de punto fijo a dos decimales basada en centavos enteros.
3. **COM-6.1.3 (Modificar cantidades)**: Controles interactivos de suma y resta unitaria en el modal del carrito con recálculo instantáneo. El incremento se bloquea automáticamente al alcanzar el stock real disponible en cocina; restar sobre la última unidad (cantidad = 1) elimina el plato por completo. Las modificaciones rápidas aplican actualización optimista inmediata con sincronización diferida (*debouncing* de 300 ms) y reversión determinista (*rollback*) con alerta visible ante fallos de red o inconsistencias de stock.
4. **COM-1.4.2 (Editar datos demográficos)**: Formulario de edición demográfica que amplía el expediente del comensal con edad (validación estricta en rango biológico de 14 a 120 años), género (clasificación cerrada estándar), domicilio de contacto (texto libre hasta 255 caracteres, aislado de ruteo o logística externa) y gestión de foto de perfil. La subida de imagen admite JPEG, PNG y WEBP hasta 5 MB (5.242.880 bytes inclusivo) persistida en PostgreSQL (`BYTEA`), reflejándose de inmediato en la barra superior (`WorkspaceLayout`). La eliminación de foto cuenta con diálogo de verificación y confirmación diferida al presionar "Guardar cambios", restableciendo el avatar institucional por defecto.

---

## Technical Context

**Language/Version**: Java 21 (LTS), TypeScript 5.x / React 18.  
**Primary Dependencies**:
- *Backend*: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Spring Security, Spring Validation), Flyway Core 10.x, PostgreSQL Driver (`42.7.4`), JJWT (`0.12.5`), Lombok, Jackson Problem (`problem-spring-web` RFC 7807), Testcontainers (`1.20.1`). *(Verificado en `easyeat/backend/pom.xml`)*.
- *Frontend*: React 18, React Router v6 (`react-router-dom`), Tailwind CSS v4, Lucide React (iconos), Axios (`apiClient.ts`). *(Verificado en `easyeat/frontend/package.json`)*.  
**Storage**: PostgreSQL 16 (tabla `users` extendida con `age`, `gender`, `address`; tabla `user_photos` para almacenamiento binario `BYTEA` de avatares; sesión local `sessionStorage` para el carrito de compras con clave `'easyeat_cart'`). *(Verificado en migraciones `V1` a `V9` y `CartContext.tsx`)*.  
**Testing**:
- *Backend*: JUnit 5, Mockito, MockMvc, AssertJ, Testcontainers (pruebas de integración PostgreSQL reales).
- *Frontend*: Vitest, React Testing Library, jsdom, `@testing-library/user-event`. *(Verificado en `easyeat/frontend/src/test`)*.  
**Target Platform**: Servidores Linux / contenedores Docker para backend Spring Boot; navegadores web modernos estándar (Chrome, Firefox, Safari, Edge) para el frontend SPA.  
**Project Type**: Full-stack Web Application (Spring Boot REST API Gateway + React Single-Page Application).  
**Performance Goals**:
- Latencia de adición y recálculo visual en carrito: $\le 100\text{ ms}$ (en memoria en cliente).
- Sincronización diferida de stock: ventana de *debouncing* de $300\text{ ms}$.
- Reflejo de avatar en barra de navegación: $\le 1\text{ segundo}$ tras confirmación en servidor.
- Ocultamiento de botón flotante al vaciar carrito: $\le 500\text{ ms}$.  
**Constraints**:
- Autenticación obligatoria con rol `ROLE_COMENSAL` para acceso al catálogo, perfil demográfico y endpoints de avatar. Clientes anónimos reciben HTTP 401 Unauthorized sin fuga de datos.
- Aislamiento multi-tenant estricto: el carrito se encuentra ligado al `tenantId` del restaurante activo; cambiar de establecimiento alerta y vacía la orden (Spec 008 COM-1.5.1 CA 5).
- Aritmética decimal exacta a 2 decimales (punto fijo mediante centavos enteros, INV-2).
- Cota de tamaño de imagen: $0 < \text{tamaño} \le 5.242.880\text{ bytes}$ (INV-4).
- Cota de edad biológica: $14 \le \text{edad} \le 120$ (INV-5).
- Domicilio exclusivamente como dato de contacto de texto, sin motores de ruteo, geocodificación ni APIs externas de delivery (INV-8).  
**Scale/Scope**: Cientos de restaurantes concurrentes, menús de más de 50 platos por local, órdenes de carrito con hasta 50 ítems simultáneos, usuarios comensales gestionando personalización de perfil.

---

## Constitution Check

*GATE: Evaluación frente a la Constitución SDD versión 2.0.0. Aprobación obligatoria antes de avanzar a descomposición de tareas.*

| Principio | Estado | Evidencia y Justificación Técnica |
|---|---|---|
| **I. The Specification Is the Source of Truth** | **PASS** | El plan técnico cubre rigurosamente los 15 criterios de aceptación (15/15), las 9 invariantes matemáticas y de negocio (INV-1 a INV-9) y los 32 requerimientos funcionales (FR-001 a FR-032) definidos en `specs/009-shopping-cart/spec.md`. No se redefinen requisitos funcionalmente. |
| **II. Specification Before Implementation** | **PASS** | La especificación fue completada y ratificada previamente; el proceso de ingeniería produjo los artefactos de diseño requeridos (`research.md`, `data-model.md`, `contracts/openapi.yaml`, `contracts/behavioral-contracts.md`, `quickstart.md`, `plan.md`) antes de cualquier codificación o generación de tareas. |
| **III. Layered Verification Proportional to Risk** | **PASS** | Estrategia de pruebas en 4 capas: (1) Pruebas unitarias de invariantes matemáticas (aritmética de centavos INV-2, suma de unidades INV-1, rango de edad INV-5); (2) Pruebas de integración Spring Boot con Testcontainers y MockMvc validando DDL Flyway `V10`, constraints de base de datos y endpoints de avatar/demográficos; (3) Pruebas de componentes frontend (RTL) para selector numérico, botón flotante, modal y debounce/rollback; (4) Pruebas de regresión con specs previas (003, 007, 008). |
| **IV. Simple, Cohesive, and Justified Design** | **PASS** | Se reutiliza `sessionStorage` y `CartContext` existente en lugar de introducir esquemas complejos de carritos distribuidos en base de datos. Se reutiliza el patrón probado de almacenamiento `BYTEA` de `DishPhoto` para los avatares (`UserPhoto`), evitando dependencias innecesarias de proveedores cloud externos. |
| **V. Bounded Scope** | **PASS** | El alcance está estrictamente delimitado a las 4 historias de usuario (COM-6.1.1, COM-6.1.2, COM-6.1.3, COM-1.4.2). Checkout, pasarelas de pago, ruteo geográfico, filtros de catálogo e historial de compras quedan explícitamente excluidos como Out of Scope. |
| **VI. Controlled Technical Debt** | **PASS** | Se incorpora la migración limpia `V10__extend_user_demographics_and_avatar.sql` siguiendo secuencialmente a `V9__admin_dish_approval_pricing.sql`, sin modificar tablas de cocina ni alterar migraciones históricas. |
| **VII. Traceability, Consistency, and Documentation** | **PASS** | Matriz de trazabilidad bidireccional completa documentada en este plan: 15/15 ACs, 9/9 Invariantes y 32/32 FRs vinculados con decisiones técnicas, contratos y pruebas verificables. |
| **VIII. Evidence Over Assumption and Human Escalation** | **PASS** | Todas las decisiones técnicas se sustentan en patrones observables del repositorio (`CartContext.tsx`, `dish_photos`, `WorkspaceLayout.tsx`, `ProfileController.java`) y en las aclaraciones formales acordadas en la sesión de grill-me del 31/10/2026. |
| **IX. Engineering Over Convenience** | **PASS** | Errores estructurados RFC 7807 `ProblemDetail`, validación de magic bytes para evitar spoofing de extensiones de imagen, constraints `CHECK` en PostgreSQL como última línea de defensa ante datos inválidos. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | **PASS** | Entrega un vertical slice full-stack integrado: backend (migración Flyway `V10`, entidades JPA, repositorios, servicios, controladores REST, validaciones) y frontend (componentes React, context providers, hooks, estilos Tailwind y vistas integradas). |

---

## Project Structure

### Documentation (this feature)

```text
specs/009-shopping-cart/
├── spec.md                  # Especificación funcional aprobada
├── checklists/
│   └── requirements.md      # Lista de verificación de calidad del spec
├── plan.md                  # Este plan técnico de implementación
├── research.md              # Fase 0: investigación y decisiones técnicas
├── data-model.md            # Fase 1: modelo de datos conceptual y esquema relacional
├── quickstart.md            # Fase 1: guía de validación end-to-end ejecutable
├── contracts/               # Fase 1: especificación de contratos
│   ├── openapi.yaml         # Contrato OpenAPI 3.0.3 de endpoints expuestos
│   └── behavioral-contracts.md # Contratos de comportamiento detallados por operación
└── tasks.md                 # Fase 2: desglose de tareas (a ser generado por /speckit-tasks)
```

### Source Code Layout

```text
easyeat/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/easyeat/
│   │   │   │   ├── profile/
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── ProfileController.java            # Extendido: PUT /demographics, endpoints /avatar
│   │   │   │   │   │   └── ProfileAvatarController.java      # GET /api/v1/profile/avatar (stream binario)
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── UserPhoto.java                    # Entidad JPA para almacenamiento BYTEA de avatares
│   │   │   │   │   │   └── Gender.java                       # Enum para clasificación estándar de género
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── DemographicsRequest.java          # DTO de entrada: edad (14-120), género, domicilio
│   │   │   │   │   │   ├── AvatarUploadResponse.java         # DTO de respuesta de subida de avatar
│   │   │   │   │   │   └── ExtendedProfileResponse.java      # Extensión de ProfileResponse con datos demográficos
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   └── UserPhotoRepository.java          # JPA Repository para tabla user_photos
│   │   │   │   │   └── service/
│   │   │   │   │       ├── ProfileService.java               # Extendido con actualización demográfica
│   │   │   │   │       └── UserPhotoService.java             # Lógica de validación (magic bytes, <= 5MB) y persistencia
│   │   │   │   ├── cart/
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   └── CartValidationController.java     # POST /api/v1/cart/validate-stock (soporte de sync)
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── CartStockValidationRequest.java
│   │   │   │   │       └── CartStockValidationResponse.java
│   │   │   │   ├── auth/domain/
│   │   │   │   │   └── User.java                             # Extendido con columnas age, gender, address
│   │   │   │   └── config/
│   │   │   │       └── SecurityConfig.java                   # Configuración de permisos para /profile/** y /cart/**
│   │   │   └── resources/
│   │   │       └── db/migration/
│   │   │           └── V10__extend_user_demographics_and_avatar.sql # DDL Flyway para user_photos y columnas en users
│   │   └── test/java/com/easyeat/
│   │       ├── profile/
│   │       │   ├── controller/ProfileControllerTest.java     # Pruebas MockMvc de endpoints demográficos y avatar
│   │       │   ├── service/UserPhotoServiceTest.java         # Pruebas unitarias de límites de imagen y magic bytes
│   │       │   └── domain/DemographicsValidationTest.java    # Pruebas de validación de edad (14-120) y constraints
│   │       └── cart/
│   │           └── CartValidationIntegrationTest.java        # Pruebas de integración de validación de stock
│   └── pom.xml
│
└── frontend/
    ├── src/
    │   ├── api/
    │   │   ├── profileApi.ts                                 # Extendido: updateDemographics, uploadAvatar, removeAvatar
    │   │   └── cartValidationApi.ts                          # Cliente para validación de stock de carrito
    │   ├── components/
    │   │   ├── cart/
    │   │   │   ├── FloatingCartButton.tsx                    # Nuevo: Botón flotante reactivo (cantidad + subtotal exacto)
    │   │   │   ├── CartDrawer.tsx                            # Extendido: Controles (+/-), subtotales y eliminación total
    │   │   │   └── CartItemWithBadge.tsx                     # Extendido: Advertencias de stock y formato monetario
    │   │   ├── catalog/
    │   │   │   └── DishDetailModal.tsx                       # Extendido: Selector numérico previo (1..stock) y botón agregar
    │   │   ├── common/
    │   │   │   └── WorkspaceLayout.tsx                       # Extendido: Reflejo inmediato de avatar en barra de navegación
    │   │   └── profile/
    │   │       ├── PersonalDataForm.tsx                      # Extendido: Campos edad, género, domicilio y gestión de foto
    │   │       └── PhotoDeleteConfirmModal.tsx               # Nuevo: Diálogo de confirmación para eliminación diferida
    │   ├── context/
    │   │   └── CartContext.tsx                               # Extendido: addItem con cantidad, debounce 300ms y rollback
    │   ├── hooks/
    │   │   ├── useCart.ts                                    # Reexportación del hook de carrito
    │   │   └── useDebouncedCartModifier.ts                   # Hook para gestión de ráfagas (+/-), debounce y rollback
    │   ├── utils/
    │   │   └── currencyUtils.ts                              # Nuevo: Aritmética decimal exacta en centavos enteros (INV-2)
    │   ├── types/
    │   │   ├── cart.ts                                       # Extendido: availableStock, lastSyncedQuantity
    │   │   └── profile.ts                                    # Extendido: age, gender, address, avatarUrl
    │   └── pages/
    │       ├── CatalogPage.tsx                               # Integración de FloatingCartButton y visibilidad condicional
    │       └── ProfilePage.tsx                               # Integración del formulario demográfico
    └── tests/
        ├── cart/
        │   ├── CartContext.test.tsx                          # Pruebas de INV-1, INV-2, acumulación y sessionStorage
        │   ├── FloatingCartButton.test.tsx                   # Pruebas de INV-6, INV-7 (visibilidad contextual)
        │   ├── useDebouncedCartModifier.test.ts              # Pruebas de debounce 300ms, fallo de red y rollback (INV-9)
        │   └── DishDetailModal.test.tsx                      # Pruebas del selector numérico y cotas de stock (INV-3)
        └── profile/
            ├── PersonalDataForm.test.tsx                     # Pruebas de edad (14-120), género, domicilio (INV-5, INV-8)
            └── PhotoUploadAndRemoval.test.tsx                # Pruebas de límite 5MB (INV-4) y confirmación diferida
```

**Structure Decision**: Se mantiene la arquitectura por capas y paquetes por feature estándar del repositorio. En backend, se extiende el módulo existente `com.easyeat.profile` y se incorpora `com.easyeat.cart` para validación de inventario. En frontend, se incorporan componentes especializados dentro de `components/cart/` y utilidades matemáticas en `utils/currencyUtils.ts`.

---

## Decisiones Técnicas por Área Funcional

### D-001 — Gestión de Sesión Local y Aislamiento por Establecimiento
- **Decisión**: El carrito de compras se almacena en `sessionStorage` (clave `'easyeat_cart'`), indexado por el `tenantId` del restaurante activo obtenido de `TenantContext`. Si el comensal navega a un nuevo restaurante teniendo ítems de otro local, se dispara el modal de confirmación `EstablishmentSwitchModal` antes de purgar la sesión previa.
- **Fuente**: `specs/009-shopping-cart/spec.md` (FR-002, FR-004, A-003); `specs/008-diner-catalog-multi-tenant` (COM-1.5.1 CA 5); `easyeat/frontend/src/context/CartContext.tsx`.
- **Cubre**: COM-6.1.1 AC-1.1, FR-004.
- **Invariantes protegidas**: INV-1, INV-7.
- **Riesgo**: Pérdida de ítems seleccionados si el comensal cambia involuntariamente de restaurante.
- **Mitigación**: Diálogo explícito de advertencia previo al vaciado de la orden, requiriendo confirmación consciente del usuario.
- **Alternativa descartada**: Almacenar carritos de compras no confirmados en base de datos relacional. Descartada por saturación innecesaria de la BD y complejidad de recolección de carritos huérfanos.

### D-002 — Selector Numérico Previo en Vista de Detalle de Plato
- **Decisión**: Incorporar en `DishDetailModal.tsx` un selector numérico con cota inferior 1 y cota superior $\max(0, \text{stockRealDisponible} - \text{cantidadEnCarrito})$. Al pulsar "Agregar al Carrito", se despacha la cantidad seleccionada en una sola interacción mediante `addItem(dish, quantity)`, desplegando un toast emergente de confirmación de 3 segundos.
- **Fuente**: `specs/009-shopping-cart/spec.md` (Clarificación 1, FR-001, FR-002, FR-003); `DishDetailModal.tsx`.
- **Cubre**: COM-6.1.1 AC-1.1, AC-1.2, AC-1.3, AC-1.4, AC-1.5; FR-001, FR-002, FR-003; SC-001.
- **Invariantes protegidas**: INV-1, INV-3.
- **Riesgo**: Desincronización si el stock en cocina desciende mientras el modal está abierto.
- **Mitigación**: Verificación de stock previa a la adición y advertencia visual si la suma supera la disponibilidad remanente.
- **Alternativa descartada**: Botón de adición fija de 1 unidad sin selector numérico. Descartada porque fue rechazada expresamente en la clarificación del spec.

### D-003 — Botón Flotante Reactivo, Modal de Carrito y Precisión Decimal
- **Decisión**: Implementar `FloatingCartButton.tsx` en posición fija inferior derecha, cuya visibilidad está biunívocamente condicionada a $\text{totalCount} \ge 1 \land \text{isCatalogView}$. Todos los cálculos de subtotales y total se realizan mediante aritmética de punto fijo entera basada en centavos (`currencyUtils.ts`). Al pulsar el botón flotante, se despliega el modal interactivo `CartDrawer`.
- **Fuente**: `specs/009-shopping-cart/spec.md` (FR-005 a FR-010, INV-1, INV-2, INV-6, INV-7); `CartDrawer.tsx`.
- **Cubre**: COM-6.1.2 AC-2.1, AC-2.2, AC-2.3, AC-2.4; FR-005 a FR-010, FR-017; SC-002, SC-007, SC-009.
- **Invariantes protegidas**: INV-1, INV-2, INV-6, INV-7.
- **Riesgo**: Desviaciones de centavos por redondeo de punto flotante en sumas complejas (drift de IEEE 754).
- **Mitigación**: Función `toCents` y `fromCents` con redondeo estricto `Math.round((amount + Number.EPSILON) * 100)` sobre números enteros de centavos.
- **Alternativa descartada**: Visibilidad del botón flotante en todas las pantallas incluyendo perfil. Descartada para evitar solapamientos con botones de guardado de formularios.

### D-004 — Modificación de Cantidades, Bloqueo de Stock y Reversión con Debounce
- **Decisión**: En el modal del carrito, las pulsaciones de `+` y `-` aplican cambios optimistas inmediatos en la interfaz. Una ventana de amortiguación (*debouncing*) de 300 ms agrupa las peticiones hacia el backend. Si la petición falla por error de red o stock insuficiente, el contador se revierte de forma determinista al último valor confirmado y se muestra una alerta visual. Restar sobre cantidad 1 elimina el producto completamente.
- **Fuente**: `specs/009-shopping-cart/spec.md` (FR-011 a FR-016, INV-3, INV-9); `CartItemWithBadge.tsx`.
- **Cubre**: COM-6.1.3 AC-3.1, AC-3.2, AC-3.3, AC-3.4, AC-3.5; FR-011 a FR-016; SC-003, SC-004.
- **Invariantes protegidas**: INV-1, INV-3, INV-6, INV-9.
- **Riesgo**: Inconsistencias temporales durante ráfagas de clicks rápidos en conexiones de alta latencia.
- **Mitigación**: Hook `useDebouncedCartModifier` que retiene `lastConfirmedQuantity` y cancela temporizadores previos antes de emitir la sincronización definitiva.
- **Alternativa descartada**: Sincronización bloqueante por cada click individual. Descartada por provocar parpadeos y congelamiento de UI.

### D-005 — Datos Demográficos, Validación y Almacenamiento de Avatar BYTEA
- **Decisión**: Extender la tabla `users` con columnas `age` (CHECK $14 \le \text{age} \le 120$), `gender` (CHECK clasificación cerrada) y `address` (CHECK $\le 255$ caracteres, tratado estrictamente como dato de contacto sin ruteo). Crear la tabla `user_photos` para almacenamiento binario `BYTEA` de fotos $\le 5$ MB validadas por magic bytes (JPEG/PNG/WEBP). La eliminación de foto cuenta con diálogo de confirmación y surte efecto diferido únicamente al pulsar "Guardar cambios", actualizando el avatar en `WorkspaceLayout.tsx`.
- **Fuente**: `specs/009-shopping-cart/spec.md` (FR-018 a FR-032, INV-4, INV-5, INV-8); `DishPhotoService.java`; migración `V7`.
- **Cubre**: COM-1.4.2 AC-4.1 a AC-4.6; FR-018 a FR-032; SC-005, SC-006, SC-008.
- **Invariantes protegidas**: INV-4, INV-5, INV-8.
- **Riesgo**: Carga de archivos maliciosos renombrados con extensiones de imagen falsas o saturación de base de datos.
- **Mitigación**: Validación binaria por magic bytes en backend, límite duro de 5.242.880 bytes en Spring Boot y constraint `CHECK` en PostgreSQL.
- **Alternativa descartada**: Eliminación inmediata de la foto al confirmar el modal. Descartada porque viola el requisito de confirmación diferida de la especificación.

---

## Modelo de Datos Conceptual

### Resumen de Entidades
1. **Carrito de Compras (`ShoppingCart`)**: Contenedor en sesión local (`sessionStorage`). Atributos: `tenantId`, `items`, `totalCount`, `totalAmount`, `isFloatingButtonVisible`.
2. **Ítem del Carrito (`CartItem`)**: Línea de producto. Atributos: `dishId`, `name`, `price`, `quantity`, `subtotal`, `availableStock`, flags dietarios.
3. **Plato / Referencia (`DishCatalogReference`)**: Referencia de solo lectura del catálogo activo (`dishes`, `retail_price`, disponibilidad en cocina).
4. **Perfil Demográfico (`DinerDemographics`)**: Extensión de `users` en BD (`age`, `gender`, `address`).
5. **Foto de Perfil / Avatar (`UserPhoto`)**: Almacenamiento relacional `BYTEA` en tabla `user_photos` (`user_id`, `data`, `content_type`, `file_size`, `file_name`).

*(Para el detalle exhaustivo de tipos, restricciones, ciclo de vida y script DDL de migración `V10`, consultar [data-model.md](./data-model.md)).*

---

## Contratos de Comportamiento

Las operaciones expuestas al frontend y sus contratos formales de entrada/salida son:
1. `AddToCartFromDishDetail`: Adición al carrito con selector de cantidad previa y notificación de éxito.
2. `ModifyCartItemQuantity`: Modificación de cantidades en modal, bloqueo de stock, eliminación total en 1 y sincronización diferida con rollback.
3. `UpdateDinerDemographics` (`PUT /api/v1/profile/demographics`): Actualización de edad, género y domicilio de contacto.
4. `UploadProfileAvatar` (`POST /api/v1/profile/avatar`): Carga multipart/form-data de avatar $\le 5$ MB y reflejo en navbar.
5. `RemoveProfileAvatar` (`DELETE /api/v1/profile/avatar`): Eliminación diferida de avatar personalizado y vuelta al default.
6. `ValidateCartStock` (`POST /api/v1/cart/validate-stock`): Validación masiva de disponibilidad de stock para el debounce.

*(Para el detalle de esquemas OpenAPI 3.0.3, consultar [contracts/openapi.yaml](./contracts/openapi.yaml); para los contratos semánticos por operación, consultar [contracts/behavioral-contracts.md](./contracts/behavioral-contracts.md)).*

---

## Estrategia de Testing Integral

Siguiendo el Principio III de la Constitución (Verificación por Capas Proporcional al Riesgo):

### 1. Pruebas Unitarias de Invariantes Matemáticas y de Negocio
- `currencyUtils.test.ts`: Verificación de aritmética decimal exacta en centavos enteros (INV-2), evaluando sumas de valores fraccionarios (ej. \$12,33 × 3 = \$36,99; \$9,99 + \$14,50 = \$24,49) sin drift de punto flotante.
- `CartContext.test.tsx`: Verificación de cálculo estricto de cantidad total $\sum q_i$ (INV-1), acumulación de platos idénticos sin duplicar filas (AC-1.2), y eliminación total al decrementar cantidad 1 (INV-6, AC-3.4).
- `UserPhotoServiceTest.java`: Verificación de validación de tamaño de archivo (INV-4): aceptación de archivo en límite exacto de 5.242.880 bytes y rechazo en 5.242.881 bytes. Verificación de rechazo de formatos no admitidos (PDF, SVG, GIF) y archivos vacíos.
- `DemographicsValidationTest.java`: Verificación de límites de edad (INV-5): aceptación de 14 y 120 años; rechazo de 13, 121, negativos, decimales y caracteres alfanuméricos.

### 2. Pruebas de Integración Backend (JUnit 5 + MockMvc + Testcontainers)
- `ProfileControllerTest.java`: Verificación de endpoints `GET /api/v1/profile/me`, `PUT /api/v1/profile/demographics`, `POST /api/v1/profile/avatar` y `DELETE /api/v1/profile/avatar` bajo autenticación con rol `COMENSAL`. Respuestas 401 para solicitudes anónimas.
- `FlywayMigrationV10Test.java`: Verificación de ejecución limpia de la migración `V10__extend_user_demographics_and_avatar.sql` sobre PostgreSQL 16 y activación efectiva de los constraints `CHECK`.
- `CartValidationIntegrationTest.java`: Verificación del endpoint de validación de stock frente a recetas de insumos reales.

### 3. Pruebas de Componentes y Estado Frontend (Vitest + React Testing Library)
- `FloatingCartButton.test.tsx`: Verificación de visibilidad biunívoca (INV-6, INV-7): renderizado exclusivo cuando $\text{totalCount} \ge 1$ y ocultamiento instantáneo al vaciar el carrito o cambiar a pantalla de perfil.
- `DishDetailModal.test.tsx`: Verificación del selector numérico (FR-001, FR-002): cotas entre 1 y stock disponible, bloqueo de adición si el plato está agotado (`isAvailable = false`) o el carrito ya contiene el stock máximo (INV-3).
- `useDebouncedCartModifier.test.ts`: Verificación de la ventana de debounce de 300 ms, actualización optimista inmediata, simulación de error de red y verificación de rollback al valor confirmado previo (INV-9).
- `PersonalDataForm.test.tsx`: Verificación del formulario demográfico, validación visual en línea de edad y confirmación diferida de eliminación de foto (FR-024, FR-025).
- `WorkspaceLayout.test.tsx`: Verificación del reflejo dinámico del avatar de usuario en la barra de navegación superior y renderizado del avatar genérico predeterminado en ausencia de foto.

---

## Manejo de Errores

Todos los errores generados por la capa de backend se estructuran conforme a la especificación estándar RFC 7807 `ProblemDetail` estandarizada en el repositorio (`GlobalExceptionHandler.java`):

```json
{
  "type": "https://easyeat.com/errors/validation-error",
  "title": "Bad Request",
  "status": 400,
  "detail": "La edad debe ser un número entero entre 14 y 120 años.",
  "instance": "/api/v1/profile/demographics",
  "errors": [
    {
      "field": "age",
      "message": "La edad debe ser un número entero entre 14 y 120 años."
    }
  ]
}
```

- **Mapeo de Errores de Validación en Frontend**: El formulario demográfico `PersonalDataForm.tsx` intercepta los campos listados en `errors` para marcar visualmente el campo en rojo y renderizar el mensaje de error inmediatamente debajo del control respectivo.
- **Errores de Imagen**: Mensajes específicos para archivo mayor a 5 MB (`"El archivo supera el tamaño máximo permitido de 5 MB."`) y formato no permitido (`"Formato no soportado. Debe ser JPEG, PNG o WebP."`).
- **Errores de Red en Carrito**: Notificación flotante de advertencia (`"Error de conexión: se restablecieron las cantidades de {plato.name}"`) disparada automáticamente tras el rollback del debounce.

---

## Cumplimiento de Invariantes de Negocio

| Invariante | Descripción Funcional | Enforce en | Mecanismo de Enforce | Test que la Verifica | AC Relacionado |
|---|---|---|---|---|---|
| **INV-1** | Cantidad total del carrito = $\sum q_i$ | Frontend | Reducción aritmética estricta sobre ítems en `CartContext` | `CartContextTest.testTotalCountCalculation` | AC-1.1, AC-2.1 |
| **INV-2** | Subtotales y total exactos con 2 decimales | Frontend | Aritmética de punto fijo en centavos enteros (`toCents`/`fromCents`) | `currencyUtilsTest.testExactMonetaryPrecision` | AC-2.1, AC-2.3 |
| **INV-3** | Cantidad unitaria $\le$ stock real en cocina | Ambas | Deshabilitación visual de control `+` y validación en `/cart/validate-stock` | `DishDetailModalTest.testMaxStockBoundary` | AC-1.3, AC-3.3 |
| **INV-4** | $0 < \text{tamaño\_foto} \le 5.242.880\text{ bytes}$ | Ambas | Validación de `File.size` en cliente y `chk_user_photos_file_size` en BD | `UserPhotoServiceTest.testFileSizeBoundaries` | AC-4.1, AC-4.2 |
| **INV-5** | Edad entera $14 \le \text{edad} \le 120$ | Ambas | Validación regex/rango en cliente y `chk_users_age_range` en BD | `DemographicsValidationTest.testAgeRange` | AC-4.4, AC-4.5 |
| **INV-6** | Carrito vacío $\iff$ botón flotante oculto | Frontend | Renderizado condicional estricto `totalCount > 0` | `FloatingCartButtonTest.testHiddenWhenEmpty` | AC-2.2, AC-3.4 |
| **INV-7** | Carrito $\ge 1$ y en catálogo $\iff$ botón visible | Frontend | Renderizado condicional `totalCount >= 1 && isCatalogView` | `FloatingCartButtonTest.testVisibleWhenHasItems` | AC-2.1, AC-2.4 |
| **INV-8** | Domicilio aislado como contacto sin ruteo | Ambas | Almacenamiento de texto plano sin dependencias logísticas | `ProfileServiceTest.testAddressIsolatedStorage` | AC-4.6 |
| **INV-9** | Reversión determinista por desconexión en debounce | Frontend | Hook con `lastConfirmedQuantity` y rollback ante catch | `useDebouncedModifierTest.testRollbackOnFailure`| AC-3.5 |

---

## Casos Borde Abordados

1. **Mismo plato agregado reiteradamente**: Acumula $+m$ a la cantidad existente en la misma fila; no duplica filas en el carrito (AC-1.2, FR-003).
2. **Cotas del selector numérico en detalle**: Bloqueado entre 1 y la disponibilidad máxima remanente.
3. **Alcanzar stock exacto y pulsar suma adicional**: Control `+` se deshabilita visualmente; pulsaciones adicionales no modifican la cantidad ni los importes (AC-3.3, INV-3).
4. **Reducción concurrente de stock en cocina**: El plato se preserva en el carrito con distintivo visible de "Stock insuficiente", deshabilitando la suma y bloqueando el avance a la orden (FR-014a).
5. **Restar sobre cantidad 1**: Elimina la fila del ítem por completo sin dejar cantidades en 0 ni números negativos (AC-3.4, FR-015).
6. **Vaciado completo del carrito desde el modal**: Al eliminar el último ítem, el modal refleja estado vacío y el botón flotante exterior desaparece inmediatamente (INV-6, SC-009).
7. **Modificaciones rápidas sucesivas y corte de red**: El debounce de 300 ms consolida las pulsaciones; ante desconexión o HTTP 5xx, el contador se revierte de inmediato al último valor confirmado y se notifica al usuario (INV-9, SC-004).
8. **Precisión en centavos y sumas con decimales**: Aritmética de punto fijo en centavos previene desfasajes de coma flotante (INV-2, SC-007).
9. **Plato con stock inicial igual a 0**: Botón "Agregar al Carrito" deshabilitado con etiqueta "Agotado" (AC-1.3, FR-001).
10. **Archivo de imagen en límite exacto de 5,0 MB**: Archivo de exactamente 5.242.880 bytes es aceptado; archivo de 5.242.881 bytes es rechazado (INV-4, SC-005).
11. **Archivo con formato MIME no admitido**: Archivos como `.pdf`, `.gif`, `.svg` o `.exe` son interceptados y rechazados indicando los tipos soportados (JPEG, PNG, WEBP) (AC-4.3, FR-020).
12. **Edad en límites de coherencia biológica**: 14 y 120 años son aceptados; 13 y 121 años son rechazados (AC-4.4, INV-5, SC-006).
13. **Eliminación de foto sin foto previa configurada**: La opción de eliminar foto se mantiene deshabilitada si el usuario solo posee el avatar por defecto.
14. **Domicilio en texto libre**: Admite hasta 255 caracteres sin requerir normalización postal externa ni geocodificación (INV-8, FR-029).
15. **Persistencia tras cerrar modal de carrito**: Cerrar el drawer o modal conserva íntegramente los productos y cantidades en la sesión local.

---

## Matriz de Trazabilidad Obligatoria

| Criterio de Aceptación (AC) | Requerimientos Funcionales | Invariantes | Decisión Técnica / Artefacto | Prueba Verificable |
|---|---|---|---|---|
| **COM-6.1.1 AC-1.1** | FR-001, FR-002 | INV-1, INV-7 | D-002, `DishDetailModal.tsx` | `DishDetailModalTest.testQuantitySelectorAndAdd` |
| **COM-6.1.1 AC-1.2** | FR-003 | INV-1, INV-3 | D-001, D-002, `CartContext.tsx` | `CartContextTest.testAccumulateExistingDish` |
| **COM-6.1.1 AC-1.3** | FR-001 | INV-3 | D-002, `DishDetailModal.tsx` | `DishDetailModalTest.testOutOfStockDisabled` |
| **COM-6.1.1 AC-1.4** | FR-001, FR-003 | INV-3 | D-002, `DishDetailModal.tsx` | `DishDetailModalTest.testMaxStockReachedWarning` |
| **COM-6.1.1 AC-1.5** | FR-002 | — | D-002, `AddToCartRiskModal.tsx` | `DishDetailModalTest.testDietaryConflictConfirmation` |
| **COM-6.1.2 AC-2.1** | FR-005, FR-006 | INV-1, INV-2, INV-7 | D-003, `FloatingCartButton.tsx` | `FloatingCartButtonTest.testDisplaysTotals` |
| **COM-6.1.2 AC-2.2** | FR-008 | INV-6 | D-003, `FloatingCartButton.tsx` | `FloatingCartButtonTest.testHiddenWhenCartEmpty` |
| **COM-6.1.2 AC-2.3** | FR-009, FR-010 | INV-2 | D-003, `CartDrawer.tsx` | `CartDrawerTest.testDisplaysDetailedBreakdown` |
| **COM-6.1.2 AC-2.4** | FR-007 | INV-1, INV-2 | D-003, `CartContext.tsx` | `CartContextTest.testRealtimeTotalsUpdate` |
| **COM-6.1.3 AC-3.1** | FR-011, FR-012, FR-013 | INV-1, INV-2 | D-004, `CartDrawer.tsx` | `CartDrawerTest.testUnitaryIncrementDecrement` |
| **COM-6.1.3 AC-3.2** | FR-014, FR-014a | INV-3 | D-004, `CartItemWithBadge.tsx`| `CartItemTest.testStockLimitReachedDisabled` |
| **COM-6.1.3 AC-3.3** | FR-015 | INV-6 | D-004, `CartDrawer.tsx` | `CartDrawerTest.testRemoveOnLastUnit` |
| **COM-6.1.3 AC-3.4** | FR-016 | INV-9 | D-004, `useDebouncedCartModifier` | `useDebouncedModifierTest.testRollbackOnFailure` |
| **COM-1.4.2 AC-4.1** | FR-018, FR-023 | INV-4 | D-005, `WorkspaceLayout.tsx` | `WorkspaceLayoutTest.testAvatarReflectedImmediately`|
| **COM-1.4.2 AC-4.2** | FR-021, FR-022 | INV-4 | D-005, `PersonalDataForm.tsx` | `PhotoUploadTest.testRejectOver5MB` |
| **COM-1.4.2 AC-4.3** | FR-019, FR-020 | INV-4 | D-005, `UserPhotoService.java`| `UserPhotoServiceTest.testRejectInvalidMimeType` |
| **COM-1.4.2 AC-4.4** | FR-026, FR-027 | INV-5 | D-005, `PersonalDataForm.tsx` | `DemographicsValidationTest.testAgeInvalidRange` |
| **COM-1.4.2 AC-4.5** | FR-024, FR-025 | — | D-005, `PhotoDeleteConfirmModal` | `PhotoRemovalTest.testDeferredConfirmationFlow` |
| **COM-1.4.2 AC-4.6** | FR-029, FR-030 | INV-8 | D-005, `ProfileController.java` | `ProfileControllerTest.testSaveContactAddress` |

*Cobertura Total*: 15/15 Criterios de Aceptación (con escenarios 1.5, 3.5 y 4.5 desglosados = 19 escenarios cubiertos) + 9/9 Invariantes de Negocio + 32/32 Requerimientos Funcionales.

---

## Assumptions

- `[SUPUESTO-001]`: El comensal se encuentra autenticado con rol `COMENSAL` y cuenta con un `tenantId` activo seleccionado en el contexto de navegación (garantizado por Spec 001, Spec 002 y Spec 008).
- `[SUPUESTO-002]`: El almacenamiento en sesión local del navegador (`sessionStorage`) dispone de espacio suficiente para estructuras JSON de carrito habituales (< 50 KB).
- `[SUPUESTO-003]`: Los avatares de perfil utilizan los tipos MIME web universales (`image/jpeg`, `image/png`, `image/webp`). Otros formatos como SVG se descartan deliberadamente por riesgo de vectores de inyección XSS.
- `[SUPUESTO-004]`: El domicilio de contacto no requiere validación de nomenclatura catastral ni verificación de código postal en esta etapa, admitiéndose como texto descriptivo de hasta 255 caracteres.
- `[SUPUESTO-005]`: El avatar genérico institucional por defecto se sirve como un recurso SVG estático local en el frontend sin requerir persistencia en la base de datos para usuarios sin foto.

---

## Out of Scope (Heredado del Spec)

- **Pasarela de pagos y procesamiento de cobro**: Autorización de tarjetas de crédito/débito y confirmación de transferencias (diferido a Sprint 4).
- **Checkout formal de la orden**: Generación de números de comanda y despacho a cocina KDS (diferido a Sprint 4).
- **Cálculo de rutas de envío o logística de delivery**: Cálculo de kilometraje, geocodificación de direcciones o integración con repartidores externos.
- **Autenticación, registro y recuperación de contraseñas**: Mecanismos de login y signup (cubiertos por specs 001 y 002).
- **Gestión de datos de contacto base y preferencias dietarias**: Edición de nombre, apellido, teléfono y restricciones alérgenas (cubiertos por spec 003).
- **Búsqueda, filtros y navegación de catálogo**: Búsqueda por texto y categorías gastronómicas (cubiertos por spec 008).
- **Historial de pedidos y compras pasadas**.
- **Cupones, promociones y descuentos de marketing**.
- **Sincronización multi-dispositivo en tiempo real del carrito**.
- **Soporte multi-moneda**: La plataforma opera exclusivamente en la moneda de curso legal configurada para el restaurante.

---

## Riesgos y Mitigaciones

| Riesgo Identificado | Impacto | Probabilidad | Estrategia de Mitigación en el Plan |
|---|---|---|---|
| Modificaciones rápidas sucesivas de cantidad provocan race conditions con el stock | Alto | Media | Ventana de debounce de 300 ms en frontend con rollback determinista y cancelación de timers previos. |
| Errores de redondeo de punto flotante en cálculo de subtotales | Medio | Alta | Estandarización de aritmética de punto fijo en centavos enteros (`toCents`/`fromCents`). |
| Carga de archivos maliciosos renombrados con extensión .jpg o .png | Crítico | Baja | Validación estricta en servidor de magic bytes en el payload binario (`UserPhotoService.java`). |
| Pérdida de foto original si el usuario cierra el formulario tras confirmar eliminación provisional | Medio | Media | Confirmación diferida al formulario: el borrado efectivo en servidor solo se despacha al presionar "Guardar cambios". |
| Inconsistencia de stock al agregar desde catálogo mientras el stock desciende en cocina | Medio | Media | Verificación de inventario en tiempo real con cota superior dinámica en el selector de cantidad. |

---

## Phasing (Fases de Implementación de Alto Nivel)

### Fase 1: Persistencia Relacional y Endpoints de Perfil Demográfico y Avatar (Backend)
- **Alcance**: Migración Flyway `V10__extend_user_demographics_and_avatar.sql`, entidad `UserPhoto`, extensión de `User`, DTOs de entrada/salida demográficos, `UserPhotoService` con validación de magic bytes y tamaño $\le 5$ MB, y endpoints REST en `ProfileController`.
- **Criterio de Done Verificable**: Migración `V10` ejecutada exitosamente en PostgreSQL; tests de integración `ProfileControllerTest` y `UserPhotoServiceTest` aprobados al 100%.

### Fase 2: Formulario Demográfico y Actualización de Avatar en Navbar (Frontend)
- **Alcance**: Extensión de `profileApi.ts`, integración de campos de edad (14-120), género y domicilio en `PersonalDataForm.tsx`, componente modal `PhotoDeleteConfirmModal.tsx` con confirmación diferida, y actualización reactiva de avatar en `WorkspaceLayout.tsx`.
- **Criterio de Done Verificable**: Formulario demográfico guarda exitosamente en API; rechazo visual de edad inválida e imágenes > 5 MB; cambio inmediato de foto en barra superior.

### Fase 3: Selector Previo de Cantidad en Detalle de Plato (COM-6.1.1)
- **Alcance**: Modificación de `DishDetailModal.tsx` incorporando selector numérico (1 a stock disponible), ampliación de firma `addItem(dish, quantity)` en `CartContext.tsx` y notificación emergente de confirmación de éxito.
- **Criterio de Done Verificable**: Comensal añade cantidades arbitrarias válidas en una única acción; platos reiterados acumulan unidades sin duplicar filas; platos agotados se presentan deshabilitados.

### Fase 4: Botón Flotante Reactivo y Aritmética Monetaria Exacta (COM-6.1.2)
- **Alcance**: Utilidad `currencyUtils.ts` basada en centavos enteros, componente `FloatingCartButton.tsx` en `CatalogPage.tsx` con visibilidad condicional biunívoca, y apertura del drawer `CartDrawer.tsx` con detalle completo.
- **Criterio de Done Verificable**: Botón visible si y solo si $\text{totalCount} \ge 1$ en catálogo; cálculos monetarios exactos sin drift; ocultamiento instantáneo al vaciar el carrito.

### Fase 5: Modificación Interactiva, Bloqueo de Stock y Reversión por Falla (COM-6.1.3)
- **Alcance**: Controles de incremento/decremento en `CartDrawer.tsx`, bloqueo al alcanzar disponibilidad de cocina, hook `useDebouncedCartModifier.ts` (300 ms), endpoint `/api/v1/cart/validate-stock` y mecanismo de rollback ante pérdida de conexión.
- **Criterio de Done Verificable**: Modificación unitaria funcional; retiro total al restar en cantidad 1; reversión determinista comprobada al cortar la conexión de red.

---

## Resumen Ejecutivo de Cierre de Fase

El presente plan técnico define la arquitectura de implementación para la feature `009-shopping-cart` anclándose estrictamente en el stack verificado del repositorio (Java 21, Spring Boot 3.3.4, PostgreSQL 16 con Flyway, React 18, TypeScript, Tailwind CSS y Vitest). Las decisiones clave adoptadas garantizan precisión decimal monetaria exacta (INV-2), visibilidad biunívoca contextual del carrito (INV-6, INV-7), reversión determinista ante fallos de red con debounce de 300 ms (INV-9), confirmación diferida de eliminación de foto de perfil y almacenamiento seguro de avatares en PostgreSQL BYTEA (INV-4) y rango biológico estricto de edad (INV-5). No existen riesgos técnicos abiertos ni bloqueantes de constitución (Constitution Gate: 10/10 PASS). Se alcanza una cobertura total y exhaustiva de 15/15 Criterios de Aceptación, 9/9 Invariantes de Negocio y 32/32 Requerimientos Funcionales. Se confirma explícitamente que no se han generado tareas de implementación en esta fase, quedando el proyecto listo para la ejecución de `/speckit-tasks`.
