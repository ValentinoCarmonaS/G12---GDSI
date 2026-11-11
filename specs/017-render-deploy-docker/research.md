# Technical Research & Decision Log: 017 Render Deploy Docker

**Branch**: `017-render-deploy-docker` | **Fecha**: 10/11/2026 | **Spec**: [spec.md](./spec.md)  
**Estado**: Completado  

---

## 1. Introducción

El presente documento consolida las decisiones arquitectónicas, de infraestructura y de configuración para el despliegue de EasyEat en la plataforma Render conectada a PostgreSQL en Neon. Resuelve de forma exhaustiva los requerimientos técnicos de las historias de usuario `HU-01` a `HU-06`, garantizando la estabilidad operativa sobre instancias con recursos restringidos (512 MB RAM en plan gratuito) y asegurando compatibilidad total con el desarrollo local.

---

## 2. Registro de Decisiones Técnicas

### 2.1. Composición multi-etapa y usuario no privilegiado en Dockerfile Backend (HU-01 / Q1, Q9, Q10)

- **Decisión**:
  - Implementar un `Dockerfile` multi-etapa en `easyeat/backend/` con dos fases:
    1. **Etapa de build**: `maven:3.9.9-eclipse-temurin-21-alpine` (o `eclipse-temurin:21-jdk-alpine` con Maven instalado). Se copia `pom.xml`, se descargan dependencias en capa cacheada (`mvn dependency:go-offline -B` o empaquetado directo) y se compila el ejecutable omitiendo tests unitarios (`mvn clean package -DskipTests -Djacoco.skip=true`).
    2. **Etapa de runtime**: `eclipse-temurin:21-jre-alpine`. Se crea un grupo y usuario de sistema sin privilegios (`addgroup -S easyeat && adduser -S easyeat -G easyeat -s /bin/sh -D`).
  - Se define un directorio de trabajo `/app` asignando propiedad `chown -R easyeat:easyeat /app`.
  - Se copia exclusivamente el artefacto final `target/*.jar` como `/app/app.jar`.
  - Se conmuta la ejecución al usuario no privilegiado (`USER easyeat`).
- **Rationale**:
  - Alpine Linux provee una huella mínima (menos de 180 MB de imagen final), reduciendo drásticamente los tiempos de descarga y arranque en Render.
  - Ejecutar el proceso Java como `easyeat` (no root) neutraliza ataques de escape de contenedor o escalada de privilegios en el host de orquestación.
  - La segregación multi-etapa garantiza que la imagen de producción no contenga código fuente (`.java`), herramientas de compilación (`mvn`), tests ni repositorios locales (`~/.m2`), cumpliendo AS-1 y AS-3.
- **Alternativas consideradas**:
  - *Distroless (`gcr.io/distroless/java21`)*: Descartada porque dificulta operaciones de diagnóstico básicas en contenedores (falta de shell) y Alpine ya ofrece un tamaño equivalente con soporte de shell para healthcheck y troubleshooting.
  - *Ubuntu / Debian base (`eclipse-temurin:21-jre`)*: Descartada por triplicar el tamaño de la imagen final (> 450 MB vs < 180 MB), aumentando el tiempo de cold start en Render.

---

### 2.2. Gobernanza de memoria JVM en contenedores restringidos (HU-01 / CA-01.2, AS-2)

- **Decisión**:
  - Configurar en el `ENTRYPOINT` del contenedor backend los flags:
    ```sh
    exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar /app/app.jar
    ```
- **Rationale**:
  - Las instancias de capa gratuita de Render limitan la memoria física a 512 MB. Si el proceso Java excede ese umbral, el kernel emite `SIGKILL` (código de salida 137, OOMKilled).
  - `-XX:+UseContainerSupport` permite a la JVM leer los límites de cgroups v1/v2 del contenedor en lugar de la memoria física del host virtual.
  - `-XX:MaxRAMPercentage=75.0` asigna como máximo el 75% del límite del contenedor al Heap Space (~384 MB de 512 MB), reservando el 25% restante (~128 MB) para Metaspace, hilos de ejecución (Thread Stack), código JIT (CodeCache) y estructuras internas del sistema operativo.
- **Alternativas consideradas**:
  - *Fijar `-Xmx384m` estático*: Descartada porque carece de adaptabilidad si la instancia se escala verticalmente a 1 GB o 2 GB en planes superiores.
  - *Omitir flags*: Descartada porque la JVM por defecto asigna el 25% de la memoria del host o 1/4 de la memoria física no restringida, provocando OOMKilled inmediato al superar los 512 MB de cgroups.

---

### 2.3. Bean de CORS dinámico, sanitización sintáctica y preflight (HU-02 / Q6, Q8, AS-4 a AS-8)

- **Decisión**:
  - Incorporar en `SecurityConfig.java` la definición del bean `CorsConfigurationSource`:
    ```java
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:3000}") String allowedOriginsConfig) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("/+$", ""))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        if (origins.isEmpty()) {
            origins = List.of("http://localhost:3000");
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // 1 hora de expiración para preflight

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
    ```
  - Enlazar la configuración en `SecurityFilterChain` mediante `.cors(cors -> cors.configurationSource(corsConfigurationSource(allowedOriginsConfig)))`.
  - Exponer la propiedad en `application.yml`:
    ```yaml
    app:
      cors:
        allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000}
    ```
- **Rationale**:
  - La normalización sintáctica (`trim()` y `replaceAll("/+$", "")`) resuelve Q6/A6, evitando fallos de coincidencia cuando los operadores introducen espacios accidentales o barras diagonales finales en el dashboard de Render.
  - `Access-Control-Max-Age = 3600` (Q8/A8) instruye al navegador a retener las respuestas a solicitudes `OPTIONS` durante una hora, eliminando preflights redundantes antes de cada llamada REST y reduciendo la latencia percibida por el usuario.
  - `allowCredentials = true` permite el intercambio seguro de encabezados de autorización y cookies cross-origin requeridos por la SPA.
- **Alternativas consideradas**:
  - *Anotaciones `@CrossOrigin` a nivel de controlador*: Descartada por dispersar la configuración en decenas de clases, vulnerar el principio de responsabilidad única e impedir una gestión centralizada y dinámica mediante variables de entorno.
  - *Filtro `CorsFilter` manual en servlet*: Descartada porque Spring Security ya provee integración nativa mediante `.cors()`, garantizando que las solicitudes `OPTIONS` se resuelvan antes de evaluar tokens JWT.

---

### 2.4. Adaptabilidad de puerto dinámico y Spring Boot Actuator Health (HU-03 / AS-9 a AS-12)

- **Decisión**:
  - Modificar `application.yml` para vincular el puerto HTTP del servidor embebido a la variable de entorno `PORT`:
    ```yaml
    server:
      port: ${PORT:8080}
    ```
  - Incorporar la dependencia `org.springframework.boot:spring-boot-starter-actuator` en `pom.xml`.
  - Configurar en `application.yml`:
    ```yaml
    management:
      endpoints:
        web:
          exposure:
            include: health
      endpoint:
        health:
          show-details: when-authorized
    ```
  - Declarar explícitamente en `SecurityConfig.java`:
    ```java
    .requestMatchers("/actuator/health").permitAll()
    ```
- **Rationale**:
  - Render asigna puertos aleatorios (por ejemplo, 10000) a cada contenedor en su red interna. La sintaxis `${PORT:8080}` toma el puerto dinámico si existe y mantiene `8080` como valor por omisión en entornos locales.
  - Exponer `/actuator/health` sin autenticación permite que la sonda de salud de Render verifique el estado del backend (`UP` con HTTP 200) para habilitar el enrutamiento de tráfico público.
  - Si la base de datos o el contexto colapsan, Actuator responde HTTP 503 con `DOWN`, instruyendo a Render a no enviar tráfico a una instancia no funcional.
- **Alternativas consideradas**:
  - *Crear un `@RestController` ad-hoc (`/health`)*: Descartada por reinventar la rueda; Actuator es el estándar oficial de Spring Boot, integra verificaciones automáticas del pool HikariCP y la base de datos, y provee semántica HTTP estándar (200 OK / 503 Service Unavailable).

---

### 2.5. Conexión directa a Neon, compatibilidad con Flyway y resiliencia ante scale-to-zero (HU-04 / Q7, Q11, AS-13 a AS-16)

- **Decisión**:
  - Configurar las propiedades de base de datos en `application.yml`:
    ```yaml
    spring:
      datasource:
        url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/easyeat}
        username: ${SPRING_DATASOURCE_USERNAME:easyeat}
        password: ${SPRING_DATASOURCE_PASSWORD:easyeat_dev_password}
        driver-class-name: org.postgresql.Driver
        hikari:
          connection-timeout: 30000
          validation-timeout: 5000
          minimum-idle: 1
          maximum-pool-size: 5
      flyway:
        enabled: true
        locations:
          - classpath:db/migration
          - classpath:db/dev
    ```
  - Exigir cadena de conexión directa a Neon (sin `-pooler`), admitiendo `?sslmode=require` en `SPRING_DATASOURCE_URL`.
- **Rationale**:
  - Neon provee endpoints directos y con pooler (PgBouncer). Flyway requiere soporte transaccional de bloqueos consultivos a nivel de sesión (`pg_advisory_lock`) para serializar migraciones; PgBouncer en modo transacción rompe estos bloqueos. Por tanto, la conexión directa (Q7/A7) es obligatoria.
  - Neon suspende las instancias inactivas en su plan gratuito (scale-to-zero tras 5 minutos). Cuando llega una petición, el servidor tarda entre 2 y 5 segundos en despertar. Fijar `connection-timeout: 30000` (30 segundos) en HikariCP evita que la aplicación falle por timeout durante el encendido del nodo de Neon (Q11/A11).
  - Mantener `classpath:db/migration` y `classpath:db/dev` garantiza la ejecución de las migraciones `V1` a `V19` y la inserción de las semillas `R__seed_dev_*` en bases de datos nuevas.
- **Alternativas consideradas**:
  - *Conexión mediante PgBouncer de Neon (`-pooler`)*: Rechazada taxativamente debido a que PgBouncer no preserva bloqueos de sesión de PostgreSQL, causando errores fatales durante el arranque de Flyway (`ERROR: lock pg_advisory_lock cannot be used in transaction pooling mode`).

---

### 2.6. Contenedorización frontend, resolución dinámica de puerto y enrutamiento SPA en Nginx (HU-05 / Q2, Q5, AS-17 a AS-20)

- **Decisión**:
  - Implementar un `Dockerfile` multi-etapa en `easyeat/frontend/`:
    1. **Etapa de build**: `node:20-alpine`. Se copian `package*.json`, se ejecuta `npm ci`, se recibe `ARG VITE_API_BASE_URL`, se exporta `ENV VITE_API_BASE_URL=$VITE_API_BASE_URL` y se ejecuta `npm run build`.
    2. **Etapa de runtime**: `nginx:alpine`. Se copia el directorio compilado `/app/dist` hacia `/usr/share/nginx/html`.
  - Configuración de Nginx (`nginx.conf`):
    ```nginx
    server {
        listen ${PORT};
        server_name localhost;

        root /usr/share/nginx/html;
        index index.html;

        # Compresión gzip para optimizar transferencia
        gzip on;
        gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;

        # Política de caché para activos inmutables de Vite
        location /assets/ {
            try_files $uri =404;
            add_header Cache-Control "public, max-age=31536000, immutable";
        }

        # Redirección SPA de rutas virtuales y caché de index.html
        location / {
            try_files $uri $uri/ /index.html;
            add_header Cache-Control "no-cache";
        }

        # Manejo de errores
        error_page 500 502 503 504 /50x.html;
        location = /50x.html {
            root /usr/share/nginx/html;
        }
    }
    ```
  - Para soportar la variable `${PORT}` dinámicamente con fallback a `80` (Q2/A2):
    La imagen oficial de Nginx (`nginx:alpine`) incluye el script nativo `/docker-entrypoint.d/20-envsubst-on-templates.sh`. Al colocar la plantilla en `/etc/nginx/templates/default.conf.template`, Nginx evalúa automáticamente las variables de entorno en el arranque y genera `/etc/nginx/conf.d/default.conf`.
    Para garantizar el fallback seguro a `80` si `PORT` no está seteada, se define un script de entrada liviano o se inicializa `ENV PORT=80` en el `Dockerfile`.
- **Rationale**:
  - `try_files $uri $uri/ /index.html` redirige todas las rutas virtuales (`/catalog`, `/checkout`, `/orders/123`) a `index.html` con código HTTP 200, permitiendo que React Router se encargue del enrutamiento del lado del cliente sin arrojar 404.
  - La política de caché (Q5/A5) evita el "cache lock": `index.html` siempre se valida con el servidor (`no-cache`), descargando la versión más reciente tras cada deploy, mientras que los archivos en `/assets/` usan hashes inmutables y se cachean por 1 año (`max-age=31536000, immutable`).
- **Alternativas consideradas**:
  - *Servir el frontend mediante `serve` en un contenedor Node*: Descartada por alto consumo de memoria RAM (> 120 MB en Node vs < 15 MB en Nginx Alpine) y menor rendimiento sirviendo estáticos.

---

### 2.7. Sincronización de URLs públicas y disponibilidad de Webhooks (HU-06 / Q3, AS-21 a AS-24)

- **Decisión**:
  - Mantener la convención desacoplada de variables de entorno:
    1. `MERCADOPAGO_FRONTEND_BASE_URL`: Origen raíz del frontend desplegado (`https://easyeat-frontend.onrender.com`). El servicio `MercadoPagoGatewayAdapter.java` ya concatena internamente `/checkout/payment-return`.
    2. `APP_PASSWORD_RESET_BASE_URL`: URL pública completa con ruta (`https://easyeat-frontend.onrender.com/reset-password`).
    3. `APP_EMAIL_VERIFICATION_BASE_URL`: URL pública completa con ruta (`https://easyeat-frontend.onrender.com/verificar-email`).
  - Habilitar explícitamente en `SecurityConfig.java`:
    ```java
    .requestMatchers(
            "/api/v1/payments/*/webhook",
            "/api/v1/payments/mercadopago/webhook"
    ).permitAll()
    ```
- **Rationale**:
  - Mercado Pago Checkout Pro exige URLs de retorno accesibles por el navegador del comensal. Si apuntan a `localhost`, el comensal no puede completar el retorno de pago.
  - Los webhooks de Mercado Pago son invocados directamente por los servidores externos de Mercado Pago, por lo que deben ser accesibles públicamente vía HTTPS y sin requerir autenticación JWT. La autenticidad se valida mediante la firma HMAC `x-signature` implementada en `WebhookSignatureValidator`.
- **Alternativas consideradas**:
  - *Construir todas las URLs a partir de una única variable base*: Descartada para evitar refactorizar los componentes de correo ya probados y validados en las Specs 001 y 014.

---

### 2.8. Higiene de compilación con `.dockerignore` (HU-01, HU-05 / Q10)

- **Decisión**:
  - Crear `easyeat/backend/.dockerignore`:
    ```gitignore
    target/
    .git/
    .gitignore
    .idea/
    *.iml
    .vscode/
    .env
    .env.*
    *.md
    checklists/
    ```
  - Crear `easyeat/frontend/.dockerignore`:
    ```gitignore
    node_modules/
    dist/
    .git/
    .gitignore
    .idea/
    .vscode/
    .env
    .env.*
    coverage/
    *.md
    ```
- **Rationale**:
  - Previene que directorios pesados (`node_modules/` o `target/`) se transfieran al contexto de compilación del daemon de Docker, acelerando drásticamente el tiempo de build de minutos a segundos y garantizando reproducibilidad.

---

### 2.9. Lock pesimista en confirmación de checkout y estabilización de integración (11/11/2026)

- **Decisión**:
  - Incorporar `OrderRepository.findByIdForUpdate(UUID id)` con `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE` sobre `orders`) y utilizarlo en los tres métodos transaccionales de `CheckoutConfirmationServiceImpl`: `confirmPayment`, `registerPendingPayment` y `confirmZeroAmountOrder`, incorporando además el guard de idempotencia (`order.getStatus() == OrderStatus.IN_PROGRESS`) en `confirmZeroAmountOrder`.
  - Mantener el orden estricto de adquisición de locks `Order -> Ingredient (ASC)` sin ciclos respecto de `CheckoutPaymentService` (`Establishment -> Ingredient`), `OrderService` (`Establishment`) y `StockAdjustmentService` (`Ingredient -> IngredientBatch`).
  - Registrar la migración idempotente `V24__nullable_stock_audit_user_id.sql` (`ALTER TABLE stock_audit_entries ALTER COLUMN user_id DROP NOT NULL;`) para que las deducciones automáticas de `StockDeductionServiceImpl` registren `user_id = NULL` (`adminName = "System"`) en lugar de violar `stock_audit_entries_user_id_fkey` con el UUID sintético `00000000-0000-0000-0000-000000000000`.
  - Incluir `IN_PROGRESS`, `PENDING_ACCREDITATION` y `READY_FOR_PICKUP` en `OrderRepository.countActiveOrdersBySlot` junto con `CONFIRMED`, `PAID` y `PENDING_PAYMENT` no expirado.
- **Rationale**:
  - Bajo `Isolation.READ_COMMITTED`, las lecturas sin lock permitían que múltiples hilos concurrentes (por ejemplo, webhook simultáneo con retorno de pasarela) leyeran `PENDING_PAYMENT` antes del primer commit, ejecutando múltiples descuentos de stock sobre `Ingredient` mientras `KdsQueueServiceImpl` devolvía el ticket ya insertado y ocultaba la duplicación devolviendo `idempotentReplay = false`.
  - Con `SELECT ... FOR UPDATE` sobre `orders`, las transacciones competidoras esperan el commit de la primera y PostgreSQL `READ COMMITTED` reevalúa la tupla comprometida (`IN_PROGRESS`), garantizando exactamente una deducción física de stock y respuestas `idempotentReplay = true` para los reintentos concurrentes.
  - Hacer `stock_audit_entries.user_id` nullable evita crear filas sintéticas en `users` sujetas a `chk_users_role_tenant` y aprovecha el mapeo `"System"` ya soportado en `StockAdjustmentService.getAuditHistory`.
- **Alternativas consideradas**:
  - *Optimistic locking (`@Version` en `orders` con captura de `OptimisticLockException`)*: Descartada frente a `findByIdForUpdate` porque obligaría a reabrir una transacción nueva para consultar el ticket KDS ya creado, mientras `findByIdForUpdate` resuelve el replay idempotente dentro de la misma llamada sin error transitorio.
  - *Usuario sintético `00000000-0000-0000-0000-000000000000` en `users`*: Descartada para mantener la tabla `users` restringida a cuentas reales.

---

## 3. Resumen de Parámetros y Valores por Defecto

| Parámetro / Variable | Componente | Valor Render / Producción | Fallback Local | Propósito |
|---|---|---|---|---|
| `PORT` | Backend / Frontend | Asignado por Render (ej. 10000) | `8080` (Backend), `80` (Frontend) | Puerto de escucha de red |
| `SPRING_DATASOURCE_URL` | Backend | `jdbc:postgresql://<neon-host>/easyeat?sslmode=require` | `jdbc:postgresql://localhost:5432/easyeat` | Conexión directa JDBC a Neon con SSL |
| `SPRING_DATASOURCE_USERNAME` | Backend | Credencial Neon | `easyeat` | Usuario de base de datos |
| `SPRING_DATASOURCE_PASSWORD` | Backend | Credencial Neon | `easyeat_dev_password` | Contraseña de base de datos |
| `APP_CORS_ALLOWED_ORIGINS` | Backend | `https://easyeat-frontend.onrender.com` | `http://localhost:3000` | Orígenes web autorizados para interactuar con la API |
| `VITE_API_BASE_URL` | Frontend (Build) | `https://easyeat-backend.onrender.com/api/v1` | `/api/v1` | URL base de la API REST consumida por la SPA |
| `MERCADOPAGO_FRONTEND_BASE_URL` | Backend | `https://easyeat-frontend.onrender.com` | `http://localhost:3000` (o `http://localhost:5173`) | Origen raíz para retornos de Checkout Pro |
| `APP_PASSWORD_RESET_BASE_URL` | Backend | `https://easyeat-frontend.onrender.com/reset-password` | `http://localhost:3000/reset-password` | Enlace para reseteo de claves en correos |
| `APP_EMAIL_VERIFICATION_BASE_URL`| Backend | `https://easyeat-frontend.onrender.com/verificar-email`| `http://localhost:3000/verificar-email`| Enlace para validación de casillas en correos |

