# Plan de Implementación Técnica: Gestión de Personal, RBAC e Inventario Inicial de Ingredientes

**Rama**: `004-staff-mgmt-rbac-inventory` | **Fecha**: 2026-10-20 | **Especificación**: [spec.md](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/004-staff-mgmt-rbac-inventory/spec.md)

**Entrada**: Especificación funcional de `/specs/004-staff-mgmt-rbac-inventory/spec.md` y acuerdos de clarificación del 2026-10-20.

---

## Resumen Ejecutivo

Esta característica extiende la plataforma multi-tenant EasyEat integrando la administración de nómina de empleados del local gastronómico (`ADM-3.1.1`), el control de acceso basado en roles con aislamiento estricto de tenant (`ADM-3.1.2`), el catálogo maestro de ingredientes con ciclo de vida operativo (`ADM-4.1.1`), los ajustes manuales de stock físico con auditoría inmutable (`ADM-4.1.2`) y la exploración paginada del catálogo para cocina (`CHF-2.1.1`).

El enfoque técnico reutiliza la arquitectura establecida en la iteración 001 (`central-auth-recovery`), desplegando servicios REST construidos sobre **Java 21** y **Spring Boot 3.3.4**, persistencia relacional en **PostgreSQL 16** con control de esquemas mediante **Flyway**, tokens **JWT** con claims de rol y establecimiento, bloqueo pesimista en base de datos para garantizar el invariante de stock no negativo ante concurrencia, y validación automatizada mediante **JUnit 5**, **Mockito** y **Testcontainers**.

---

## Contexto Técnico

**Lenguaje / Versión**: Java 21 (LTS)  
**Dependencias Principales**:
- Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`)
- JJWT 0.12.6 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`)
- Flyway Core + Flyway PostgreSQL Database Extension 10.x
- PostgreSQL JDBC Driver 42.7.x
- Lombok (para reducción de boilerplate en DTOs y entidades)

**Almacenamiento y Persistencia**:
- PostgreSQL 16 con tipos nativos `UUID` (`gen_random_uuid()`), `TIMESTAMPTZ` y `NUMERIC(12, 4)`.
- Bloqueo pesimista de escritura (`SELECT ... FOR UPDATE`) en ajustes de inventario.
- Tablas de auditoría inmutables (append-only) con reglas de denegación de `UPDATE` y `DELETE`.

**Estrategia de Pruebas**:
- JUnit 5 y Mockito para pruebas unitarias de servicios de dominio y validadores.
- Spring Security Test (`MockMvc`, `@WithMockUser`) para verificación de filtros de seguridad y autorización por roles.
- Testcontainers con contenedor PostgreSQL 16 para pruebas de integración reales con ejecución de migraciones Flyway y pruebas de concurrencia multihilo.

**Plataforma de Despliegue**: Contenedores Linux / Docker (producción); ejecución local en JVM para desarrollo.  
**Tipo de Proyecto**: Servicio web / REST API (módulo backend monolítico modular en monorepo).  

**Metas de Rendimiento**:
- Alta de personal y consulta de nómina: latencia p95 ≤ 150ms.
- Ajuste de stock bajo bloqueo pesimista: latencia p95 ≤ 100ms.
- Catálogo paginado del Chef (20 items indexados por tenant y nombre): latencia p95 ≤ 50ms.

**Restricciones de Dominio y Seguridad**:
- Claves temporales con hash BCrypt (costo 12) y visualización única en pantalla.
- Forzado de cambio de clave en primer login (`must_change_password = true`).
- Aislamiento multi-tenant: todas las consultas filtran obligatoriamente por `tenant_id`.
- Precisión numérica contable absoluta con `BigDecimal` y `NUMERIC(12, 4)`.
- Prevención de desadministración: rechazar operaciones que dejen al establecimiento sin Administrador.

**Alcance / Escala**: MVP - 4 roles formales (`ADMIN`, `CHEF`, `COUNTER_CLERK`, `DINER`), arquitectura multi-tenant con esquema compartido y discriminación por columna `tenant_id`.

---

## Verificación de la Constitución (Constitution Check)

*GATE: Evaluación pre-diseño y post-diseño de conformidad con `.specify/memory/constitution.md`.*

| Principio | Estado Pre-Diseño | Estado Post-Diseño | Evidencia y Justificación |
|---|---|---|---|
| **I. La especificación es la fuente de verdad** | ✅ PASS | ✅ PASS | El plan, modelo de datos y contratos OpenAPI derivan de forma estricta de FR-001 a FR-027. No se agregaron funcionalidades no solicitadas. |
| **II. Especificación antes de implementación** | ✅ PASS | ✅ PASS | Se completaron `spec.md`, clarificaciones, `research.md`, `data-model.md`, `contracts/openapi.yaml` y `quickstart.md` antes de generar código fuente. |
| **III. Verificación por capas y proporcional al riesgo** | ✅ PASS | ✅ PASS | Estrategia de pruebas definida con unit tests (Mockito), seguridad (MockMvc) e integración real (Testcontainers con concurrencia multihilo). |
| **IV. Diseño simple, cohesivo y justificado** | ✅ PASS | ✅ PASS | Paquetes modulares por contexto (`com.easyeat.staff`, `com.easyeat.inventory`). Bloqueo pesimista justificado para evitar race conditions en stock. |
| **V. Alcance acotado** | ✅ PASS | ✅ PASS | Sin adiciones fuera de alcance (no recetas N:M ni pasarelas de pago en esta iteración; corresponden a Sprints 2 y 4). |
| **VI. Deuda técnica controlada** | ✅ PASS | ✅ PASS | Todas las decisiones técnicas se encuentran documentadas en `research.md`. |
| **VII. Trazabilidad, consistencia y documentación** | ✅ PASS | ✅ PASS | Matriz de trazabilidad exhaustiva en `data-model.md` vinculando cada requisito a entidades y restricciones relacionales. |
| **VIII. Evidencia sobre suposición y escalamiento humano** | ✅ PASS | ✅ PASS | Se ejecutó sesión de clarificación interactiva resolviendo 5 ambigüedades clave antes del diseño técnico. |
| **IX. Ingeniería por sobre conveniencia** | ✅ PASS | ✅ PASS | Precisión decimal exacta (`BigDecimal` / `NUMERIC`), bloqueo pesimista en concurrencia y auditoría inmutable en vez de soluciones superficiales. |
| **X. Gobernanza agnóstica a la tecnología** | ✅ PASS | ✅ PASS | La arquitectura responde a principios de ingeniería sólidos respetando la continuidad del stack del proyecto. |

**RESULTADO DEL GATE: APROBADO (ALL PASS)**

---

## Estructura del Proyecto

### Documentación de la Característica (`specs/004-staff-mgmt-rbac-inventory/`)

```text
specs/004-staff-mgmt-rbac-inventory/
├── plan.md              # Este plan de implementación técnica
├── research.md          # Registro de investigación técnica y decisiones de diseño (Phase 0)
├── data-model.md        # Entidades, esquema SQL, transiciones de estado y matriz de trazabilidad (Phase 1)
├── quickstart.md        # Guía de validación y escenarios ejecutables de extremo a extremo (Phase 1)
├── contracts/
│   └── openapi.yaml     # Especificación formal de contratos de interfaz OpenAPI 3.0 (Phase 1)
└── checklists/
    └── requirements.md  # Checklist de calidad de requisitos
```

### Código Fuente en el Repositorio (`easyeat/backend/`)

```text
easyeat/backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/easyeat/
    │   │   ├── EasyEatApplication.java
    │   │   ├── auth/                                    # Infraestructura de Auth (Iteración 001)
    │   │   │   ├── controller/AuthController.java
    │   │   │   ├── dto/FirstLoginPasswordChangeRequest.java
    │   │   │   ├── entity/User.java
    │   │   │   └── security/JwtService.java
    │   │   ├── common/
    │   │   │   ├── context/TenantContextHolder.java     # Contexto ThreadLocal del tenant actual
    │   │   │   ├── filter/TenantContextFilter.java      # Extractor de tenant_id desde claims JWT
    │   │   │   └── audit/SecurityAuditLogger.java       # Emisor de logs estructurados de seguridad
    │   │   ├── staff/                                   # Módulo de Personal y RBAC (Iteración 004)
    │   │   │   ├── controller/StaffController.java      # Endpoints /api/v1/admin/staff/**
    │   │   │   ├── dto/StaffRegistrationRequest.java
    │   │   │   ├── dto/StaffRegistrationResponse.java
    │   │   │   ├── dto/StaffUpdateRequest.java
    │   │   │   ├── dto/StaffMemberResponse.java
    │   │   │   ├── service/StaffService.java            # Alta, edición, credencial temporal, huérfano check
    │   │   │   └── repository/StaffUserRepository.java
    │   │   ├── inventory/                               # Módulo de Catálogo e Inventario (Iteración 004)
    │   │   │   ├── controller/AdminIngredientController.java # Endpoints /api/v1/admin/ingredients/**
    │   │   │   ├── controller/ChefCatalogController.java     # Endpoints /api/v1/chef/ingredients
    │   │   │   ├── dto/CreateIngredientRequest.java
    │   │   │   ├── dto/UpdateIngredientRequest.java
    │   │   │   ├── dto/AdminIngredientDetailResponse.java
    │   │   │   ├── dto/StockAdjustmentRequest.java
    │   │   │   ├── dto/StockAdjustmentResponse.java
    │   │   │   ├── dto/StockAuditEntryResponse.java
    │   │   │   ├── dto/ChefIngredientPageResponse.java
    │   │   │   ├── entity/Ingredient.java
    │   │   │   ├── entity/StockAuditEntry.java
    │   │   │   ├── repository/IngredientRepository.java      # Consulta con bloqueo pesimista
    │   │   │   ├── repository/StockAuditEntryRepository.java # Repositorio append-only
    │   │   │   ├── service/IngredientService.java            # CRUD y ciclo de vida de insumos
    │   │   │   └── service/StockAdjustmentService.java       # Ajuste atómico de inventario y auditoría
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java                  # Configuración de roles y WebSecurityCustomizer
    │   │   │   └── MethodSecurityConfig.java            # Habilitación de @EnableMethodSecurity
    │   │   └── exception/
    │   │       ├── GlobalExceptionHandler.java          # Manejador RFC 9457 ProblemDetail
    │   │       ├── NegativeStockException.java
    │   │       ├── OrphanedAdminException.java
    │   │       └── TenantViolationException.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           ├── V1__create_users.sql                 # Base de usuarios (001)
    │           ├── V2__create_password_reset_tokens.sql # Recuperación de claves (001)
    │           ├── V3__extend_users_staff.sql           # Personal de establecimiento (004)
    │           └── V4__create_ingredients_and_stock_audit.sql # Catálogo y auditoría de stock (004)
    └── test/
        ├── java/com/easyeat/
        │   ├── staff/
        │   │   ├── controller/StaffControllerTest.java  # Pruebas MockMvc y RBAC de personal
        │   │   └── service/StaffServiceTest.java        # Pruebas unitarias de alta y admin huérfano
        │   ├── inventory/
        │   │   ├── controller/IngredientControllerTest.java # Pruebas MockMvc de catálogo y Chef
        │   │   ├── service/StockAdjustmentServiceTest.java  # Pruebas unitarias de invariantes de stock
        │   │   └── concurrency/StockAdjustmentConcurrencyTest.java # Prueba multihilo con Testcontainers
        │   └── security/
        │       └── TenantIsolationIntegrationTest.java  # Validación estricta de aislamiento de tenant
        └── resources/
            └── application-test.yml
```

**Decisión de Estructura**: Arquitectura modular orientada a dominios (`staff`, `inventory`, `auth`) dentro de una única aplicación backend monolítica ejecutable (`easyeat/backend/`), manteniendo cohesión vertical y simplificando la observabilidad y el despliegue.

---

## Control de Complejidad

> **Nota**: No se detectaron violaciones ni desvíos respecto a los principios de la Constitución del Proyecto.
> Todos los componentes introducidos (bloqueo pesimista, filtro de tenant, DTOs de proyección) responden de manera directa y justificada a los requerimientos funcionales FR-001 a FR-027.

| Componente / Patrón | ¿Por qué es necesario? | Alternativa más simple rechazada y motivo |
|---|---|---|
| Bloqueo pesimista (`SELECT ... FOR UPDATE`) | Garantiza de manera atómica que el stock resultante no sea negativo (`>= 0`) ante ajustes concurrentes. | Bloqueo optimista (`@Version`): rechazaría transacciones concurrentes con excepciones de concurrencia obligando a reintentos confusos para el personal de depósito. |
| Tabla inmutable `stock_audit_entries` | Requisito explícito de trazabilidad y auditoría contable inalterable (FR-019). | Almacenar solo el balance actual en `ingredients`: perdería por completo el motivo, usuario y delta del ajuste. |
| Filtro y contexto de tenant (`ThreadLocal`) | Garantiza que ninguna consulta exponga o modifique datos de un local gastronómico ajeno. | Parámetro manual `tenant_id` en cada controlador: susceptible a omisiones humanas que generen fugas de seguridad. |
| Proyección exclusiva para Chef | Protege información comercial sensible (costos unitarios) conforme al principio de mínimo privilegio. | Reutilizar el DTO del Administrador: filtraría datos financieros al cliente web de la cocina. |
