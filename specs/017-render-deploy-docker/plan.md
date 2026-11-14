# Implementation Plan: 017 Render Deploy Docker

**Branch**: `017-render-deploy-docker` | **Fecha**: 10/11/2026 | **Spec**: [spec.md](./spec.md)  
**Input**: Especificación funcional aprobada desde `/specs/017-render-deploy-docker/spec.md`  

---

## 1. Summary

Establecer la infraestructura de empaquetado, conectividad segura, configuración dinámica de red y monitoreo de salud para desplegar la plataforma integral EasyEat (backend Spring Boot y frontend React SPA) sobre los servicios de contenedores de Render conectados a una base de datos PostgreSQL gestionada en Neon con cifrado SSL obligatorio, garantizando:
1. **Contenedorización multi-etapa y memoria JVM del backend (`HU-01`)**: Dockerfile con compilación Maven JDK 21 y runtime Alpine JRE 21, usuario no root `easyeat`, flags `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` para no exceder los 512 MB de Render, y exclusión de código fuente mediante `.dockerignore` (**AS-1 a AS-3**, **FR-001 a FR-004**, **SC-001**, **SC-002**).
2. **CORS dinámico en Spring Security (`HU-02`)**: Bean `CorsConfigurationSource` con orígenes resueltos desde `APP_CORS_ALLOWED_ORIGINS`, sanitización automática (trim y remoción de barras finales), `allowCredentials = true`, `maxAge = 3600`, métodos permitidos universales y fallback a `http://localhost:3000` (**AS-4 a AS-8**, **FR-005 a FR-009**, **SC-003**).
3. **Puerto dinámico y health check (`HU-03`)**: Enlace a `${PORT:8080}`, dependencia `spring-boot-starter-actuator`, endpoint público `/actuator/health` en `SecurityConfig` respondiendo HTTP 200 `{"status":"UP"}` o 503 `{"status":"DOWN"}` (**AS-9 a AS-12**, **FR-010 a FR-013**, **SC-004**, **SC-010**).
4. **Neon PostgreSQL y migraciones completas (`HU-04`)**: Conexión directa JDBC sin pooler con `sslmode=require`, credenciales desde variables de entorno, HikariCP con `connection-timeout: 30000` tolerando el scale-to-zero de Neon, y ejecución íntegra de migraciones `V1` a `V19` y semillas `R__seed_dev_*` (**AS-13 a AS-16**, **FR-014 a FR-017**, **SC-005**).
5. **Contenedorización frontend y enrutamiento SPA (`HU-05`)**: Dockerfile multi-etapa Node 20 a Nginx Alpine con inyección de `ARG VITE_API_BASE_URL`, Nginx escuchando en `${PORT:-80}`, regla `try_files $uri $uri/ /index.html =404;` para eliminar errores 404 en recargas virtuales, y directivas de caché HTTP (`no-cache` para HTML y `immutable` para activos con hash) (**AS-17 a AS-20**, **FR-018 a FR-021**, **SC-006**).
6. **Sincronización de URLs públicas (`HU-06`)**: Configuración de `MERCADOPAGO_FRONTEND_BASE_URL` concatenando `/checkout/payment-return`, disponibilidad pública del webhook de pagos `/api/v1/payments/*/webhook` sin JWT, y enlaces de recuperación y verificación apuntando al dominio público del frontend (**AS-21 a AS-24**, **FR-022 a FR-024**, **SC-007 a SC-009**).

---

## 2. Technical Context

**Lenguaje(s) y versión(es)**:
- Backend: Java 21 (LTS).
- Frontend: TypeScript 5.6.3 / React 18.3.1.
- Contenedores: Dockerfile Alpine (Linux x86_64).

**Framework(s) y dependencias principales**:
- Backend: Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-mail`), Flyway Core + `flyway-database-postgresql`, PostgreSQL JDBC Driver (`org.postgresql:postgresql`), HikariCP (gestionado por Spring Boot), JJWT (`0.12.6`), Caffeine Cache, Jackson (`jackson-databind`).
- Nueva dependencia a incorporar en `pom.xml`: `org.springframework.boot:spring-boot-starter-actuator` (FR-011).
- Frontend: React 18.3.1, Vite 5.4.8, Tailwind CSS 3.4.13, React Router DOM 6.27.0, Axios 1.7.7, Lucide React 0.453.0.
- Servidor web frontend en producción: Nginx Alpine (`nginx:alpine`).

**Persistencia, base de datos y esquema**:
- Base de datos destino: PostgreSQL 15+ gestionada en Neon Cloud.
- Conexión: JDBC directa (sin `-pooler.neon.tech` / sin PgBouncer en modo transacción) con SSL mandatorio (`?sslmode=require`).
- Migraciones: Flyway con rutas `classpath:db/migration` y `classpath:db/dev`.
- Secuencia de ejecución: `V1` a `V19` correlativas + scripts repetibles `R__seed_dev_users.sql`, `R__seed_dev_establishments.sql`, `R__seed_dev_dishes_and_dietary.sql`. Cero migraciones nuevas añadidas en esta spec.

**Configuración actual y perfiles (`application.yml`)**:
- `application.yml` centraliza propiedades generales. Se actualiza para declarar:
  - `server.port: ${PORT:8080}` (FR-010).
  - `spring.datasource.url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/easyeat}`.
  - `spring.datasource.username: ${SPRING_DATASOURCE_USERNAME:easyeat}`.
  - `spring.datasource.password: ${SPRING_DATASOURCE_PASSWORD:easyeat_dev_password}`.
  - `spring.datasource.hikari.connection-timeout: 30000` (resiliencia scale-to-zero Neon Q11/A11).
  - `spring.flyway.enabled: true` con locations `classpath:db/migration` y `classpath:db/dev`.
  - `app.cors.allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000}`.
  - `management.endpoints.web.exposure.include: health`.
  - `management.endpoint.health.show-details: when-authorized`.

**Seguridad y cadena de filtros (`SecurityConfig.java`)**:
- `SecurityFilterChain` actual carece de bean `CorsConfigurationSource` y de soporte `.cors()`.
- Se incorpora bean `CorsConfigurationSource` con normalización sintáctica (trim y remoción de barras finales), `allowCredentials=true`, `maxAge=3600L` y métodos `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- Se registra `.cors(cors -> cors.configurationSource(corsConfigurationSource(allowedOriginsConfig)))`.
- Se añade acceso público sin JWT (`permitAll()`) en `authorizeHttpRequests`:
  - `/actuator/health` (FR-012).
  - `/api/v1/payments/*/webhook` y `/api/v1/payments/mercadopago/webhook` (FR-023).

**Mecanismo de correo y Mercado Pago (Specs 001, 014, 015, 016)**:
- Correo: `JavaMailSender` preconfigurado en `${app.mail.from}` consumiendo `APP_PASSWORD_RESET_BASE_URL` y `APP_EMAIL_VERIFICATION_BASE_URL`.
- Mercado Pago: `MercadoPagoProperties` mapea `mercadopago.frontend-base-url` desde `MERCADOPAGO_FRONTEND_BASE_URL`. `MercadoPagoGatewayAdapter` concatena `/checkout/payment-return`. No se alteran sus lógicas internas.

**Estado actual de artefactos de contenedor**:
- Inexistentes en el repositorio. Se crean desde cero:
  - `easyeat/backend/Dockerfile` y `easyeat/backend/.dockerignore`.
  - `easyeat/frontend/Dockerfile`, `easyeat/frontend/.dockerignore` y `easyeat/frontend/nginx.conf`.

**Estrategia de testing y verificación**:
- Tests automatizados existentes: suites completas de JUnit 5, Mockito, AssertJ, MockMvc y Testcontainers en backend; suites de Vitest y React Testing Library en frontend.
- Nuevos tests de regresión y configuración:
  - Unit tests de normalización y binding del bean CORS en Spring Security.
  - Tests de integración de endpoint público `/actuator/health` y `/api/v1/payments/mercadopago/webhook`.
- Verificación operativa: Guía de pruebas de humo y validación determinística documentada en `quickstart.md` cubriendo AS-1 a AS-24 contra contenedores locales y Render.

**Observabilidad**:
- Spring Boot Actuator (`/actuator/health`) como única superficie de telemetría añadida.

**Restricciones de plataforma y entorno**:
- RAM: 512 MB en capa gratuita de Render (heap limitado al 75% ~384 MB mediante `-XX:MaxRAMPercentage=75.0`).
- Cold start: suspensión por inactividad tras 15 minutos en Render (sonda de salud con tolerancia).
- Scale-to-zero: suspensión por inactividad tras 5 minutos en Neon (HikariCP connection timeout hasta 30 s).
- Alcance cerrado: sin pipelines CI/CD, sin IaC, sin migraciones de base de datos nuevas, sin cambios en reglas de negocio ni interfaces de usuario.

---

## 3. Constitution Check

*GATE: Evaluación exhaustiva frente a la Constitución SDD del proyecto (v2.0.0).*

| Principio | Estado | Evidencia y Justificación Técnica |
|---|---|---|
| **I. The Specification Is the Source of Truth** | **PASS** | El plan responde estrictamente a los 24 requisitos funcionales (FR-001 a FR-024), 24 escenarios de aceptación (AS-1 a AS-24), invariantes y 10 criterios de éxito (SC-001 a SC-010) de `spec.md`. No se reabren clarificaciones ya resueltas (Q1 a Q11). |
| **II. Specification Before Implementation** | **PASS** | La especificación funcional está aprobada. Este plan técnico y sus artefactos de diseño (`research.md`, `deployment-model.md`, `contracts/`, `quickstart.md`) se completan íntegramente antes de generar tareas o escribir código de empaquetado y configuración. |
| **III. Layered Verification Proportional to Risk** | **PASS** | Validación en capas: (1) Unit tests de sanitización sintáctica CORS; (2) Tests de integración MockMvc para `/actuator/health` y webhook público; (3) Tests de empaquetado Docker validando ausencia de código fuente, identidad de usuario y flags JVM; (4) Smoke tests operativos de enrutamiento SPA y caché Nginx documentados en `quickstart.md`. |
| **IV. Simple, Cohesive, and Justified Design** | **PASS** | Se aplica KISS: reutilización de imágenes estándar Alpine (`eclipse-temurin:21-jre-alpine`, `nginx:alpine`); uso del script nativo `envsubst` de Nginx para resolución de puerto; Spring Boot Actuator oficial sin controladores caseros; conexión directa a Neon sin dependencias adicionales de proxying. |
| **V. Bounded Scope** | **PASS** | Delimitación estricta a infraestructura y despliegue: cero CI/CD, cero Terraform/IaC, cero réplicas/clustering, cero cambios en reglas de negocio de specs 001 a 016, y cero migraciones de base de datos añadidas. |
| **VI. Controlled Technical Debt** | **PASS** | No se genera deuda técnica; se corrigen omisiones de configuración (`server.port: 8080` estático y ausencia de CORS en `SecurityConfig`), garantizando que el sistema sea desplegable y reproducible en cualquier entorno estándar OCI. |
| **VII. Traceability, Consistency, and Documentation** | **PASS** | Trazabilidad completa y bidireccional entre `spec.md`, `plan.md`, `research.md`, `deployment-model.md`, `contracts/` (8 contratos) y `quickstart.md`. Todo el diseño está redactado en español profesional con identificadores técnicos en inglés. |
| **VIII. Evidence Over Assumption & Escalation** | **PASS** | Toda decisión se sustenta en el código real del repositorio (`pom.xml`, `application.yml`, `SecurityConfig.java`, migraciones `V1` a `V19`, `package.json`, `vite.config.ts`, `apiClient.ts`) y en las clarificaciones de `spec.md`. Cero asunciones no respaldadas. |
| **IX. Engineering Over Convenience** | **PASS** | Gobernanza de memoria por cgroups (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`); usuario no root `easyeat` para seguridad en contenedor; conexión directa a Neon garantizando soporte de `pg_advisory_lock` en Flyway; política diferenciada de Cache-Control en Nginx. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | **PASS** | Vertical slice integral de despliegue: backend Spring Boot en contenedor + frontend React Nginx en contenedor + base de datos Neon PostgreSQL conectada + contratos de red (CORS, puertos, salud, SSL, URLs públicas) validados de extremo a extremo. |

---

## 4. Project Structure

### 4.1. Documentación (esta feature)

```text
specs/017-render-deploy-docker/
├── spec.md                         # Especificación funcional aprobada
├── plan.md                         # Este plan de implementación técnica
├── research.md                     # Registro de investigación y decisiones (Fase 0)
├── deployment-model.md             # Modelo de infraestructura, variables y topología (Fase 1)
├── data-model.md                   # Adaptación formal de modelo de despliegue (Fase 1)
├── quickstart.md                   # Guía de verificación operativa y smoke tests (Fase 1)
├── contracts/                      # Manifiestos y contratos operativos verificables (Fase 1)
│   ├── 01-backend-image-contract.md
│   ├── 02-frontend-image-contract.md
│   ├── 03-cors-contract.md
│   ├── 04-health-check-contract.md
│   ├── 05-neon-connection-contract.md
│   ├── 06-flyway-migrations-contract.md
│   ├── 07-spa-routing-contract.md
│   └── 08-public-urls-contract.md
└── checklists/
    ├── requirements.md
    └── deployment.md
```

### 4.2. Código Fuente y Configuración Afectados en el Repositorio

```text
easyeat/
├── backend/
│   ├── Dockerfile                                      # [NUEVO] Empaquetado multi-etapa Maven/Alpine JRE 21
│   ├── .dockerignore                                   # [NUEVO] Exclusiones de contexto de build (target/, etc.)
│   ├── pom.xml                                         # [MODIFICAR] Incorporar spring-boot-starter-actuator
│   └── src/
│       ├── main/
│       │   ├── java/com/easyeat/
│       │   │   └── config/
│       │   │       └── SecurityConfig.java             # [MODIFICAR] Bean CORS, .cors() y permitAll health/webhook
│       │   └── resources/
│       │       └── application.yml                     # [MODIFICAR] server.port, datasource, flyway, cors, actuator
│       └── test/
│           ├── java/com/easyeat/
│           │   ├── auth/service/
│           │   │   └── PublicUrlEmailLinkTest.java             # [NUEVO] Unit test de URLs públicas en correos (T026)
│           │   ├── config/
│           │   │   ├── SecurityCorsConfigurationTest.java      # [NUEVO] Unit test de sanitización CORS (T010)
│           │   │   ├── SecurityCorsIntegrationTest.java        # [NUEVO] MockMvc preflight OPTIONS y CORS (T011)
│           │   │   ├── ActuatorHealthEndpointIntegrationTest.java # [NUEVO] Integration test de /actuator/health público (T014)
│           │   │   ├── ServerPortConfigurationTest.java        # [NUEVO] Unit test de puerto dinámico Tomcat (T015)
│           │   │   └── NeonDataSourceConfigurationTest.java    # [NUEVO] Unit test de DataSource SSL y timeouts (T018)
│           │   └── order/
│           │       ├── FlywayFullMigrationIntegrationTest.java # [NUEVO] Testcontainers migraciones V1..V19 (T017)
│           │       ├── PaymentWebhookSecurityIntegrationTest.java # [NUEVO] MockMvc webhook público sin JWT (T024)
│           │       └── service/
│           │           └── MercadoPagoPublicUrlTest.java       # [NUEVO] Unit test back_urls Mercado Pago (T025)
│           └── resources/scripts/
│               └── verify-backend-image.sh                     # [NUEVO] Smoke test de imagen Docker backend (T008)
└── frontend/
    ├── Dockerfile                                      # [NUEVO] Empaquetado multi-etapa Node 20 / Nginx Alpine
    ├── .dockerignore                                   # [NUEVO] Exclusiones de contexto de build (node_modules/, etc.)
    ├── nginx.conf                                      # [NUEVO] Configuración Nginx (try_files SPA, cache-control, PORT)
    └── src/test/
        ├── config/
        │   └── apiClientBaseUrl.test.ts                # [NUEVO] Unit test de baseURL en apiClient (T020)
        └── scripts/
            └── verify-nginx-spa.sh                     # [NUEVO] Smoke test de servidor Nginx y SPA (T021)
```

**Decisión sobre la estructura**: Se mantiene la arquitectura mono-repositorio desacoplada existente en `easyeat/backend` y `easyeat/frontend`. No se alteran paquetes de dominio, servicios de negocio, entidades JPA ni interfaces visuales de React.

---

## 5. Phase 0 — Outline & Research

Se completó la investigación técnica exhaustiva documentada en [research.md](./research.md). Decisiones consolidadas:
1. **Composición Dockerfile Backend**: Etapa 1 Maven 3.9 JDK 21 (`mvn clean package -DskipTests`), Etapa 2 Alpine JRE 21 (`eclipse-temurin:21-jre-alpine`), creación de usuario de sistema `easyeat:easyeat`, copia exclusiva de `/app/app.jar` y cambio a `USER easyeat`.
2. **Gobernanza de Memoria JVM**: `ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]`.
3. **CORS Dinámico**: Bean `CorsConfigurationSource` sanitizando con `trim()` y remoción de barras finales, `allowCredentials=true`, `maxAge=3600L`, integrado en `SecurityFilterChain`. Fallback a `http://localhost:3000`.
4. **Puerto y Health Check**: `server.port: ${PORT:8080}`, dependencia `spring-boot-starter-actuator`, endpoint público `/actuator/health` en `SecurityConfig` respondiendo 200 `{"status":"UP"}` o 503 `{"status":"DOWN"}`.
5. **Conexión Directa a Neon**: `SPRING_DATASOURCE_URL` con `sslmode=require` directo (sin `-pooler`), HikariCP con `connection-timeout: 30000` para tolerar scale-to-zero, y ejecución de migraciones `V1` a `V19` y `R__seed_dev_*`.
6. **Composición Dockerfile Frontend**: Etapa 1 Node 20 Alpine con `ARG VITE_API_BASE_URL` ejecutando `npm ci` y `npm run build`; Etapa 2 Nginx Alpine sirviendo `/dist` en `/usr/share/nginx/html`.
7. **Enrutamiento SPA y Caché en Nginx**: Directiva `try_files $uri $uri/ /index.html =404;`, resolución de `${PORT:-80}` vía script/envsubst de Nginx, `Cache-Control: no-cache` para `index.html` y `Cache-Control: public, max-age=31536000, immutable` para `/assets/*`.
8. **Sincronización de URLs Públicas**: `MERCADOPAGO_FRONTEND_BASE_URL` concatenando `/checkout/payment-return`, webhook público `/api/v1/payments/*/webhook` exento de JWT, y enlaces de correo con dominios de Render.
9. **Higiene de Contexto**: Archivos `.dockerignore` en backend y frontend.

---

## 6. Phase 1 — Design & Contracts

Se elaboraron los artefactos de diseño e interfaces operativas correspondientes:
- **Modelo de Infraestructura y Topología**: Registrado en [deployment-model.md](./deployment-model.md) y complementado en [data-model.md](./data-model.md).
- **Contratos Operativos Verificables (`contracts/`)**:
  - [01-backend-image-contract.md](./contracts/01-backend-image-contract.md): Contrato de imagen backend, usuario no root y flags JVM.
  - [02-frontend-image-contract.md](./contracts/02-frontend-image-contract.md): Contrato de imagen frontend, inyección de API y puerto dinámico.
  - [03-cors-contract.md](./contracts/03-cors-contract.md): Contrato de CORS dinámico, normalización sintáctica y preflight.
  - [04-health-check-contract.md](./contracts/04-health-check-contract.md): Contrato de salud público Actuator y códigos de estado.
  - [05-neon-connection-contract.md](./contracts/05-neon-connection-contract.md): Contrato de conexión directa Neon SSL y timeouts HikariCP.
  - [06-flyway-migrations-contract.md](./contracts/06-flyway-migrations-contract.md): Contrato de migraciones `V1` a `V19` y semillas repetibles.
  - [07-spa-routing-contract.md](./contracts/07-spa-routing-contract.md): Contrato de reescritura `try_files` SPA y políticas de caché Nginx.
  - [08-public-urls-contract.md](./contracts/08-public-urls-contract.md): Contrato de sincronización de URLs de retorno y webhooks.
- **Guía de Verificación Operativa**: Registrada en [quickstart.md](./quickstart.md) con escenarios ejecutables para AS-1 a AS-24.

---

## 7. Re-evaluación de Constitution Check

*Post-diseño: verificación final de cumplimiento de principios.*

- **Principio I a X**: Todos los principios fundamentales se mantienen en estado **PASS**.
- **Principio X (Full-Stack Vertical Slice)**: Se confirma la integración simétrica y desacoplada entre backend, frontend, base de datos y pasarela externa sin dependencias huérfanas.
- **Principio V (Bounded Scope)**: El diseño no introduce ninguna tecnología ajena a las declaradas en el spec (cero pipelines CI/CD externos, cero herramientas IaC, cero modificaciones en reglas de negocio).

---

## 8. Complexity Tracking

> *No se registran violaciones a la Constitución que requieran justificación excepcional. Todas las decisiones arquitectónicas respetan el diseño simple, cohesivo y justificado (KISS).*

