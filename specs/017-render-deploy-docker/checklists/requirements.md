# Control de calidad de la especificación: despliegue de EasyEat en Render con Docker, CORS, health check, PostgreSQL en Neon y sincronización de URLs públicas

**Propósito**: Validar la completitud y calidad de la especificación funcional antes de avanzar a la fase de planificación técnica (`speckit-plan`).  
**Fecha de creación**: 10/11/2026  
**Especificación**: [spec.md](file:///f:/ALL/PROGRAMACION/Proyectos/SDD/sdd%20-%20gdsi%20-%20grupo%2012/specs/017-render-deploy-docker/spec.md)

## Calidad del contenido

- [x] Tratamiento riguroso de las tecnologías como entidades del dominio de despliegue e infraestructura (Docker, Nginx, Render, Neon, Spring Boot, Actuator, Flyway) sin invención de detalles de implementación no descritos en las historias fuente.
- [x] Foco claro en la operatividad del sistema, valor para el operador, navegabilidad sin fallos 404 para el comensal y continuidad del negocio en producción.
- [x] Redacción profesional en español neutral para operadores, desarrolladores y stakeholders técnicos.
- [x] Todas las secciones obligatorias completadas con rigor, consistencia y exhaustividad.

## Completitud de requisitos

- [x] Sin marcadores `[NEEDS CLARIFICATION]` pendientes; acuerdos formales de la sesión de Grill-Me documentados e integrados (resolución de migraciones V1–V19, puerto dinámico en Nginx con fallback `${PORT:-80}`, formato de URLs públicas y priorización unificada P1).
- [x] Requisitos funcionales testeables, unívocos y con numeración correlativa (`FR-001` a `FR-024`).
- [x] Criterios de éxito medibles y verificables (`SC-001` a `SC-010`).
- [x] Todos los escenarios de aceptación definidos en formato Dado / Cuando / Entonces (`AS-1` a `AS-24`), con trazabilidad directa y unívoca a las historias fuente `HU-01` a `HU-06` y sus 24 criterios de aceptación (`CA-01.1` a `CA-06.4`).
- [x] Casos borde y comportamiento ante errores contemplados exhaustivamente (OOM por memoria en Render, fallos de build, variables CORS ausentes o mal formadas, preflight sin auth, puerto `PORT` ausente, health check degradado, conexión sin SSL a Neon, credenciales hardcodeadas, migraciones fallidas, recarga de rutas SPA con 404, URLs con referencias a localhost en producción y cold start en Render).
- [x] Alcance claramente delimitado, explicitando exclusiones estrictas (CI/CD avanzado, auto-scaling, balanceadores dedicados externos, monitoreo fuera de Actuator, backups fuera de Neon, secretos fuera de variables de entorno, entornos adicionales de staging y nuevas migraciones fuera de V1–V19).
- [x] Supuestos y dependencias de upstream formalmente identificados (Specs 001 a 016).

## Preparación de la feature (Readiness)

- [x] Todos los requisitos funcionales cuentan con criterios de aceptación verificables.
- [x] Los escenarios de usuario cubren de punta a punta la contenedorización, red, seguridad, base de datos, enrutamiento web y sincronización de URLs.
- [x] La feature cumple con los resultados cuantificables de los Criterios de Éxito (100% de builds limpios sin código fuente, 100% de respeto a cuota de RAM JVM, 100% de preflights OPTIONS autorizados, 0 bloqueos CORS, 100% de respuestas 200 en health check, 100% de migraciones V1–V19 aplicadas contra Neon, 0 errores 404 en rutas internas de SPA, 0 referencias a localhost en pasarela o correos).
- [x] Cumple con la Constitución del proyecto (Principios I, II, III, IV, V, VII y X: vertical slice completa de backend y frontend de infraestructura).

## Notas

- Los 15 ítems de control de calidad fueron verificados y validados exitosamente (15/15).
- Acuerdos clave consolidados en la especificación (10/11/2026):
  1. **Alcance de migraciones de base de datos (HU-04 / CA-04.4)**: Se fija formalmente la ejecución de `V1` a `V19` junto con los scripts repetibles `R__seed_dev_*`, alineando la especificación con el commit `fc34dde9` preexistente en el repositorio.
  2. **Resolución de puerto en Nginx (HU-05 / CA-05.4)**: Se define la resolución de puerto dinámica dando prioridad a la variable `PORT` inyectada por Render, con fallback automático a `80` (`${PORT:-80}`).
  3. **Convención de URLs públicas (HU-06)**: Se preserva la convención existente del backend: `MERCADOPAGO_FRONTEND_BASE_URL` como origen raíz del frontend sin barra final, y `APP_PASSWORD_RESET_BASE_URL` y `APP_EMAIL_VERIFICATION_BASE_URL` con sus rutas completas de acción.
  4. **Priorización P1 unificada**: Las seis historias (`HU-01` a `HU-06`) se establecen como P1 por constituir condiciones necesarias e indispensables para la viabilidad técnica y operativa de la plataforma en producción.
  5. **Política de Cache-Control en Nginx (HU-05)**: `no-cache` para `index.html` (revalidación inmediata) y caché inmutable de 1 año para recursos con hash en `/assets/`.
  6. **Sanitización de orígenes CORS (HU-02)**: Eliminación automática de espacios (`trim`) y barras diagonales finales (`/`) en `APP_CORS_ALLOWED_ORIGINS`.
  7. **Conexión directa a Neon (HU-04)**: Uso de cadena directa sin `-pooler` para soporte pleno de `pg_advisory_lock` en Flyway con pool local HikariCP.
  8. **Caché de preflight en CORS (HU-02)**: Fijación de `Access-Control-Max-Age: 3600` (1 hora) para reducir el 50% de llamadas HTTP redundantes.
  9. **Seguridad en contenedor backend (HU-01)**: Creación y uso de usuario de sistema sin privilegios (`easyeat`) en runtime Alpine.

