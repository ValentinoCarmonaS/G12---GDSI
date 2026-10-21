# Classroom Demo & Hands-on Prompts

Use these tested prompts during live lectures, workshops, or student lab exercises.

---

## 1. Establishing the Constitution
**Command**: `/speckit-constitution`

**Sample Prompt**:
-"
Crear la constitución del proyecto en formato spec-kit, basada en principios de ingeniería de software agnósticos a tecnología. Los principios deben seguir siendo válidos aunque cambien el lenguaje, framework, arquitectura, infraestructura, dominio o herramientas. Redactar todo en español.

REGLAS DE REDACCIÓN
- Usar las palabras MUST, MUST NOT, SHOULD y MAY con el significado de RFC 2119.
- Cada principio debe ser declarativo, verificable en una revisión o gate de calidad, y sin ambigüedad.
- Cada principio debe incluir: enunciado normativo, criterios de cumplimiento verificables y un breve Rationale (el porqué). El rationale no constituye un principio nuevo.
- No introducir principios por iniciativa propia. Solo se permiten los que son consecuencia directa de las reglas de este prompt.

ESTRUCTURA DEL ARCHIVO
1. Principios Fundamentales (los 10 principios de abajo)
2. Restricciones Adicionales (mecanismos y decisiones declaradas por el proyecto)
3. Flujo de Desarrollo y Puertas de Calidad
4. Gobernanza

=== 1. PRINCIPIOS FUNDAMENTALES ===

I. La especificación es la fuente de verdad
- Cuando existan requisitos de clientes, usuarios o stakeholders, la especificación funcional resultante MUST ser la autoridad sobre qué comportamiento debe proporcionar el software.
- La implementación MUST adaptarse a la especificación y MUST NOT redefinirla silenciosamente.
- Ante una discrepancia entre código y especificación, el agente MUST tomar la especificación como autoridad, y MUST identificar y resolver la discrepancia de forma explícita.
- Cumplimiento: toda discrepancia detectada queda registrada y resuelta, ya sea corrigiendo el código o enmendando la especificación con aprobación humana.

II. Especificación antes de implementación
- En proyectos con Spec-Driven Development, el agente MUST NOT iniciar la implementación de una historia de usuario hasta completar el proceso de especificación y preparación definido en el Flujo de Desarrollo.
- Una historia de usuario SOLO puede entrar en implementación cuando existe como unidad de trabajo definida y verificable.
- Cumplimiento: ninguna tarea de implementación existe sin un artefacto previo de especificación, plan y tareas que la respalde.

III. Verificación por capas y proporcional al riesgo
- La validación MUST ser progresiva y cada nivel NO reemplaza al anterior:
  1. tests que verifican que el comportamiento implementado funciona;
  2. verificación de que ese comportamiento cumple la especificación;
  3. pruebas de regresión que preservan el comportamiento validado;
  4. tests suficientemente expresivos para servir como documentación ejecutable.
- Los casos borde, estados inválidos, límites, errores esperables y restricciones del dominio MUST tratarse explícitamente cuando formen parte del comportamiento garantizado. Una funcionalidad no se considera verificada solo porque funcionen sus casos normales.
- La estrategia de testing MUST ser proporcional al riesgo y la criticidad del software, combinando según corresponda técnicas tales como tests unitarios, de integración y de nivel superior, análisis de cobertura, mutation testing o chaos engineering. Estas técnicas son ejemplos y no obligaciones individuales.
- Ninguna métrica de cobertura MUST usarse como sustituto de la calidad de los tests.
- Cumplimiento: cada requisito funcional tiene al menos un test trazable, y los casos borde relevantes tienen tests explícitos.

IV. Diseño simple, cohesivo y justificado
- El diseño MUST priorizar alta cohesión, bajo acoplamiento, legibilidad, separación clara de responsabilidades, mínima complejidad accidental y mantenibilidad. KISS MUST aplicarse como principio de diseño.
- Simplicidad significa estructura clara, predecible y coherente con las buenas prácticas aplicables. NO significa facilidad de implementación ni ausencia de rigor.
- Abstracciones, patrones de diseño, frameworks y mecanismos arquitectónicos son herramientas, no objetivos. Una abstracción SHOULD introducirse cuando reduzca complejidad, mejore mantenibilidad, represente un concepto real del dominio, facilite una variabilidad prevista o evite un costo futuro significativo. Un patrón de diseño MUST usarse solo ante una necesidad concreta que lo justifique.
- La duplicación NO MUST eliminarse automáticamente si hacerlo introduce más complejidad que la duplicación existente.
- Cuando un paradigma orientado a objetos sea apropiado, se SHOULD favorecer encapsulación, abstracción, polimorfismo y delegación, y usar herencia solo cuando represente correctamente la relación conceptual y aporte valor.
- Cumplimiento: toda abstracción o patrón introducido tiene una justificación documentada en el plan o en la revisión.

V. Alcance acotado
- El agente MUST NOT implementar funcionalidades, comportamientos, configuraciones o cambios que no estén respaldados por al menos una de estas fuentes: especificación, documentación oficial del proyecto, requisito explícito del usuario, decisión arquitectónica documentada o instrucción explícita del prompt actual. Una mejora no solicitada está fuera de alcance.
- El agente MUST NOT realizar refactorizaciones no solicitadas durante la implementación de una historia. Refactorizar es mejorar la estructura interna sin modificar el comportamiento externo, preservando las garantías funcionales existentes.
- Si el agente detecta una oportunidad de refactoring, MUST explicar dónde está, por qué la considera conveniente, y el beneficio y el riesgo esperados, y MUST solicitar una decisión mediante el mecanismo de escalamiento designado (ver Principio VIII). MUST NOT realizarla hasta recibir autorización explícita.
- Cumplimiento: todo cambio del diff se corresponde con un requisito, tarea o decisión documentada.

VI. Deuda técnica controlada
- La deuda técnica detectada MUST identificarse y priorizarse.
- Durante la implementación de una feature, la deuda técnica no relacionada con ella MUST NOT desviar el trabajo en curso.
- Cuando no haya una feature activa, la deuda relevante SHOULD resolverse lo antes razonablemente posible según su prioridad e impacto.
- La resolución de deuda que implique refactoring MUST tramitarse como trabajo especificado y autorizado de forma explícita, conforme al Principio V.
- Cumplimiento: existe un registro de deuda técnica con prioridad e impacto.

VII. Trazabilidad, consistencia y documentación
- Cuando el proyecto use artefactos de ingeniería, MUST mantenerse consistencia a lo largo de la cadena: requisitos → especificación → diseño/plan → tareas → implementación → tests → documentación.
- Toda modificación que afecte un artefacto MUST provocar la revisión de los artefactos dependientes cuando corresponda.
- Si el agente detecta una contradicción entre artefactos, MUST NOT ocultarla ni resolverla unilateralmente cuando implique una decisión de producto, dominio o arquitectura, y MUST escalarla (Principio VIII).
- La documentación MUST preservar toda información necesaria para comprender, mantener, verificar o evolucionar el sistema: decisiones, restricciones, requisitos, modelos y trade-offs. Los comentarios en el código SHOULD usarse solo cuando aporten información que el código no hace evidente.
- Cumplimiento: cada tarea referencia su requisito de origen, y un cambio en un artefacto deja constancia de la revisión de sus dependientes.

VIII. Evidencia sobre suposición y escalamiento humano
- El agente MUST priorizar la evidencia disponible (repositorio, especificaciones, documentación, tests, configuración y herramientas) antes de asumir comportamiento.
- Cuando una afirmación no pueda verificarse razonablemente, el agente MUST declararlo.
- Ante una ambigüedad significativa, un conflicto entre fuentes o más de una interpretación técnicamente válida que pueda alterar el comportamiento, el agente MUST detener la implementación afectada y escalar la decisión a un humano mediante el mecanismo designado, presentando: la ambigüedad detectada, las interpretaciones posibles, las consecuencias de cada una y su recomendación técnica.
- La implementación SOLO puede continuar una vez resuelta la ambigüedad.
- El mecanismo de escalamiento concreto se declara en Restricciones Adicionales.
- Cumplimiento: toda decisión de producto, dominio o arquitectura tomada ante ambigüedad queda registrada con su resolución.

IX. Ingeniería por sobre conveniencia
- Las decisiones del agente MUST priorizar corrección, mantenibilidad, trazabilidad, verificabilidad y coherencia arquitectónica por encima de la conveniencia inmediata.
- La velocidad de implementación NO justifica omitir especificación, validación, tests, revisión ni criterios arquitectónicos establecidos.

X. Gobernanza agnóstica a la tecnología
- Los principios MUST expresarse en términos de ingeniería y comportamiento, no de tecnologías específicas.
- La constitución MUST NOT imponer lenguaje, framework, base de datos, arquitectura, patrón, proveedor cloud ni herramienta, salvo que el proyecto los declare explícitamente como restricciones propias en la sección de Restricciones Adicionales.
- Las decisiones tecnológicas concretas se definen en los artefactos de especificación y planificación.

=== 2. RESTRICCIONES ADICIONALES ===
Esta sección contiene los mecanismos y decisiones declarados por el proyecto. NO son principios universales. Pueden modificarse sin alterar los Principios Fundamentales, aunque siguen sujetos al procedimiento de enmienda.
- Mecanismo de escalamiento humano: la skill `grill-me`. Toda referencia a "mecanismo de escalamiento designado" en los principios se resuelve con esta skill.
- Resto de restricciones tecnológicas: ninguna declarada por el momento.

=== 3. FLUJO DE DESARROLLO Y PUERTAS DE CALIDAD ===
Proceso mínimo previo a la implementación de una historia de usuario, en este orden:
1. constitución definida;
2. especificación funcional;
3. resolución de ambigüedades;
4. planificación técnica, incluyendo la verificación de cumplimiento de esta constitución;
5. checklist de calidad de la especificación;
6. descomposición en tareas;
7. sincronización con el sistema de gestión del trabajo, cuando corresponda;
8. análisis de consistencia y cobertura previo a la implementación.

Entrega atómica, trazable y reversible:
- Cada historia de usuario MUST desarrollarse en una branch independiente. Cuando la herramienta de SDD cree las branches por feature, cada feature MUST contener una única historia de usuario.
- Los cambios MUST organizarse en commits atómicos, trazables y razonablemente reversibles. Cada commit MUST representar una unidad lógica de cambio, mantener relación clara con la especificación o tarea correspondiente, tener un mensaje descriptivo acorde a buenas prácticas de versionado, y no mezclar cambios funcionales, refactors y cambios no relacionados.
- Una historia de usuario SOLO se considera integrada después de atravesar el Pull Request y la validación definidos por el proyecto.

=== 4. GOBERNANZA ===
Incluir:
- La constitución prevalece sobre cualquier otra práctica del proyecto en caso de conflicto.
- Procedimiento de enmienda: toda modificación MUST estar documentada, justificada y aprobada, e incluir un plan de migración cuando afecte artefactos existentes.
- Política de versionado semántico de la constitución: MAJOR para eliminación o redefinición incompatible de principios; MINOR para agregado de principios o expansión sustancial; PATCH para aclaraciones, redacción y correcciones no semánticas.
- Revisión periódica de cumplimiento, con una frecuencia definida y verificación de que los planes y las revisiones respetan los principios.
- Criterio para distinguir principios obligatorios de decisiones específicas del proyecto: un principio obligatorio es aplicable con independencia de la tecnología y del dominio, y vive en Principios Fundamentales; toda decisión que dependa de una tecnología, herramienta o contexto concreto vive en Restricciones Adicionales o en los artefactos de especificación y plan.

METADATOS Y SINCRONIZACIÓN
- Versión inicial 1.0.0. Usar la fecha actual como fecha de ratificación y de última enmienda.
- Generar el Sync Impact Report al inicio del archivo, y revisar la consistencia con los templates dependientes (plan, spec y tasks), de modo que la verificación de la constitución en el plan evalúe estos principios.
"-

---

## 2. Establish the Feature Specification
**Command**: `/speckit-specify`
**Sprint:** 1
**Scheduled Activity:** Servicio Central de Autenticación JWT y Recuperación de Claves

**Sample Prompt**:
-"
[speckit-specify](slashCommand;speckit-specify) 
[/grill-me](slashCommand;grill-me) 
Feature: Central Authentication Service and Password Recovery (Servicio Central de Autenticación JWT y Recuperación de Claves) del proyecto EasyEat [[Scope%20-%2014-10-2026](file;file:///f%3A/ALL/PROGRAMACION/Proyectos/SDD/sdd/EasyEat%20-%20Docs/Scope%20-%2014-10-2026) ]. Dentro del scope tenes los artefactos de documentacion de ingenieria de software [[Artefactos%20CSV](file;file:///f%3A/ALL/PROGRAMACION/Proyectos/SDD/sdd/EasyEat%20-%20Docs/Scope%20-%2014-10-2026/Artefactos%20CSV) ], dentro de el esta todo el proyecto segun indica "Lean Inception". Backlog, USM, Es-No es-Hace-No hace, Product Vision, Cronograma, Analisis de Riesgos, etc.

Description:
Define the functional specification for the core authentication and credential recovery capabilities of the EasyEat multi-tenant platform, corresponding to Sprint 1's initial delivery activity:
- Actividad: "Servicio Central de Autenticación JWT y Recuperación de Claves"
- Detalle: "Endpoints de registro (COM-1.1.1) y login unificado con hash bcrypt (COM-1.2.1), emisión de tokens JWT con claims de rol/tenant y servicio transaccional de reseteo de claves por correo electrónico (COM-1.3.1, ADM-1.2.1)."

### Goal

Provide a secure, centralized authentication and credential management service that allows users to register accounts with email and password, authenticate through a unified login mechanism that asserts identity and tenant/role context, and securely reset forgotten credentials via time-limited transactional email tokens.

### User personas

- **Comensal (Customer / Diner)**: End-user placing orders and accessing restaurant menus.
- **Administrador (Tenant Owner / Manager)**: High-privilege administrative user managing restaurant settings and staff.

### Capabilities and user stories

#### User Story 1 (P1) - Registro de comensal con credenciales (COM-1.1.1)
- **Descripción**:
  Como comensal nuevo
  Quiero crear una cuenta en la plataforma ingresando mi correo electrónico y una contraseña
  Para poder registrarme en el sistema y comenzar a hacer pedidos.
- **Criterios de Aceptación**:
  - Dado que el comensal no posee una cuenta en EasyEat y se encuentra en la pantalla de registro, cuando ingresa un correo electrónico válido no registrado, una contraseña que cumple los requisitos de seguridad y presiona "Registrarse", entonces el sistema crea la cuenta exitosamente, muestra un mensaje de confirmación y redirige al comensal a la pantalla de Login.
  - Dado que el comensal ingresa un correo electrónico que ya se encuentra registrado en la plataforma, cuando presiona "Registrarse", entonces el sistema no crea la cuenta y muestra un mensaje de error indicando que el correo electrónico ya está asociado a un usuario existente.
  - Dado que el comensal ingresa un formato de correo electrónico sintácticamente inválido, cuando intenta enviar el formulario presionando "Registrarse", entonces el sistema bloquea el envío y muestra una notificación de error especificando que la dirección de correo es inválida.
  - Dado que el comensal ingresa un correo electrónico válido pero una contraseña que incumple la longitud mínima o los criterios de complejidad definidos por el sistema, cuando presiona "Registrarse", entonces el sistema impide la creación de la cuenta y resalta visualmente los requisitos de seguridad de la contraseña que no se han cumplido.
  - Dado que el comensal omite completar el campo de correo electrónico, el campo de contraseña o ambos, cuando presiona el botón "Registrarse", entonces el sistema no procesa la solicitud y marca los campos vacíos como obligatorios con sus correspondientes mensajes de validación.
  - Dado que el comensal se encuentra en la pantalla de registro y decide no proseguir con la creación de la cuenta, cuando presiona el enlace "Ya tengo una cuenta" o "Iniciar sesión", entonces el sistema interrumpe el flujo de registro sin conservar datos y redirige al comensal a la pantalla de Login.

#### User Story 2 (P1) - Inicio de sesión unificado con correo y contraseña (COM-1.2.1)
- **Descripción**:
  Como comensal registrado
  Quiero iniciar sesión con mis credenciales
  Para acceder a mis pedidos y preferencias.
- **Criterios de Aceptación**:
  - Dado que el comensal posee una cuenta activa en EasyEat, cuando ingresa su correo electrónico y contraseña correctos y presiona el botón "Iniciar sesión", entonces el sistema lo autentica exitosamente y lo redirige a la pantalla principal o catálogo de su establecimiento por defecto.
  - Dado que el comensal se encuentra en la pantalla de inicio de sesión, cuando ingresa un correo electrónico registrado pero una contraseña incorrecta y presiona "Iniciar sesión", entonces el sistema deniega el acceso y muestra un mensaje de error genérico de seguridad sin limpiar el campo del correo.
  - Dado que el comensal intenta ingresar a la plataforma, cuando ingresa un correo electrónico que no existe en la base de datos junto con cualquier contraseña y presiona "Iniciar sesión", entonces el sistema rechaza el intento y muestra el mismo mensaje de error genérico de seguridad para evitar exponer qué correos están registrados.
  - Dado que el comensal se encuentra en la vista de login, cuando omite completar el campo de correo electrónico, el de contraseña o ambos y presiona "Iniciar sesión", entonces el sistema no ejecuta la petición al servidor y resalta los campos vacíos indicando que son obligatorios.
  - Dado que el comensal se encuentra en la pantalla de login y no logra recordar su clave, cuando hace clic en el enlace de recuperación de contraseña, entonces el sistema interrumpe el flujo actual y lo redirige a la pantalla correspondiente para reestablecerla.
  - Dado que el comensal ingresa un correo y contraseña correctos, cuando el estado de su cuenta figura como inhabilitada o suspendida por un administrador, entonces el sistema bloquea el ingreso y despliega un aviso indicando que el perfil no se encuentra autorizado para operar.
  - Dado que el comensal posee una cuenta activa, cuando ingresa su correo electrónico con espacios en blanco al inicio o al final, o utilizando letras mayúsculas, pero escribe la contraseña con la distinción exacta de caracteres, entonces el sistema normaliza el correo internamente y procesa el inicio de sesión de manera exitosa.

#### User Story 3 (P2) - Recuperación de contraseña por correo electrónico para comensales (COM-1.3.1)
- **Descripción**:
  Como comensal que olvidó su clave
  Quiero solicitar un blanqueo de contraseña por correo electrónico
  Para poder recuperar el acceso a mi cuenta.
- **Criterios de Aceptación**:
  - Dado que el comensal olvidó su clave y se encuentra en la pantalla de recuperación, cuando ingresa su correo electrónico registrado y solicita el restablecimiento, entonces el sistema envía un enlace seguro de un solo uso a su casilla y muestra un mensaje de confirmación.
  - Dado que el comensal intenta recuperar su contraseña, cuando ingresa un correo electrónico que no se encuentra registrado en el sistema, entonces el sistema muestra un mensaje de confirmación genérico para evitar exponer qué correos existen en la base de datos.
  - Dado que el comensal se encuentra en el formulario de recuperación, cuando ingresa un formato de correo electrónico inválido o deja el campo vacío, entonces el sistema bloquea el envío y resalta el error de validación correspondiente.
  - Dado que el comensal solicitó la recuperación de contraseña, cuando intenta generar una nueva solicitud de forma consecutiva sin dejar pasar el tiempo de gracia, entonces el sistema bloquea la acción temporalmente para evitar el envío masivo de correos no deseados.
  - Dado que el comensal recibe el correo de recuperación, cuando hace clic en el enlace seguro después de que el mismo haya excedido su tiempo máximo de validez, entonces el sistema rechaza el acceso y le indica que debe solicitar un nuevo enlace.
  - Dado que el comensal inicia el flujo de recuperación, cuando decide no avanzar y presiona la opción de volver atrás o cancelar, entonces el sistema aborta la operación sin enviar correos y lo redirige a la pantalla de inicio de sesión.

#### User Story 4 (P2) - Recuperación de contraseña para administradores (ADM-1.2.1)
- **Descripción**:
  Como administrador
  Quiero poder restablecer mi contraseña vía correo electrónico
  Para no perder el acceso a la gestión de mi negocio si me olvido la clave.
- **Criterios de Aceptación**:
  - Dado que toca "Olvidé mi contraseña", Cuando ingresa su correo, Entonces recibe un link seguro para generar una clave nueva (reutilizando el servicio centralizado de recuperación).

### Validation and invariants

- **Password security invariant**: Passwords must be hashed using a one-way cryptographic hash with individual salt (e.g., bcrypt) before persistence. Plaintext passwords must never be stored, logged, or transmitted back.
- **Token context invariant**: Authenticated session tokens must deterministically include identity (`user_id`), assigned role (`role`), and tenant scope (`tenant_id` where applicable) to guarantee strict multi-tenant authorization boundaries.
- **Password reset token integrity**: Reset tokens must be single-use, cryptographically secure, and expire after a strict, bounded time window (e.g., 15 minutes). Once used or expired, any further attempts using that token must fail.
- **Email normalization**: Email addresses must be normalized (lowercased, trimmed of leading/trailing whitespace) prior to validation, uniqueness checking, or authentication lookup.
- **Anti-enumeration guarantee**: Login and password recovery endpoints must provide generic, timing-resistant responses for non-existent vs incorrect credentials to prevent user account enumeration.
- **Rate limiting / Anti-spam**: Repeated password reset requests for the same address or IP must enforce a cooling-off cooldown period.

### Edge cases

- Account status is suspended, unverified, or disabled.
- Password reset link clicked multiple times after a successful reset.
- Rapid successive submissions of the registration or recovery forms.
- Case sensitivity in email vs case sensitivity in passwords.
- Malformed, expired, or tampered tokens provided to the reset verification interface.

### Out of scope

- Social single sign-on / Google OAuth integration (COM-1.1.2, COM-1.2.2).
- SMS password recovery (COM-1.3.2).
- QR code establishment onboarding (COM-1.1.3).
- User profile updates and dietary restriction management (COM-1.4.x).
- Specific UI component styling or frontend framework routing guards (those belong to the presentation layer).

Do not prescribe database schemas, ORM configurations, controller endpoints, or framework-specific libraries in the functional spec. Keep the focus entirely on business rules, security invariants, and user-verifiable acceptance criteria.
"-

---

## 3. Clarifications
**Command**: `/speckit-clarify`
**Sprint:** 1
**Scheduled Activity:** Servicio Central de Autenticación JWT y Recuperación de Claves

**Sample Prompt**:
-"
[speckit-clarify](slashCommand;speckit-clarify) [/grill-me](slashCommand;grill-me) 
"-

---

## 4. speckit-plan
**Command**: `/speckit-plan`
**Sprint:** 1
**Scheduled Activity:** Servicio Central de Autenticación JWT y Recuperación de Claves

**Sample Prompt**:
-"
/speckit-plan
Create the technical implementation plan for the Central Authentication Service and Password Recovery feature based on the approved specification (specs/001-central-auth-recovery/spec.md) and the EasyEat product backlog.

This is a green-field backend API service implementation for the EasyEat multi-tenant platform.

## Architecture and Technology Stack

- **Platform & Language**: Java 21 (LTS).
- **Core Framework**: Spring Boot 3.3.4 (Web, Validation, Data JPA, Security).
- **Security & Cryptography**: Spring Security 6, `BCryptPasswordEncoder` (work factor 12), and `jjwt` for stateless session token signing (HMAC-SHA256).
- **Persistence & Storage**: PostgreSQL 16+ as primary relational storage. Database migrations managed via Flyway.
- **API Contracts**: Pre-commit OpenAPI 3.0 specification in `specs/001-central-auth-recovery/contracts/openapi.yaml` serving as the design authority, validated during test runs with Springdoc.
- **Error Format**: RFC 9457 `ProblemDetail` responses via a centralized `@RestControllerAdvice`.
- **Testing Stack**: JUnit 5, Mockito, AssertJ, Spring Boot Test (`MockMvc`), and `Testcontainers` (PostgreSQL 16) for real integration verification.
- **Time Abstraction**: Injectable `java.time.Clock` bean across all services to guarantee deterministic verification of token expiration and cooldown windows.

## Core Technical Architecture and Identity Model

1. **Global Identity Table (No Hibernate `@TenantId`)**:
   - The `users` table is a global platform identity directory. It must NOT be annotated with Hibernate's `@TenantId` filter, because login lookups by email occur before tenant context exists, and comensales have no fixed tenant.
   - Multi-tenant data isolation applies to restaurant business domains (menus, orders, inventory). For authentication, `tenant_id` is strictly an identity attribute stored on staff records and propagated as a claim inside the JWT.
   - Enforce database constraint: `(role = 'COMENSAL' AND tenant_id IS NULL) OR (role IN ('ADMINISTRADOR', 'CHEF', 'ENCARGADO') AND tenant_id IS NOT NULL)`.
2. **Deterministic JWT Claims and Stateful Revocation**:
   - Session tokens contain standard claims (`iss = easyeat-auth`, `sub = userId`, `iat`, `exp = 8 hours`) and custom claims: `role`, `tenant_id` (null for comensales, UUID for staff), and `token_version` (integer).
   - The JWT authentication filter checks both that `token_version` matches the database and that `status == 'ACTIVE'`. If an account is suspended or the token version has been incremented, the request is immediately rejected with `401 Unauthorized` / `403 Forbidden`.
3. **Atomic Password Reset Consumption**:
   - Password reset tokens are single-use, 15-minute lifespan, stored as SHA-256 hashes.
   - Password reset is role-agnostic: any account in `ACTIVE` status can initiate and complete the flow.
   - Consumption must be atomic via a single conditional update:
     `UPDATE password_reset_tokens SET consumed_at = :now WHERE token_hash = :hash AND consumed_at IS NULL AND expires_at > :now`.
     If affected rows == 0, the operation is rejected.
   - Requesting a new reset token immediately invalidates any prior unconsumed tokens for that user.
   - On successful reset, update `password_hash` and increment `token_version` on `User` to revoke all active sessions.

## Domain and Data Model (Flyway Migrations)

- **`V1__init_auth_schema.sql`** (stored in `classpath:db/migration`):
  - **`users` table**:
    - `id` (UUID, Primary Key, default `gen_random_uuid()`)
    - `email` (VARCHAR(255), NOT NULL, UNIQUE)
    - `password_hash` (VARCHAR(72), NOT NULL)
    - `role` (VARCHAR(30), NOT NULL: `COMENSAL`, `ENCARGADO`, `CHEF`, `ADMINISTRADOR`)
    - `tenant_id` (UUID, NULL for COMENSAL, NOT NULL for staff roles; synthetic UUID without foreign key in V1 until `tenants` table exists)
    - `status` (VARCHAR(20), NOT NULL, default 'ACTIVE': `ACTIVE`, `SUSPENDED`)
    - `token_version` (INT, NOT NULL, default 0)
    - `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL)
    - `CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))`
    - `CONSTRAINT ck_users_role_tenant CHECK ((role = 'COMENSAL' AND tenant_id IS NULL) OR (role IN ('ADMINISTRADOR', 'CHEF', 'ENCARGADO') AND tenant_id IS NOT NULL))`
  - **`password_reset_tokens` table**:
    - `id` (UUID, Primary Key, default `gen_random_uuid()`)
    - `user_id` (UUID, NOT NULL, FK to `users.id` ON DELETE CASCADE)
    - `token_hash` (VARCHAR(64), NOT NULL, UNIQUE index, SHA-256 of the raw token)
    - `expires_at` (TIMESTAMPTZ, NOT NULL)
    - `consumed_at` (TIMESTAMPTZ, NULL)
    - `created_at` (TIMESTAMPTZ, NOT NULL)
- **Environment-Specific Seed (`V2__seed_dev_tenant.sql`)**:
  - Located in `classpath:db/dev` and configured in Spring profiles (`spring.flyway.locations=classpath:db/migration,classpath:db/dev`) strictly for `dev` and `test`. Never loaded in production.
  - The development administrator password must be loaded via the environment variable `DEV_ADMIN_PASSWORD` (hashed at startup or migration placeholder) rather than hardcoded in the repository.

## API Design, Endpoints, and Product Trade-Offs

- `POST /api/v1/auth/register`:
  - Request: `RegisterRequest(@Email @NotBlank String email, @NotBlank @MaxBytes(72) String password)`.
  - Normalizes email via `email.toLowerCase(Locale.ROOT).trim()`.
  - Custom validator `@MaxBytes(72)` verifies `password.getBytes(StandardCharsets.UTF_8).length <= 72` (preventing BCrypt silent truncation of multi-byte characters), alongside password complexity (min 8 chars, 1 uppercase, 1 lowercase, 1 digit).
  - **Product Decision (COM-1.1.1)**: If email already exists, returns `409 Conflict` with ProblemDetail ("El correo electrónico ya está asociado a un usuario existente"). Documented product trade-off, protected by rate limiting.
  - Defaults: `role = COMENSAL`, `tenant_id = null`, `status = ACTIVE`.
  - Response: `201 Created` with `UserSummaryResponse(id, email, role, status)`.
- `POST /api/v1/auth/login`:
  - Request: `LoginRequest(@NotBlank String email, @NotBlank String password)`.
  - Normalizes email before lookup.
  - **Timing Attack Mitigation**: Compute a dynamic `DUMMY_HASH` at application startup using the configured `BCryptPasswordEncoder` (cost 12). If the user does not exist, execute `passwordEncoder.matches(password, DUMMY_HASH)` to ensure uniform response timing.
  - If user is missing or password does not match: return `401 Unauthorized` with generic message ("Credenciales inválidas").
  - **Product Decision (COM-1.2.1)**: If credentials are valid but `status == 'SUSPENDED'`, return `403 Forbidden` with ProblemDetail message ("Perfil no autorizado para operar").
  - If valid and active: return `200 OK` with `AuthTokenResponse(accessToken, tokenType, expiresIn, user)`.
- `POST /api/v1/auth/password-reset/request`:
  - Request: `PasswordResetRequest(@Email @NotBlank String email)`.
  - Returns `200 OK` generic message immediately regardless of whether the email exists (anti-enumeration preserved).
  - Transactional email dispatch is asynchronous (`@Async` or event publisher) and triggers only if the user exists and is active.
- `POST /api/v1/auth/password-reset/confirm`:
  - Request: `PasswordResetConfirmRequest(@NotBlank String token, @NotBlank @MaxBytes(72) String newPassword)`.
  - Validates `newPassword` complexity and `@MaxBytes(72)`.
  - Executes atomic token consumption. On success, updates `password_hash`, increments `token_version`, and returns `200 OK`.

## Security, Rate Limiting, and Audit Logging

- **Dual Independent Rate Limiting**:
  - Implement two distinct rate limiters using Caffeine in-memory cache (documented assumption: single-instance for MVP v1; Redis required for horizontal scaling):
    1. **Per-Email Lockout**: 5 failed login attempts on a normalized email (whether the email exists or not) trigger a 15-minute lockout.
    2. **Per-IP Limiter**: 50 total attempts per IP per hour to protect against password-spraying and distributed attacks.
  - **Password Reset Cooldown**: 1 reset request per normalized email per 60 seconds (increments counter even for non-existent emails, returning identical generic 200 OK).
- **Reverse Proxy Header Security**:
  - Configure `server.forward-headers-strategy=native` with `server.tomcat.remoteip.internal-proxies` restricting trusted proxy CIDRs (e.g. `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.1`) to prevent client IP spoofing via crafted `X-Forwarded-For` headers.
- **Audit Logging**:
  - `SecurityAuditLogger` records structured JSON logs: `eventType` (LOGIN_SUCCESS, LOGIN_FAILURE, LOCKOUT, RESET_REQUEST, RESET_CONFIRM), `timestamp`, `clientIp`, and masked email (`u***@domain.com`). Plaintext passwords and raw tokens must NEVER be logged.

## Project Structure (Package by Feature)

```text
backend/
├── pom.xml
├── docker-compose.yml (PostgreSQL 16 + Mailpit)
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── db/
│       ├── migration/
│       │   └── V1__init_auth_schema.sql
│       └── dev/
│           └── V2__seed_dev_tenant.sql (only in dev/test profiles)
├── src/main/java/com/easyeat/
│   ├── EasyEatApplication.java
│   ├── config/ (ClockConfig, SecurityConfig, ProxyConfig)
│   └── auth/
│       ├── domain/ (User, UserRole, UserStatus, PasswordResetToken)
│       ├── dto/ (RegisterRequest, LoginRequest, PasswordResetRequest, PasswordResetConfirmRequest, AuthTokenResponse, UserSummaryResponse)
│       ├── validation/ (MaxBytes, MaxBytesValidator, PasswordComplexity)
│       ├── repository/ (UserRepository, PasswordResetTokenRepository)
│       ├── service/ (AuthService, PasswordResetService, JwtTokenService, EmailService)
│       ├── controller/ (AuthController, PasswordResetController, AuthExceptionHandler)
│       └── security/ (JwtAuthenticationFilter, LoginRateLimiter, SecurityAuditLogger)
└── src/test/java/com/easyeat/auth/
    ├── unit/ (MaxBytesValidatorTest, JwtTokenServiceTest, PasswordResetServiceTest)
    ├── contract/ (OpenApiContractTest, ProblemDetailFormatTest)
    └── integration/ (TestcontainersPostgresTest, AuthFlowIntegrationTest, ConcurrencyTokenTest, RateLimitingTest)

"-

---

## 5. speckit-tasks
**Command**: `/speckit-tasks`
**Sprint:** 1
**Scheduled Activity:** Servicio Central de Autenticación JWT y Recuperación de Claves

**Sample Prompt**:
-"
/speckit-tasks
Generate atomic, dependency-ordered tasks for the Central Authentication Service and Password Recovery feature based on the approved design artifacts in specs/001-central-auth-recovery/ (spec.md, plan.md, data-model.md, quickstart.md, and contracts/openapi.yaml).

Target repository path: All implementation and test files live under `easyeat/backend/`.

Enforce strict TDD ordering (write failing test first -> implement minimal code -> verify with Testcontainers/MockMvc) within each User Story phase.

Ensure the task breakdown follows these exact phase requirements:

1. Phase 1: Setup (Shared Infrastructure)
   - Initialize Maven single-project configuration (`easyeat/backend/pom.xml`) with Java 21, Spring Boot 3.3.4, Spring Security 6, JJWT 0.12.6, Caffeine, Flyway 10, Testcontainers PostgreSQL, and Spring Mail.
   - Configure local dev containers (`easyeat/backend/docker-compose.yml`) with PostgreSQL 16 and Mailpit.
   - Set up application profiles (`application.yml`, `application-dev.yml`, `application-test.yml`).

2. Phase 2: Foundational (Blocking Prerequisites)
   - Create Flyway database migrations `V1__create_users.sql` and `V2__create_password_reset_tokens.sql` with checks for lowercase email and role/tenant constraints.
   - Configure `ClockConfig` bean for deterministic time-based testing.
   - Implement centralized RFC 9457 error handling in `GlobalExceptionHandler.java`.
   - Implement base `User` entity and `UserRepository` with `findByEmail()` and `existsByEmail()`.
   - Configure baseline `SecurityConfig.java` with `BCryptPasswordEncoder` (work factor 12) and stateless session policy.

3. Phase 3: User Story 1 - Comensal Registration (Priority: P1) [US1]
   - Test-first: Unit test for `@MaxBytes(72)` custom validator and registration DTO constraints.
   - Test-first: MockMvc WebMvc contract tests for `POST /api/v1/auth/register` (201 Created, 400 Bad Request, 409 Conflict).
   - Implementation: `@MaxBytes` validator, `RegisterRequest` DTO, and `AuthService.register()` enforcing normalized email and password hashing.
   - Implementation: `AuthController.register()` endpoint.
   - Integration: Testcontainers integration test verifying end-to-end comensal persistence and duplicate email conflict.

4. Phase 4: User Story 2 - Unified Login with JWT (Priority: P1) [US2]
   - Test-first: Unit tests for `JwtService` (token generation, claims extraction: `sub`, `role`, `tenant_id`, `token_version`, expiration, and tampering).
   - Test-first: Unit tests for `RateLimitingService` (per-email 5-attempt lockout, per-IP limit, non-existent email counting).
   - Test-first: MockMvc tests for `POST /api/v1/auth/login` (200 OK, 401 Unauthorized with timing mitigation, 403 Forbidden for SUSPENDED accounts).
   - Implementation: `JwtService`, `RateLimitingService`, and `AuthService.login()` with dynamic `DUMMY_HASH` comparison on missing users.
   - Implementation: `JwtAuthenticationFilter` verifying `token_version` match and `ACTIVE` account status.
   - Implementation: `AuthController.login()` endpoint.
   - Integration: Testcontainers integration tests verifying login for all 4 roles, account suspension blocking, and rate-limiting lockout.

5. Phase 5: User Story 3 - Password Recovery for Comensales (Priority: P2) [US3]
   - Test-first: Unit tests for `PasswordResetService` verifying 15-minute token generation, SHA-256 hashing, and cooldown rate limiting.
   - Test-first: MockMvc tests for `POST /api/v1/auth/password-reset/request` (always 200 OK generic) and `POST /api/v1/auth/password-reset/confirm` (200 OK, 400 Bad Request).
   - Implementation: `PasswordResetToken` entity, repository, and atomic SQL consumption update.
   - Implementation: `EmailService` wrapping `JavaMailSender` with async reset email dispatch.
   - Implementation: `PasswordResetService` and `AuthController` recovery endpoints (`request` and `confirm`), incrementing `token_version` on success.
   - Integration: Testcontainers integration test for full recovery cycle and concurrency test proving single-use token consumption.

6. Phase 6: User Story 4 - Password Recovery for Administrators (Priority: P2) [US4]
   - Test-first: Integration test verifying admin password recovery using seeded tenant data, confirming token invalidation across active admin sessions.
   - Verification: End-to-end test confirming that a reset admin account regains access to its tenant environment while prior admin JWTs are rejected.

7. Phase 7: Polish and Cross-Cutting Concerns
   - Contract verification test asserting live endpoints match `specs/001-central-auth-recovery/contracts/openapi.yaml`.
   - Implement `SecurityAuditLogger` for structured SLF4J JSON security event logging.
   - Complete quickstart validation scenarios from `quickstart.md`.

Format Requirements:
- Strictly format every task as `- [ ] [TaskID] [P?] [Story?] Description with exact file path`.
- Sequential task IDs: T001, T002, T003...
- Use `[P]` exclusively on parallelizable tasks touching independent files.
- Use `[US1]`, `[US2]`, `[US3]`, `[US4]` labels only inside their respective User Story phases.
"-

---

## 6. speckit-implement
**Command**: `/speckit-implement`

-"
## Incremento 1: Setup, Fundacionales y Registro de Comensales (MVP)

**Alcance:** Tareas `T001` a `T020` (Fase 1: Setup, Fase 2: Fundacionales, Fase 3: User Story 1).  
**GitHub Issues:** [#1](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/1), [#2](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/2), [#3](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/3).

```text
/speckit-implement
Implement Increment 1 (MVP: Setup, Foundational, and User Story 1 - Comensal Registration) for feature `001-central-auth-recovery`:
- Checkout dedicated branch feature/001/mvp-registration from 001-central-auth-recovery.
- Execute tasks T001 through T020 strictly adhering to specs/001-central-auth-recovery/tasks.md.
- Follow strict TDD ordering (write failing tests before implementation in Phase 3).
- Create 1:1 atomic commits per task with format "T###: description".
- Push the branch and open a GitHub Pull Request to 001-central-auth-recovery referencing Issues #1, #2, and #3.
```

---

## Incremento 2: Login Unificado con JWT

**Alcance:** Tareas `T021` a `T031` (Fase 4: User Story 2).  
**GitHub Issue:** [#4](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/4).

```text
/speckit-implement
Implement Increment 2 (User Story 2 - Unified Login with JWT) for feature `001-central-auth-recovery`:
- Checkout dedicated branch feature/001/login-jwt from 001-central-auth-recovery.
- Execute tasks T021 through T031 strictly adhering to specs/001-central-auth-recovery/tasks.md.
- Follow strict TDD ordering (write failing tests before implementation).
- Create 1:1 atomic commits per task with format "T###: description".
- Push the branch and open a GitHub Pull Request to 001-central-auth-recovery referencing Issue #4.
```

---

## Incremento 3: Recuperación de Contraseña para Comensales

**Alcance:** Tareas `T032` a `T041` (Fase 5: User Story 3).  
**GitHub Issue:** [#5](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/5).

```text
/speckit-implement
Implement Increment 3 (User Story 3 - Password Recovery for Comensales) for feature `001-central-auth-recovery`:
- Checkout dedicated branch feature/001/comensal-recovery from 001-central-auth-recovery.
- Execute tasks T032 through T041 strictly adhering to specs/001-central-auth-recovery/tasks.md.
- Follow strict TDD ordering (write failing tests before implementation).
- Create 1:1 atomic commits per task with format "T###: description".
- Push the branch and open a GitHub Pull Request to 001-central-auth-recovery referencing Issue #5.
```

---

## Incremento 4: Recuperación de Contraseña para Administradores

**Alcance:** Tareas `T042` a `T044` (Fase 6: User Story 4).  
**GitHub Issue:** [#6](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/6).

```text
/speckit-implement
Implement Increment 4 (User Story 4 - Password Recovery for Administrators) for feature `001-central-auth-recovery`:
- Checkout dedicated branch feature/001/admin-recovery from 001-central-auth-recovery.
- Execute tasks T042 through T044 strictly adhering to specs/001-central-auth-recovery/tasks.md.
- Follow strict TDD ordering (write failing tests before implementation and verification).
- Create 1:1 atomic commits per task with format "T###: description".
- Push the branch and open a GitHub Pull Request to 001-central-auth-recovery referencing Issue #6.
```

---

## Incremento 5: Pulido y Aspectos Transversales

**Alcance:** Tareas `T045` a `T048` (Fase 7: Polish & Cross-Cutting Concerns).  
**GitHub Issue:** [#7](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/7).

```text
/speckit-implement
Implement Increment 5 (Phase 7 - Polish and Cross-Cutting Concerns) for feature `001-central-auth-recovery`:
- Checkout dedicated branch feature/001/polish-audit from 001-central-auth-recovery.
- Execute tasks T045 through T048 strictly adhering to specs/001-central-auth-recovery/tasks.md.
- Ensure full test suite passes (mvn clean verify) and quickstart scenarios succeed.
- Create 1:1 atomic commits per task with format "T###: description".
- Push the branch and open a GitHub Pull Request to 001-central-auth-recovery referencing Issue #7.
```
"-