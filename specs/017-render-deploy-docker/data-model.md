# Data & Deployment Model: 017 Render Deploy Docker

**Branch**: `017-render-deploy-docker` | **Fecha**: 10/11/2026 | **Spec**: [spec.md](./spec.md)  
**Estado**: Completado  

---

## 1. Naturaleza del Modelo en Spec 017

La especificación 017 es una iteración de **infraestructura y despliegue** que no introduce nuevas tablas relacionales ni altera las entidades del dominio de negocio (`User`, `Dish`, `Order`, `Payment`, etc.) introducidas en las Specs 001 a 016.

En consecuencia, el modelo de datos para esta característica se adapta formalmente a un **Modelo de Despliegue e Infraestructura**. El documento canónico y detallado se encuentra registrado en:

👉 **[deployment-model.md](./deployment-model.md)**

---

## 2. Resumen de Entidades Operativas

### 2.1. Artefactos de Empaquetado
- **Backend Dockerfile** (`easyeat/backend/Dockerfile`): Construcción multi-etapa (Maven JDK 21 -> Alpine JRE 21), usuario no root `easyeat`, flags JVM (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`).
- **Backend .dockerignore** (`easyeat/backend/.dockerignore`): Exclusión de `target/`, `.git/`, `.idea/`, artefactos de build.
- **Frontend Dockerfile** (`easyeat/frontend/Dockerfile`): Construcción multi-etapa (Node 20 Alpine -> Nginx Alpine), inyección de `ARG VITE_API_BASE_URL`.
- **Frontend .dockerignore** (`easyeat/frontend/.dockerignore`): Exclusión de `node_modules/`, `dist/`, `.git/`.
- **Configuración Nginx** (`easyeat/frontend/nginx.conf`): Servidor estático con directiva `try_files $uri $uri/ /index.html =404;`, resolución de variable `${PORT:-80}` y política de caché HTTP.

### 2.2. Esquema Relacional Persistente (Estado en Neon)
Las migraciones ejecutadas al inicio del backend en Neon corresponden a la secuencia completa existente tras integrar las Specs 015, 016 y C-11 junto con la estabilización de integración:
- Migraciones versionadas: `V1` a `V24` en `classpath:db/migration` (incluyendo `V24__nullable_stock_audit_user_id.sql`, que habilita `user_id NULL` en `stock_audit_entries` para deducciones automáticas sin actor humano).
- Scripts repetibles de inicialización y datos semilla: `R__seed_dev_users.sql`, `R__seed_dev_establishments.sql`, `R__seed_dev_dishes_and_dietary.sql` en `classpath:db/dev`.

Para consultar la matriz completa de variables de entorno, topología de red, flujos de inicialización y validación de invariantes, remitirse íntegramente a [deployment-model.md](./deployment-model.md).

