# Lista de control de despliegue e infraestructura cloud: despliegue de EasyEat en Render con Docker, CORS, health check, PostgreSQL en Neon y sincronización de URLs públicas

**Propósito**: Puerta de pase a producción (Release Gate) para auditar la calidad, completitud, claridad, consistencia y cobertura de los requisitos de infraestructura, empaquetado, red y despliegue cloud antes de la implementación técnica.  
**Fecha de creación**: 10/11/2026  
**Especificación**: [spec.md](file:///f:/ALL/PROGRAMACION/Proyectos/SDD/sdd%20-%20gdsi%20-%20grupo%2012/specs/017-render-deploy-docker/spec.md)

**Nota**: Esta lista de control personalizada fue generada mediante el comando `/speckit-checklist` sobre el contexto de la feature Spec 017.  
**Propiedad de revisión**: Este artefacto pertenece al revisor técnico como instrumento de auditoría de calidad de requisitos ("Unit Tests for Requirements"). Marcar un ítem con `[x]` únicamente cuando el revisor determine que el criterio de calidad del requisito está plenamente satisfecho en la especificación.  
**Semántica de marcado**: `[x]` significa que el requisito ha sido evaluado y satisface los estándares de redacción, completitud y verifiabilidad. No significa que el código de implementación esté terminado.

---

## Calidad de requisitos de empaquetado y contenedorización (Docker Backend y Frontend)

- [x] CHK001 ¿Están completamente especificadas las etapas de compilación (Maven/JDK 21) y de ejecución (JRE 21 Alpine) para el backend, y sus contrapartes (Node/Nginx) para el frontend? [Completeness, Spec §FR-001, §FR-018]
- [x] CHK002 ¿Se definen con exactitud los parámetros de la JVM (`-XX:+UseContainerSupport` y `-XX:MaxRAMPercentage=75.0`) requeridos para gobernar el consumo de memoria en contenedores con límite de 512 MB? [Clarity, Spec §FR-003, §AS-2]
- [x] CHK003 ¿Está explícitamente requerida la exclusión de código fuente, dependencias de desarrollo y herramientas de build en la imagen final de runtime del backend? [Completeness, Spec §FR-004, §AS-3]
- [x] CHK004 ¿Se especifica la identidad de sistema no privilegiada (usuario no-root `easyeat`) bajo la cual debe ejecutarse el proceso Java en la imagen final Alpine? [Security, Spec §FR-002, Clarification Q9]
- [x] CHK005 ¿Se contempla la especificación de archivos `.dockerignore` para backend y frontend a fin de evitar el copiado accidental de directorios locales voluminosos (`node_modules`, `target`, `.git`) a las imágenes de Docker? [Build Hygiene, Spec §FR-004, §FR-018, Clarification Q10]
- [x] CHK006 ¿Es objetivamente medible y verificable el criterio de éxito que certifica la ausencia de herramientas de compilación y código fuente en el contenedor productivo? [Measurability, Spec §SC-001]

---

## Calidad de requisitos del servidor web Nginx y enrutamiento SPA

- [x] CHK007 ¿Está definida de forma unívoca la directiva de enrutamiento virtual de Nginx (`try_files $uri $uri/ /index.html =404;`) para impedir errores HTTP 404 ante recargas en rutas internas? [Clarity, Spec §FR-019, §AS-18]
- [x] CHK008 ¿Se especifican con precisión los encabezados HTTP `Cache-Control` distinguiendo `index.html` (`no-cache`) de los recursos estáticos versionados con hash en `/assets/` (`public, max-age=31536000, immutable`)? [Completeness, Spec §FR-019, Clarification Q5]
- [x] CHK009 ¿Está claramente especificado el mecanismo de resolución de puerto dinámico en Nginx dando prioridad a la variable `PORT` con fallback al puerto `80` (`${PORT:-80}`)? [Clarity, Spec §FR-021, §AS-20, Clarification Q2]
- [x] CHK010 ¿Se define explícitamente el paso del argumento de construcción `ARG VITE_API_BASE_URL` para inyectar la URL pública del backend durante la compilación de la SPA? [Coverage, Spec §FR-020, §AS-19]
- [x] CHK011 ¿Se especifica el comportamiento esperado de la SPA en caso de que no se proporcione `VITE_API_BASE_URL` durante el build? [Edge Case, Spec §Casos Borde]

---

## Calidad de requisitos de seguridad de red y CORS dinámico

- [x] CHK012 ¿Están documentados todos los métodos HTTP autorizados (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`), cabeceras comodín y la habilitación de credenciales en el bean `CorsConfigurationSource`? [Completeness, Spec §FR-005, §AS-4]
- [x] CHK013 ¿Se especifica la regla de sanitización de orígenes (recorte de espacios en blanco y remoción de barras finales) en `APP_CORS_ALLOWED_ORIGINS` para evitar rechazos sintácticos por parte del navegador? [Clarity, Spec §FR-006, Clarification Q6]
- [x] CHK014 ¿Está cuantificado con una métrica temporal explícita el tiempo de retención de preflight mediante `Access-Control-Max-Age` (3600 segundos)? [Measurability, Spec §FR-005, Clarification Q8]
- [x] CHK015 ¿Se especifica el comportamiento de fallback a `http://localhost:3000` ante la ausencia o valor vacío de `APP_CORS_ALLOWED_ORIGINS` para preservar retrocompatibilidad local? [Consistency, Spec §FR-007, §AS-6]
- [x] CHK016 ¿Está explícitamente indicado que las solicitudes preflight HTTP `OPTIONS` dirigidas a cualquier endpoint no deben exigir autenticación JWT ni tokens de sesión? [Security, Spec §FR-009, §AS-8]

---

## Calidad de requisitos de puerto dinámico y observabilidad de salud (Health Check)

- [x] CHK017 ¿Se encuentra unívocamente especificada la adaptación del servidor web embebido mediante `server.port: ${PORT:8080}` en `application.yml`? [Clarity, Spec §FR-010, §AS-9]
- [x] CHK018 ¿Se define la inclusión de la dependencia oficial `spring-boot-starter-actuator` en el `pom.xml` del backend? [Completeness, Spec §FR-011, §AS-10]
- [x] CHK019 ¿Está formalmente establecida la exención de autenticación para el endpoint `/actuator/health` en la cadena de seguridad de Spring Security? [Security, Spec §FR-012, §AS-11]
- [x] CHK020 ¿Son objetivamente testeables el código de estado (200 OK) y el contenido del cuerpo JSON (`{"status":"UP"}`) retornados por el health check al completar el inicio? [Measurability, Spec §FR-013, §AS-12]
- [x] CHK021 ¿Están contemplados los requisitos de tolerancia operativa y tiempos de gracia ante cold starts de la JVM en el plan gratuito de Render antes de reportar falla de inicio? [Non-Functional, Spec §Casos Borde]

---

## Calidad de requisitos de base de datos en Neon y migraciones Flyway

- [x] CHK022 ¿Se exige explícitamente la utilización de la cadena de conexión directa a Neon (sin `-pooler`) para asegurar compatibilidad con los bloqueos consultivos (`pg_advisory_lock`) de Flyway? [Consistency, Spec §FR-014, Clarification Q7]
- [x] CHK023 ¿Se define como obligatoria la negociación cifrada SSL (`sslmode=require`) en la cadena JDBC de conexión a PostgreSQL? [Security, Spec §FR-014, §AS-13]
- [x] CHK024 ¿Está estrictamente acotada la provisión de credenciales de base de datos a variables de entorno, prohibiendo su presencia fija en el repositorio? [Security, Spec §FR-015, §AS-14]
- [x] CHK025 ¿Se detalla la totalidad de scripts de migración versionados (`V1` a `V19`) y repetibles (`R__seed_dev_*`) que deben ejecutarse exitosamente contra Neon? [Completeness, Spec §FR-016, §FR-017, Clarification Q1]
- [x] CHK026 ¿Se define el comportamiento de resiliencia y reconexión automática ante interrupciones transitorias de red con Neon mediante el pool HikariCP? [Resilience, Spec §FR-014, Clarification Q11]

---

## Calidad de requisitos de sincronización de URLs públicas

- [x] CHK027 ¿Se especifica con precisión que `MERCADOPAGO_FRONTEND_BASE_URL` recibe el origen raíz público (sin barra final) y que el backend construye el retorno anexando `/checkout/payment-return`? [Clarity, Spec §FR-022, Clarification Q3]
- [x] CHK028 ¿Está especificada la disponibilidad pública bajo HTTPS del endpoint de webhook de Mercado Pago (`/api/v1/payments/mercadopago/webhook`) exento de autenticación JWT? [Coverage, Spec §FR-023, §AS-23]
- [x] CHK029 ¿Se especifican las rutas completas de acción requeridas en las variables de correo (`APP_PASSWORD_RESET_BASE_URL` y `APP_EMAIL_VERIFICATION_BASE_URL`)? [Consistency, Spec §FR-024, §AS-24]
- [x] CHK030 ¿Se establece la prohibición absoluta e invariante de referencias a `localhost` en redirecciones y correos salientes en el entorno cloud desplegado? [Invariants, Spec §Validaciones e Invariantes]

---

## Trazabilidad y completitud de escenarios de borde

- [x] CHK031 ¿Están cubiertos los escenarios negativos de fallo de contexto Spring, OOM por cuota de memoria, conexión rechazada por falta de SSL y scripts de migración fallidos? [Coverage, Spec §Casos Borde, §Comportamiento ante errores]
- [x] CHK032 ¿Cuenta cada requisito funcional con al menos un escenario de aceptación unívoco en formato Dado/Cuando/Entonces correlacionado en la matriz de trazabilidad? [Traceability, Spec §Matriz de trazabilidad]
- [x] CHK033 ¿Se encuentran claramente delimitadas las exclusiones de alcance (pipelines CI/CD avanzados, balanceadores externos, clustering y auto-escalado horizontal)? [Completeness, Spec §Fuera de alcance]

---

## Notas

- Marcar los ítems con `[x]` únicamente cuando la revisión confirme que el criterio de calidad del requisito está satisfecho.
- Dejar los ítems sin marcar (`[ ]`) mientras requieran clarificación, ajustes o evaluación pendiente por parte del revisor.
- `/speckit-implement` toma el estado de las listas de control como compuerta de pase y no modifica los marcadores.
- `checklists/requirements.md` mantiene un ciclo de vida independiente administrado por `/speckit-specify` y `/speckit-clarify`.
- Los 33 ítems de esta lista de control auditan la calidad intrínseca de los requisitos de la Spec 017 como Suite de Pruebas Unitarias para Requisitos de Despliegue.

