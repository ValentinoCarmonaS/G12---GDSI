# Plan de implementacion: control de acceso de personal, salud financiera y ranking de platos

**Rama**: `023-staff-access-bi-dish-ranking` | **Fecha**: 20/11/2026 | **Especificacion**: [spec.md](./spec.md)

---

## 1. Resumen ejecutivo

El presente plan formaliza la implementacion tecnica incremental de la especificacion `023-staff-access-bi-dish-ranking`. Cubre tres capacidades funcionales independientes pero coordinadas para el rol Administrador:
1. **Control de acceso del personal (Historia 3.1.3)**: Endpoint para suspender o inactivar empleados, bloqueo generico e indistinguible de login para cuentas no activas (HTTP 401) con proteccion contra temporizacion, y proteccion atomica contra orfandad administrativa mediante bloqueo pesimista en base de datos.
2. **Panel de salud financiera (Historia 6.1.1)**: Consulta analitica de ingresos totales y pedidos concretados (`PAID`, `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`) por granularidad relativa (dia, semana calendario ISO 8601, mes calendario), anclada al instante de acreditacion de pago `orders.paid_at` y delimitada por el huso horario del establecimiento.
3. **Ranking de platos mas vendidos (Historia 6.2.1)**: Reporte analitico de platos ordenado descendentemente por unidades vendidas con desempate alfabetico A-Z determinista, acumulado global historico por defecto o acotado al periodo seleccionado, excluyendo platos sin ventas.

Las tres historias se implementan de forma desacoplada respetando la arquitectura existente de Spring Boot 3 y React 18, garantizando que ninguna altere el comportamiento de modulos operativos previos (KDS, mostrador, checkout o catalogo).

---

## 2. Contexto tecnico

* **Lenguaje y version**: Java 21 (LTS) en backend; TypeScript 5.6.3 en frontend.
* **Dependencias principales**:
  - Backend: Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`), Flyway Core 10+, PostgreSQL JDBC Driver, JJWT 0.12.6.
  - Frontend: React 18.3.1, React Router DOM 6.27.0, Axios 1.7.7, TailwindCSS 3.4.13, Lucide React 0.453.0, Recharts 2.13.0 (visualizacion grafica interactiva de metricas financieras).
* **Persistencia y base de datos**: PostgreSQL 15+. Migracion Flyway `V29__add_paid_at_to_orders.sql` para incorporar `paid_at TIMESTAMPTZ NULL` con indice parcial `idx_orders_analytics` sobre `orders(establishment_id, paid_at) WHERE status IN ('PAID', 'IN_PROGRESS', 'READY_FOR_PICKUP', 'COMPLETED')`.
* **Testing**:
  - Backend: JUnit 5, Mockito, Spring Security Test, Testcontainers PostgreSQL 1.21.4.
  - Frontend: Vitest 2.1.3, React Testing Library 16.0.1, `@testing-library/user-event`.
* **Plataforma objetivo**: Despliegue en contenedor Docker (JVM Linux) y navegadores web modernos de escritorio.
* **Tipo de proyecto**: Aplicacion web full-stack (Backend REST API multi-tenant + Frontend SPA modular).
* **Objetivos de rendimiento**:
  - Conmutacion de granularidades en menos de 2 segundos (SC-003).
  - Consultas analiticas de lectura en base de datos resueltas en menos de 50 milisegundos mediante escaneo de indice parcial.
* **Restricciones tecnicas**:
  - Rechazo de login en cuentas suspendidas 100% indistinguible de credenciales invalidas (HTTP 401) con evaluacion post-BCrypt.
  - Proteccion de orfandad administrativa atomica mediante bloqueo pesimista de escritura (`@Lock(LockModeType.PESSIMISTIC_WRITE)`).
  - Inmutabilidad estricta de auditoria historica (sin cascadas que borren o muten comandas, pedidos o platos de empleados dados de baja).
* **Escala y alcance**:
  - Aislamiento estricto multi-tenant por `tenantId` / `establishmentId`.
  - Alcance acotado: sin liquidaciones salariales, sin contabilidad general y sin revocacion forzada de sesiones preexistentes.

---

## 3. Verificacion de la Constitucion (Constitution Check)

*GATE: Evaluacion obligatoria contra la Constitucion del Proyecto (v2.0.0).*

| Principio Constitucional | Estado | Evidencia y cumplimiento |
| :--- | :--- | :--- |
| **I. La especificacion es la fuente de verdad** | CUMPLE | Todas las decisiones tecnicas derivan directamente de los requisitos funcionales (FR-001 a FR-017) y criterios de aceptacion del spec.md aprobado. |
| **II. Especificacion antes de implementacion** | CUMPLE | El plan y los artefactos de diseno se completan antes de descomponer tareas o escribir codigo productivo. |
| **III. Verificacion estratificada proporcional al riesgo** | CUMPLE | Se definen escenarios de prueba unitarios, de integracion con base de datos real (Testcontainers) y de extremo a extremo documentados en `quickstart.md`. |
| **IV. Diseno simple, cohesivo y justificado** | CUMPLE | Se introduce el paquete cohesivo `com.easyeat.analytics` para BI, separando consultas de lectura de la logica transaccional de pedidos. La incorporacion de Recharts se justifica directamente por el requerimiento FR-010 y la historia de usuario 6.1.1 para proveer graficos interactivos autointuitivos de ingresos y volumen con conmutacion reactiva de periodos. |
| **V. Alcance acotado** | CUMPLE | Se respetan estrictamente los limites fuera de alcance: sin modulos contables, sin deducciones salariales ni revocacion forzada de tokens vivos. |
| **VI. Deuda tecnica controlada** | CUMPLE | Se introduce `paid_at` como hito de ciclo de vida nativo en `orders`, evitando deuda tecnica de joins complejos con pasarelas externas. |
| **VII. Trazabilidad y consistencia** | CUMPLE | Mapeo 1:1 entre historias (3.1.3, 6.1.1, 6.2.1), decisiones de investigacion (D-01 a D-15), contratos y escenarios de validacion. |
| **VIII. Evidencia sobre suposicion y escalacion** | CUMPLE | Todas las dudas detectadas respecto a base de datos y flujos se inspeccionaron directamente en el repositorio y se consensuaron formalmente mediante `grill-me`. |
| **IX. Ingenieria sobre conveniencia** | CUMPLE | Se adopto bloqueo pesimista para concurrencia y evaluacion post-BCrypt para evitar ataques de temporizacion, priorizando la robustez sobre la rapidez. |
| **X. Integridad vertical full-stack** | CUMPLE | El plan especifica y disena tanto los contratos y servicios de backend como los componentes y vistas de frontend para cada una de las tres historias. |

---

## 4. Estructura del proyecto

### Documentacion tecnica del feature

```text
specs/023-staff-access-bi-dish-ranking/
├── spec.md                     # Especificacion funcional aprobada
├── plan.md                     # Este archivo (Plan tecnico de implementacion)
├── research.md                 # Decisiones de investigacion y alternativas
├── data-model.md               # Modelo de datos, entidades, invariantes y migracion
├── quickstart.md               # Guia de validacion reproducible extremo a extremo
├── contracts/                  # Contratos de interfaz API
│   ├── 01-staff-access-status.md
│   ├── 02-financial-health.md
│   └── 03-dish-ranking.md
└── checklists/                 # Checklists de requerimientos y calidad
```

### Modulos de codigo fuente afectados

```text
easyeat/
├── backend/
│   ├── src/main/java/com/easyeat/
│   │   ├── auth/
│   │   │   └── service/AuthService.java                         # Ajuste rechazo generico 401 post-BCrypt
│   │   ├── staff/
│   │   │   ├── controller/StaffController.java                  # Nuevo endpoint PATCH /status
│   │   │   ├── dto/StaffStatusUpdateRequest.java                # Nuevo DTO de actualizacion de estado
│   │   │   ├── repository/StaffUserRepository.java              # Query con @Lock PESSIMISTIC_WRITE
│   │   │   └── service/StaffService.java                        # Logica atomica de cambio de estado
│   │   ├── order/
│   │   │   ├── domain/Order.java                                # Atributo paidAt (Instant)
│   │   │   └── service/PaymentWebhookService.java               # Asignacion order.setPaidAt(now)
│   │   └── analytics/                                           # Nuevo paquete de BI / Analitica
│   │       ├── controller/AdminAnalyticsController.java
│   │       ├── dto/FinancialHealthResponse.java
│   │       ├── dto/DishRankingItemResponse.java
│   │       ├── repository/AnalyticsRepository.java
│   │       ├── service/FinancialHealthService.java
│   │       └── service/DishRankingService.java
│   └── src/main/resources/db/migration/
│       └── V29__add_paid_at_to_orders.sql                       # Migracion Flyway paid_at + indice
│
└── frontend/
    └── src/
        ├── api/
        │   ├── staffApi.ts                                      # Metodo updateStaffStatus
        │   └── analyticsApi.ts                                  # Metodos getFinancialHealth y getDishRanking
        ├── components/
        │   ├── staff/
        │   │   ├── StaffRosterTable.tsx                         # Acciones Suspender / Activar
        │   │   └── StaffStatusModal.tsx                         # Modal de confirmacion de suspension
        │   └── analytics/
        │       ├── FinancialHealthPanel.tsx                     # Tarjetas de metricas y selector dia/semana/mes
        │       ├── FinancialHealthChart.tsx                     # Grafico visual e interactivo de ingresos y pedidos (Recharts)
        ├── pages/
        │   ├── AdminAnalyticsPage.tsx                           # Pagina unificada /admin/analytics
        │   └── AdminWorkspacePage.tsx                           # Nueva tarjeta de navegacion a Metricas
        ├── router/
        │   └── AppRoutes.tsx                                    # Ruta /admin/analytics con RoleRouteGuard
        └── types/
            ├── staff.ts                                         # Extension de tipos si aplica
            └── analytics.ts                                     # Tipos FinancialHealthResponse, DishRankingItem
```

---

## 5. Registro de complejidad

No se introducen violaciones a los principios arquitectonicos ni abstracciones no justificadas. El diseno reutiliza las convenciones vigentes del repositorio (Spring Data JPA, PostgreSQL, React, Axios, TailwindCSS).