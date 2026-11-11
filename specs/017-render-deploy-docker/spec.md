# Especificación funcional: despliegue de EasyEat en Render con Docker, CORS, health check, PostgreSQL en Neon y sincronización de URLs públicas

**Rama de la feature**: `017-render-deploy-docker`  
**Fecha de creación**: 10/11/2026  
**Estado**: Borrador  
**Entrada**: Descripción del usuario: "Feature: Despliegue de EasyEat en Render con Docker (backend y frontend), CORS, health check, PostgreSQL en Neon y sincronización de URLs públicas (Spec 017)"

---

## Contexto y trazabilidad funcional

- **Historias de usuario fuente obligatorias**:
  - `HU-01` — Contenedorización multi-etapa del backend (Docker) (Prioridad P1).
  - `HU-02` — Habilitación de CORS dinámico en Spring Security (Prioridad P1).
  - `HU-03` — Adaptabilidad de puerto dinámico y health check del backend (Prioridad P1).
  - `HU-04` — Conexión a PostgreSQL en Neon y migraciones completas (Prioridad P1).
  - `HU-05` — Contenedorización del frontend con Nginx y enrutamiento SPA (Prioridad P1).
  - `HU-06` — Sincronización de URLs públicas para Mercado Pago y correos (Prioridad P1).

- **Trazabilidad funcional y dependencias de producto**:
  - Esta especificación consolida la infraestructura y condiciones operativas necesarias para que las capacidades del producto desarrolladas en las iteraciones previas funcionen de manera integrada en un entorno cloud productivo:
    - Autenticación central y recuperación de contraseña vía correo electrónico (definido en Spec 001).
    - Roles operativos, guardas de navegación y auditoría de accesos (definido en Spec 002 y Spec 004).
    - Perfiles de comensal, restricciones dietarias y alérgenos (definido en Spec 003).
    - Gestión de recetas, costos y aprobación de platos (definido en Specs 005, 006 y 007).
    - Catálogo multi-establecimiento con filtros por estilo de vida y alérgenos (definido en Specs 008 y 011).
    - Carrito de compras individual (definido en Spec 009).
    - Configuración horaria y capacidades de atención del local (definido en Spec 010).
    - Motor de selección de franjas horarias y turnos de retiro (definido en Spec 012).
    - Control de stock crítico y pausa de platos por cocina (definido en Spec 013).
    - Checkout Pro con pasarela externa Mercado Pago, retorno e idempotencia (definido en Spec 014).
    - Pago individual, débito transaccional de stock por receta y despacho a cola KDS (definido en Spec 015).
    - Confirmación visual de pago, tiempo estimado en vivo y branding del restaurante (definido en Spec 016).
  - La presente Spec 017 es una iteración de infraestructura y despliegue continuo. No introduce reglas de negocio nuevas ni altera los flujos existentes; establece las condiciones de empaquetado ligero, conectividad segura cifrada, configuración dinámica de red, monitoreo de salud y sincronización de dominios públicos requeridas para la operación real del sistema en la plataforma Render y la base de datos gestionada Neon.

- **Límites de alcance estrictos**:
  - No se incorporan pipelines avanzados de CI/CD automatizado, balanceadores de carga dedicados externos a Render, mecanismos de auto-escalado horizontal, sistemas de métricas y telemetría adicionales fuera de Actuator health, agregación centralizada de logs (ej. ELK/Datadog), backups automáticos de base de datos fuera de Neon, gestión de secretos por fuera de variables de entorno de plataforma, entornos múltiples adicionales (staging/testing en cloud), certificados SSL personalizados fuera de los provistos por Render, proveedores de nube alternativos ni migraciones de esquema más allá de las existentes (`V1` a `V19` y semillas `R__seed_dev_*`).

---

## Clarificaciones

### Sesión 10/11/2026 (Grill-Me & Clarify)

- **Q1: Alcance de las migraciones de Flyway contra Neon (HU-04 / CA-04.4)**: En la historia fuente se menciona textualmente la ejecución de migraciones de `V1` a `V18`. No obstante, en el repositorio ya existe la migración `V19__add_dish_margin_percentage_and_backfill.sql` (renombrada para resolver una colisión de numeración). ¿Cómo debe fijarse el alcance de migraciones al iniciar el backend?  
  → **A1**: Se fija formalmente la ejecución de migraciones desde `V1` hasta `V19` junto con los scripts repetibles `R__seed_dev_*`. De este modo, la especificación refleja de manera fiel el estado real del repositorio y asegura que la base de datos en Neon contenga el esquema completo y actualizado del producto.

- **Q2: Adaptabilidad del puerto en Nginx para el servicio de frontend en Render (HU-05 / CA-05.4)**: Render asigna dinámicamente un puerto de escucha a través de la variable de entorno `PORT` (por ejemplo, 10000), mientras que las imágenes base de Nginx escuchan típicamente en el puerto 80 estático. ¿Qué mecanismo debe utilizar el contenedor de frontend?  
  → **A2**: Nginx debe resolver el puerto de escucha dinámicamente dando prioridad a la variable de entorno `PORT` inyectada por la plataforma de orquestación, aplicando un valor por defecto o fallback al puerto `80` (`${PORT:-80}`) en caso de no estar definida o encontrarse vacía. Este esquema es simétrico al del backend (`server.port: ${PORT:8080}`) y permite ejecutar la imagen sin cambios tanto en Render como en entornos locales.

- **Q3: Formato y granularidad de URLs públicas para pasarela de pagos y correos transaccionales (HU-06)**: ¿Cómo se componen las variables de entorno de URLs públicas en relación con la arquitectura de endpoints del backend?  
  → **A3**: Se preserva la convención preexistente del backend sin requerir refactorizaciones de código:
  1. `MERCADOPAGO_FRONTEND_BASE_URL` se configura con el origen raíz público del frontend (por ejemplo, `https://easyeat-frontend.onrender.com`), y el adaptador del backend concatena internamente la ruta fija `/checkout/payment-return`.
  2. `APP_PASSWORD_RESET_BASE_URL` y `APP_EMAIL_VERIFICATION_BASE_URL` se configuran con la URL pública completa que incluye su ruta de destino específica en el frontend (`https://<frontend>/reset-password` y `https://<frontend>/verificar-email`), a la cual el servicio de correos anexa el parámetro de consulta `?token=...`.

- **Q4: Priorización integral de historias de despliegue**: ¿Qué nivel de prioridad corresponde a cada historia de la especificación?  
  → **A4**: Todas las historias (`HU-01` a `HU-06`) se clasifican como **Prioridad P1**. HU-01 a HU-05 constituyen la base operativa mínima (sin ellas la aplicación no compila, no arranca, no conecta con la base de datos o sufre bloqueos de CORS y caídas por memoria). HU-06 es igualmente crítica para la continuidad operativa del negocio, dado que sin la sincronización de URLs públicas las transacciones monetarias de Mercado Pago y los procesos de activación y reseteo de claves quedarían interrumpidos al intentar redirigir a `localhost`.

- **Q5: Política de encabezados Cache-Control en Nginx para la SPA (HU-05 / CA-05.2)**: ¿Cómo debe estructurarse la política de caché HTTP para evitar que los navegadores de los usuarios queden atrapados en versiones obsoletas de la SPA tras un nuevo despliegue?  
  → **A5**: Se adopta el estándar de la industria para aplicaciones SPA con Vite: el archivo `index.html` se entrega con la directiva `Cache-Control: no-cache` (asegurando revalidación y actualización inmediata ante cada nuevo deploy), mientras que los recursos estáticos compilados bajo `/assets/` (cuyos nombres incorporan hashes de contenido inmutables generados por Vite) se sirven con caché pública prolongada (`Cache-Control: public, max-age=31536000, immutable`).

- **Q6: Sanitización y tolerancia sintáctica de orígenes en CORS (HU-02 / CA-02.2)**: Si la variable `APP_CORS_ALLOWED_ORIGINS` contiene espacios accidentales tras las comas o barras inclinadas finales (por ejemplo, `https://app.render.com/ , http://localhost:3000`), ¿cómo debe procesar la lista el backend?  
  → **A6**: El backend debe sanitizar y normalizar automáticamente cada origen provisto en la lista: recorta espacios en blanco iniciales y finales (`trim()`) y remueve cualquier barra diagonal final (`/`), garantizando que la comparación con el encabezado `Origin` del navegador (que nunca incluye barra final conforme a RFC 6454) no falle por desajustes sintácticos menores.

- **Q7: Tipo de conexión JDBC a Neon para migraciones y estabilidad (HU-04 / CA-04.1)**: Neon ofrece dos cadenas de conexión: directa y con `-pooler` (PgBouncer en modo transacción). ¿Cuál debe utilizar el backend para maximizar rendimiento y compatibilidad con Flyway?  
  → **A7**: Se establece el uso de la **conexión directa a Neon** (sin `-pooler`). Spring Boot gestiona su propio pool de conexiones ultrarrápido y persistente en memoria mediante HikariCP, eliminando la sobrecarga y latencia de intermediarios externos, y garantizando soporte nativo para los bloqueos consultivos de PostgreSQL (`pg_advisory_lock`) requeridos por Flyway para aplicar de forma segura las migraciones actuales (`V1` a `V19`) y cualquier migración futura (`V20+`) sin fallos de concurrencia.

- **Q8: Optimización de solicitudes preflight con Access-Control-Max-Age en CORS (HU-02 / CA-02.1)**: En las llamadas de la SPA hacia la API, el navegador emite una solicitud `OPTIONS` previa a cada petición no simple. ¿Debe fijarse un tiempo de retención de preflight en la configuración de CORS?  
  → **A8**: Se define explícitamente un tiempo de expiración de preflight de 3600 segundos (1 hora) mediante `Access-Control-Max-Age`. Esta directiva instruye a los navegadores web a cachear el resultado de la negociación de orígenes y métodos autorizados durante una hora, evitando el despacho redundante de solicitudes `OPTIONS` previas a cada interacción del comensal o staff, reduciendo el consumo de red y acelerando notablemente la respuesta de la interfaz en producción.

- **Q9: Seguridad del contenedor de backend mediante usuario no privilegiado (HU-01 / CA-01.1)**: En la imagen final de runtime del backend, ¿bajo qué identidad de sistema debe ejecutarse el proceso de la aplicación Spring Boot?  
  → **A9**: Se adopta el principio de mínimo privilegio en contenedores: la imagen final de Alpine JRE 21 crea un usuario y grupo de sistema sin privilegios de administración (por ejemplo, `easyeat:easyeat`) y asigna la ejecución del proceso Java a dicho usuario (`USER easyeat`), previniendo vectores de escalada de privilegios y cumpliendo con los estándares de seguridad de infraestructura en producción.

- **Q10: Higiene de construcción en Docker mediante archivos `.dockerignore` (HU-01, HU-05 / CHK005)**: ¿Deben definirse explícitamente archivos `.dockerignore` tanto en el backend como en el frontend?  
  → **A10**: Sí. Se incorporan formalmente los archivos `.dockerignore` en `easyeat/backend/` (excluyendo `target/`, `.git/`, `.idea/`, etc.) y en `easyeat/frontend/` (excluyendo `node_modules/`, `dist/`, `.git/`, etc.). Esto evita la transferencia inadvertida de gigabytes de archivos locales hacia el daemon de Docker, acelerando los tiempos de compilación y asegurando builds limpios y reproducibles.

- **Q11: Tolerancia operativa y resiliencia ante auto-suspensión de Neon y Render en capa gratuita (HU-04 / CHK026)**: Render y Neon operan bajo sus respectivos planes gratuitos, por lo cual se suspenden automáticamente ante inactividad (Render entra en reposo tras 15 minutos y Neon escala a cero tras 5 minutos). ¿Cómo debe gobernar la aplicación este comportamiento?  
  → **A11**: Se documenta formalmente que la suspensión por inactividad es el comportamiento esperado y legítimo de la infraestructura gratuita del proyecto. Para tolerarlo sin degradación ni fallas permanentes: (1) Render tolera el arranque en frío (cold start) del backend con tiempos de gracia adecuados en su health check; y (2) el pool HikariCP en Spring Boot gestiona la reconexión automática transparente ante el despertar de Neon (scale-to-zero wake up) validando las conexiones antes de su uso (`isValid()`) y permitiendo un tiempo de espera de conexión (`connection-timeout`) de hasta 30 segundos, sin requerir reinicios manuales ni arrojar errores de socket caído a los usuarios finales.

### Sesión 11/11/2026 (Validación previa a despliegue en Render y Neon)

- **Q12: Privilegios de la extensión `unaccent` en `V8__create_establishments.sql` contra Neon PostgreSQL (HU-04 / FR-017)**: En PostgreSQL gestionado en la nube (Neon), las extensiones como `unaccent` pertenecen al rol interno `cloud_admin`, por lo que `ALTER FUNCTION unaccent(text) IMMUTABLE;` falla con `SQLState 42501` al inicializar una base limpia. ¿Cómo debe definirse el índice funcional en `V8`?  
  → **A12**: Se reemplaza `ALTER FUNCTION unaccent(text) IMMUTABLE;` por una función wrapper inmutable propia del esquema (`immutable_unaccent(text)` que invoca `public.unaccent('public.unaccent', $1)`) y se crea el índice `idx_establishments_search` sobre `immutable_unaccent(LOWER(name))`, preservando la extensión `unaccent` y la compatibilidad completa tanto en Neon como en PostgreSQL local/Testcontainers.

- **Q13: Disponibilidad del bean `EmailSender` y tolerancia a falta de SMTP en el plan gratuito de Render (HU-06 / FR-024)**: `EmailChangeService` exige un bean `EmailSender`, pero `LoggingEmailSender` solo se activa bajo `@Profile("dev | local | test")`, y el plan gratuito de Render bloquea los puertos SMTP salientes `25`, `465` y `587`. ¿Cómo debe operar el despacho de correos de verificación en Render?  
  → **A13**: Se incorpora `SmtpEmailSender` con `@Profile("!dev & !local & !test")` y dependencia opcional `@Autowired(required = false) JavaMailSender`, y se agrega `spring.jpa.hibernate.ddl-auto: validate` junto con los parámetros SMTP opcionales en `application.yml` (omitiendo un `spring.mail.host` vacío por defecto para no activar `JavaMailSender` cuando no exista `SPRING_MAIL_HOST`). De este modo, el backend arranca sin errores tanto con `SPRING_PROFILES_ACTIVE=prod` como sin perfil definido y evita timeouts SMTP en el plan gratuito de Render hasta configurar un relay por HTTPS o puerto habilitado.

---

## Propósito

Asegurar el despliegue exitoso, liviano, confiable y seguro de la plataforma EasyEat (backend Spring Boot y frontend React SPA) sobre la infraestructura en la nube de Render conectada a una base de datos PostgreSQL gestionada en Neon con cifrado SSL obligatorio, garantizando la resolución dinámica de puertos, verificación de salud de la aplicación, compatibilidad de orígenes cruzados (CORS), enrutamiento virtual de navegación sin fallos 404, y la correcta redirección de pagos externos y enlaces transaccionales mediante URLs públicas sincronizadas.

---

## Actores

- **Desarrollador y operador de EasyEat**: Responsable de construir los artefactos de contenedor, configurar las variables de entorno en la nube, monitorear el despliegue y asegurar la consistencia entre los entornos local y de producción.
- **Plataforma de orquestación (Render)**: Sistema de infraestructura en la nube que ejecuta las imágenes Docker de backend y frontend, asigna dinámicamente el puerto de red vía variable `PORT`, consulta el endpoint de salud para determinar la disponibilidad del servicio y provee la terminación TLS pública.
- **Backend de EasyEat en la nube**: Servicio API REST Spring Boot empaquetado en contenedor que ejecuta migraciones Flyway contra Neon vía conexión segura, valida credenciales y atiende requerimientos transaccionales.
- **Comensal o miembro del staff**: Usuario final que interactúa con la interfaz web SPA desde su navegador, navega rutas internas, realiza compras con Mercado Pago o gestiona su cuenta.

---

## Escenarios de aceptación y pruebas

### User Story 1 — HU-01: Contenedorización multi-etapa del backend (Docker) (Prioridad: P1)

Como desarrollador y operador de EasyEat,  
quiero un Dockerfile multi-etapa en el directorio del backend,  
para empaquetar y ejecutar la aplicación Spring Boot en una imagen liviana optimizada para el límite de memoria de Render.

**Por qué esta prioridad**: Las instancias gratuitas y estándares de Render operan bajo límites estrictos de memoria RAM (típicamente 512 MB). Un build estándar sin optimización JVM ni segregación de capas excede la memoria disponible (OOMKilled) y arrastra dependencias de compilación innecesarias.

**Prueba independiente**: Se verifica construyendo la imagen con `docker build` en `easyeat/backend/`, inspeccionando que la imagen resultante esté basada en `eclipse-temurin:21-jre-alpine`, constatando la ausencia de código fuente `.java` o Maven dentro de la imagen, e iniciando el contenedor para certificar que el proceso Java arranca con los flags `-XX:+UseContainerSupport` y `-XX:MaxRAMPercentage=75.0`.

#### Escenarios de aceptación

- **AS-1 (Criterio fuente CA-01.1 — Compilación multi-etapa Maven y runtime JRE)**:  
  **Dado** que existe el archivo `Dockerfile` en el directorio `easyeat/backend/`,  
  **Cuando** el motor de Docker ejecuta la construcción de la imagen,  
  **Entonces** la etapa inicial compila y empaqueta el JAR ejecutable usando Maven con JDK 21 omitiendo la ejecución de pruebas unitarias, y la etapa final de ejecución utiliza una imagen base liviana `eclipse-temurin:21-jre-alpine` copiando únicamente el JAR generado.

- **AS-2 (Criterio fuente CA-01.2 — Configuración de memoria JVM adaptada a contenedores)**:  
  **Dado** que el contenedor del backend se inicia en un entorno con memoria restringida,  
  **Cuando** se ejecuta el comando de entrada (`ENTRYPOINT`),  
  **Entonces** la JVM inicia aplicando los parámetros `-XX:+UseContainerSupport` y `-XX:MaxRAMPercentage=75.0` para calcular dinámicamente el límite del heap space en base a la memoria disponible en el contenedor.

- **AS-3 (Criterio fuente CA-01.3 — Aislamiento y ligereza de la imagen de producción)**:  
  **Dado** que finalizó la construcción de la imagen Docker del backend,  
  **Cuando** se inspecciona el sistema de archivos del contenedor de runtime resultante,  
  **Entonces** no contiene archivos de código fuente (`.java`), herramientas de compilación (`mvn`), dependencias de desarrollo ni el repositorio local de dependencias de build.

---

### User Story 2 — HU-02: Habilitación de CORS dinámico en Spring Security (Prioridad: P1)

Como comensal o miembro del staff accediendo desde el navegador web,  
quiero que el backend autorice solicitudes cross-origin provenientes del dominio público del frontend en Render,  
para interactuar con la API (autenticación, catálogo, pedidos y checkout) sin bloqueos por política de mismo origen del navegador.

**Por qué esta prioridad**: Al desplegarse backend y frontend en servicios independientes con dominios distintos (por ejemplo, `easyeat-api.onrender.com` y `easyeat-app.onrender.com`), el navegador bloquea cualquier intento de login, consulta o pago si el backend no negocia explícitamente las cabeceras CORS en solicitudes preflight y peticiones reales.

**Prueba independiente**: Se puede validar ejecutando peticiones HTTP con método `OPTIONS` y cabeceras `Origin` correspondientes al dominio configurado, verificando que la respuesta retorne estado 200 OK con `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials: true` y métodos autorizados, así como verificando que sin variable de entorno tome por defecto `http://localhost:3000`.

#### Escenarios de aceptación

- **AS-4 (Criterio fuente CA-02.1 — Configuración integral del bean CORS)**:  
  **Dado** que la aplicación backend inicializa su contexto de seguridad en `SecurityConfig.java`,  
  **Cuando** se instancia el bean `CorsConfigurationSource`,  
  **Entonces** se configuran explícitamente los orígenes permitidos, los métodos HTTP `GET`, `POST`, `PUT`, `PATCH`, `DELETE` y `OPTIONS`, las cabeceras permitidas en comodín (`*`) y la habilitación de credenciales (`allowCredentials = true`).

- **AS-5 (Criterio fuente CA-02.2 — Resolución dinámica de orígenes permitidos)**:  
  **Dado** que la variable de entorno `APP_CORS_ALLOWED_ORIGINS` contiene una lista de dominios separados por comas (por ejemplo, `https://easyeat-frontend.onrender.com,http://localhost:3000`),  
  **Cuando** el backend procesa las solicitudes entrantes,  
  **Entonces** autoriza el acceso a cualquiera de los orígenes definidos en dicha propiedad.

- **AS-6 (Criterio fuente CA-02.3 — Fallback retrocompatible para desarrollo local)**:  
  **Dado** que la variable de entorno `APP_CORS_ALLOWED_ORIGINS` no está presente o se encuentra vacía,  
  **Cuando** el backend evalúa la procedencia de una solicitud web,  
  **Entonces** adopta automáticamente `http://localhost:3000` como origen permitido predeterminado.

- **AS-7 (Criterio fuente CA-02.4 — Vinculación en la cadena de filtros de Spring Security)**:  
  **Dado** que se define la cadena de filtros `SecurityFilterChain`,  
  **Cuando** se procesa cualquier solicitud HTTP entrante,  
  **Entonces** el filtro de seguridad aplica la configuración delegada en `.cors(cors -> cors.configurationSource(corsConfigurationSource()))`.

- **AS-8 (Criterio fuente CA-02.5 — Respuesta exitosa a solicitudes preflight OPTIONS)**:  
  **Dado** que un navegador envía una solicitud HTTP con método `OPTIONS` hacia cualquier endpoint público o privado de la API desde un origen permitido,  
  **Cuando** el filtro de seguridad la intercepta,  
  **Entonces** responde con código HTTP 200 OK e incluye la cabecera `Access-Control-Allow-Origin` correspondiente al origen solicitante sin exigir cabeceras de autenticación JWT.

---

### User Story 3 — HU-03: Adaptabilidad de puerto dinámico y health check del backend (Prioridad: P1)

Como plataforma de orquestación (Render),  
quiero que el backend escuche en la variable de entorno `PORT` y publique un endpoint de monitoreo de salud,  
para verificar que la aplicación inició exitosamente y habilitar el ruteo de tráfico web.

**Por qué esta prioridad**: Render no garantiza un puerto fijo de escucha y suspende el tráfico hacia el contenedor si no detecta un puerto dinámico abierto o si el health check configurado no responde HTTP 200 dentro del período de arranque estipulado.

**Prueba independiente**: Se verifica iniciando el backend con una variable `PORT=10000` comprobando que Tomcat enlace en el puerto 10000; iniciando sin `PORT` comprobando el enlace en el puerto 8080; y realizando una solicitud `GET /actuator/health` sin token para confirmar la recepción inmediata de HTTP 200 `{"status":"UP"}`.

#### Escenarios de aceptación

- **AS-9 (Criterio fuente CA-03.1 — Asignación dinámica de puerto con fallback)**:  
  **Dado** que la plataforma de orquestación define la variable de entorno `PORT`,  
  **Cuando** el servidor web embebido (Tomcat) inicia,  
  **Entonces** enlaza la escucha HTTP en el número de puerto indicado por `${PORT:8080}`, y utiliza el puerto 8080 si dicha variable no fue especificada.

- **AS-10 (Criterio fuente CA-03.2 — Incorporación de Spring Boot Actuator)**:  
  **Dado** que el backend es empaquetado a través de `pom.xml`,  
  **Cuando** se resuelve el árbol de dependencias,  
  **Entonces** se encuentra incluida la librería `org.springframework.boot:spring-boot-starter-actuator`.

- **AS-11 (Criterio fuente CA-03.3 — Exposición pública de salud sin autenticación)**:  
  **Dado** que se configura la regla de autorización en `SecurityConfig.java`,  
  **Cuando** un cliente o sonda de monitoreo realiza una petición al endpoint `/actuator/health`,  
  **Entonces** la solicitud es admitida sin requerir token JWT, credenciales ni permisos de rol.

- **AS-12 (Criterio fuente CA-03.4 — Verificación de estado UP del servicio)**:  
  **Dado** que la aplicación backend ha completado exitosamente la carga del contexto de Spring y su conectividad a la base de datos,  
  **Cuando** se envía una solicitud `GET /actuator/health`,  
  **Entonces** el servicio retorna código HTTP 200 con un cuerpo en formato JSON que contiene el atributo `{"status":"UP"}`.

---

### User Story 4 — HU-04: Conexión a PostgreSQL en Neon y migraciones completas (Prioridad: P1)

Como backend de EasyEat en la nube,  
quiero conectarme de forma segura a Neon mediante SSL y ejecutar las migraciones junto a los datos semilla existentes,  
para disponer inmediatamente de una base de datos operativa con comensales, personal, locales y platos de prueba.

**Por qué esta prioridad**: La plataforma Neon rechaza conexiones sin cifrado TLS (`sslmode=require`). Asimismo, el sistema requiere la totalidad de tablas (desde la estructura base hasta la gestión de franjas y Mercado Pago) y datos semilla para permitir pruebas de extremo a extremo inmediatas.

**Prueba independiente**: Se valida conectando el backend a una instancia de base de datos Neon mediante variables de entorno `SPRING_DATASOURCE_*`, observando en los logs de inicio de Flyway la aplicación secuencial sin errores de las migraciones `V1` a `V19` y los scripts `R__seed_dev_*`, y verificando la disponibilidad de los usuarios de prueba iniciales.

#### Escenarios de aceptación

- **AS-13 (Criterio fuente CA-04.1 — Compatibilidad con parámetros SSL en la cadena JDBC)**:  
  **Dado** que se provee una cadena de conexión JDBC que incluye parámetros de cifrado (como `?sslmode=require`),  
  **Cuando** el DataSource de Spring Boot inicializa el pool de conexiones,  
  **Entonces** negocia exitosamente el protocolo SSL/TLS con el servidor de PostgreSQL en Neon.

- **AS-14 (Criterio fuente CA-04.2 — Externalización absoluta de credenciales de base de datos)**:  
  **Dado** que el backend se ejecuta en el entorno cloud,  
  **Cuando** se efectúa la autenticación contra la base de datos,  
  **Entonces** los valores de host, usuario y clave se obtienen exclusivamente a partir de las variables de entorno `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`, sin valores productivos fijos en el repositorio.

- **AS-15 (Criterio fuente CA-04.3 — Preservación de rutas de migración y datos semilla de Flyway)**:  
  **Dado** que el motor Flyway se encuentra habilitado en la configuración de la aplicación,  
  **Cuando** se inspeccionan las rutas de escaneo de scripts,  
  **Entonces** se mantienen activas las ubicaciones `classpath:db/migration` y `classpath:db/dev`.

- **AS-16 (Criterio fuente CA-04.4 — Ejecución completa de migraciones y semillas contra Neon)**:  
  **Dado** que el backend inicia por primera vez contra una base de datos vacía en Neon,  
  **Cuando** Flyway ejecuta el ciclo de migración,  
  **Entonces** se aplican exitosamente todas las migraciones versionadas desde `V1` hasta `V19` seguidas de los scripts repetibles `R__seed_dev_*`, dejando el sistema operativo con usuarios y registros de prueba disponibles.

---

### User Story 5 — HU-05: Contenedorización del frontend con Nginx y enrutamiento SPA (Prioridad: P1)

Como comensal navegando en la aplicación web,  
quiero que el frontend esté empaquetado en un servidor web Nginx dentro de Docker,  
para acceder a la plataforma y recargar cualquier ruta interna (como `/checkout` o `/catalog`) sin recibir errores HTTP 404.

**Por qué esta prioridad**: Las Single Page Applications (SPA) manejan el enrutamiento del lado del cliente. Al recargar una URL profunda en un servidor estático estándar, el servidor busca un archivo físico que no existe y responde 404 Not Found, impidiendo que el usuario vuelva a ingresar al catálogo o al checkout.

**Prueba independiente**: Se valida construyendo la imagen en `easyeat/frontend/` pasando `ARG VITE_API_BASE_URL`, levantando el contenedor con un puerto `PORT` asignado y ejecutando peticiones HTTP `GET` directas sobre rutas virtuales como `/checkout`, `/catalog` y `/reset-password`, constatando que todas entregan HTTP 200 con el contenido de `index.html`.

#### Escenarios de aceptación

- **AS-17 (Criterio fuente CA-05.1 — Construcción multi-etapa del frontend y servidor Nginx)**:  
  **Dado** que existe el archivo `Dockerfile` en el directorio `easyeat/frontend/`,  
  **Cuando** se construye la imagen de contenedor,  
  **Entonces** la primera etapa ejecuta `npm ci` y compila los estáticos mediante `npm run build`, y la segunda etapa copia el directorio resultante `dist/` a una imagen base liviana `nginx:alpine`.

- **AS-18 (Criterio fuente CA-05.2 — Enrutamiento de reserva SPA con try_files en Nginx)**:  
  **Dado** que la configuración de Nginx (`nginx.conf`) incluye la directiva `try_files $uri $uri/ /index.html =404;`,  
  **Cuando** un navegador solicita o recarga directamente una ruta virtual interna (por ejemplo, `/checkout` o `/catalog`),  
  **Entonces** el servidor web sirve el archivo `index.html` con código HTTP 200 en lugar de retornar un error HTTP 404.

- **AS-19 (Criterio fuente CA-05.3 — Inyección de la URL de API en tiempo de build)**:  
  **Dado** que se invoca la construcción de la imagen de Docker especificando el argumento `--build-arg VITE_API_BASE_URL=https://<backend-render>/api/v1`,  
  **When** Vite ejecuta la transpilación de los archivos fuente en `npm run build`,  
  **Entonces** los llamados del cliente HTTP de la SPA (`apiClient`) quedan compilados apuntando a dicha URL pública del backend.

- **AS-20 (Criterio fuente CA-05.4 — Puerto configurable con fallback dinámico en Nginx)**:  
  **Dado** que el contenedor de Nginx es iniciado en la plataforma Render o en un entorno local,  
  **Cuando** el proceso web se pone en marcha,  
  **Entonces** enlaza la escucha en el puerto provisto por la variable de entorno `PORT`, aplicando fallback al puerto `80` si la variable no estuviera configurada.

---

### User Story 6 — HU-06: Sincronización de URLs públicas para Mercado Pago y correos (Prioridad: P1)

Como comensal realizando una orden o recuperando mi cuenta en la aplicación desplegada,  
quiero que las redirecciones de pasarela de pago y los enlaces enviados por correo utilicen las URLs públicas de Render,  
para completar transacciones y procesos de recuperación sin ser redirigido a `localhost`.

**Por qué esta prioridad**: Si las variables de entorno de URLs apuntan por omisión a `localhost`, un comensal que paga desde su teléfono o computadora es redirigido a una dirección inalcanzable tras abonar en Mercado Pago, y los enlaces de restablecimiento de contraseña conducen a un error de conexión, rompiendo los flujos comerciales y de seguridad más críticos del producto.

**Prueba independiente**: Se valida iniciando una preferencia de pago en Mercado Pago y verificando que los parámetros `back_urls` apunten a `https://<frontend-render>/checkout/payment-return`; realizando un llamado de simulación de webhook hacia `https://<backend-render>/api/v1/payments/mercadopago/webhook`; y solicitando recuperación de contraseña para comprobar que el enlace del correo use la URL pública del frontend.

#### Escenarios de aceptación

- **AS-21 (Criterio fuente CA-06.1 — Configuración del dominio público del frontend para Mercado Pago)**:  
  **Dado** que el backend se despliega en Render,  
  **Cuando** se configura el entorno de ejecución,  
  **Entonces** la variable `MERCADOPAGO_FRONTEND_BASE_URL` se establece con el dominio público HTTPS del frontend desplegado en Render (sin barra final).

- **AS-22 (Criterio fuente CA-06.2 — Retorno de Checkout Pro al frontend público)**:  
  **Dado** que un comensal completa una operación de pago en la pasarela externa de Mercado Pago Checkout Pro,  
  **Cuando** el proveedor efectúa la redirección automática o manual de regreso,  
  **Entonces** redirige al navegador del comensal a la URL pública del frontend bajo la ruta `/checkout/payment-return` (por ejemplo, `https://<frontend>/checkout/payment-return?collection_id=...`).

- **AS-23 (Criterio fuente CA-06.3 — Disponibilidad pública y segura del webhook de pagos)**:  
  **Dado** que Mercado Pago notifica asíncronamente el cambio de estado de una transacción,  
  **Cuando** envía la notificación HTTP POST,  
  **Entonces** el endpoint queda accesible públicamente bajo protocolo seguro en `https://<backend-render>/api/v1/payments/mercadopago/webhook` retornando la confirmación de recepción correspondiente.

- **AS-24 (Criterio fuente CA-06.4 — Sincronización de enlaces de recuperación y verificación en correos)**:  
  **Dado** que un usuario solicita el restablecimiento de su clave o la confirmación de su casilla de correo,  
  **Cuando** el backend genera y despacha el correo electrónico,  
  **Entonces** los enlaces contenidos en el mensaje se construyen utilizando los valores configurados en `APP_PASSWORD_RESET_BASE_URL` y `APP_EMAIL_VERIFICATION_BASE_URL`, apuntando al dominio público del frontend y evitando cualquier referencia a `http://localhost`.

---

### Casos borde y escenarios excepcionales

- **Build del backend sin Maven o sin JDK 21**: Si el entorno de compilación carece de JDK 21 o Maven, la primera etapa del build multi-etapa debe fallar de inmediato de forma determinística en tiempo de construcción, impidiendo la generación de imágenes rotas o inconsistentes.
- **Límite de memoria en Render y protección contra OOM (Out Of Memory)**: En entornos con 512 MB de memoria, si la JVM no aplica `-XX:+UseContainerSupport` y `-XX:MaxRAMPercentage=75.0`, el recolector de basura no detecta el límite del contenedor y el kernel de Linux finaliza el proceso (código 137). Los flags garantizan que la JVM dimensione su heap en un máximo de aproximadamente 384 MB, preservando el margen de memoria nativa.
- **Imagen final con código fuente o herramientas de build**: La verificación de la imagen de runtime debe certificar que no existan archivos `.java` ni artefactos de construcción en el contenedor final, minimizando la superficie de ataque y el tamaño de la imagen descargada por Render.
- **Ausencia de configuración en `APP_CORS_ALLOWED_ORIGINS`**: Si la variable de entorno no se define en el entorno local de desarrollo, el backend aplica fallback automático a `http://localhost:3000`, evitando roturas al ejecutar la aplicación fuera de Render.
- **Orígenes CORS mal formados o con espacios accidentales**: Si la variable contiene espacios entre comas (por ejemplo, `https://app.render.com , http://localhost:3000`), el backend debe sanear y recortar (trim) cada origen para evitar rechazos espurios del navegador.
- **Solicitudes preflight OPTIONS hacia endpoints protegidos**: Una petición OPTIONS entrante previa a invocar una ruta autenticada (por ejemplo, `/api/v1/checkout/pay`) no posee token JWT. El filtro de seguridad debe procesar y responder el preflight sin exigir autenticación, evitando que el navegador bloquee la llamada real posterior.
- **Variable `PORT` no asignada por la plataforma**: Si Render o el entorno de ejecución no inyecta la variable `PORT`, el backend enlaza en el puerto 8080 y Nginx enlaza en el puerto 80 sin abortar el inicio.
- **Falla en el arranque del contexto Spring**: Si una variable requerida es inválida o la base de datos es inalcanzable, el contenedor finaliza con código de error distinto de cero, impidiendo que Render lo marque como saludable.
- **Health check devolviendo estado distinto de UP**: Si algún componente crítico monitoreado por Actuator se encuentra en estado DOWN, el endpoint `/actuator/health` debe retornar código HTTP 503, indicando a Render que no derive tráfico de red al contenedor.
- **Conexión a Neon sin parámetro `sslmode=require`**: Si se provee una URL de Neon sin forzar SSL, el servidor de base de datos rechaza la conexión de inmediato, impidiendo el tránsito de credenciales o datos en texto plano.
- **Credenciales fijas en el código**: Queda terminantemente prohibido incluir contraseñas o claves productivas dentro del repositorio; el backend debe requerir que provengan del entorno.
- **Falla o interrupción durante migraciones Flyway**: Si una migración falla contra Neon, Flyway marca el estado como fallido y detiene el arranque de Spring Boot, evitando que la aplicación opere con esquemas inconsistentes o parciales.
- **Re-ejecución de scripts repetibles (`R__seed_dev_*`)**: Los scripts repetibles deben ser idempotentes mediante cláusulas `ON CONFLICT DO NOTHING` o validaciones de existencia, permitiendo reinicios continuos sin violaciones de clave única.
- **Recarga de rutas profundas de la SPA**: Al recargar URLs virtuales como `/catalog`, `/checkout` o `/orders/123`, Nginx intercepta la inexistencia del archivo físico mediante `try_files` y retorna `index.html` con código 200, permitiendo que React Router renderice la vista correcta.
- **Ausencia de `VITE_API_BASE_URL` en tiempo de construcción del frontend**: Si no se provee el argumento en tiempo de build, la SPA adopta `/api/v1` por omisión (válido para proxy local). Para Render, es condición operativa proveer la URL absoluta del backend desplegado.
- **Redirecciones de pasarela y correos con referencias a `localhost` en producción**: En el entorno desplegado, ninguna redirección de Mercado Pago ni enlace de correo puede apuntar a `localhost`. Si esto ocurriera por omisión de configuración, el sistema se considera inoperativo en producción.
- **Webhook de Mercado Pago inalcanzable**: El endpoint `/api/v1/payments/mercadopago/webhook` debe quedar exento de bloqueos de autenticación JWT y accesible públicamente vía HTTPS para permitir que los servidores de Mercado Pago confirmen las transacciones asíncronas.
- **Cold start en plan gratuito de Render**: Debido a la suspensión por inactividad propia del plan gratuito, el arranque de la JVM puede demorar más de 40 segundos; la configuración de salud de Render debe contemplar un tiempo de gracia adecuado antes de considerar fallido el despliegue.

---

## Requisitos funcionales

### Módulo de contenedorización y memoria del backend (HU-01)

- **FR-001**: El backend DEBE empaquetarse mediante un `Dockerfile` multi-etapa ubicado en `easyeat/backend/`, cuya primera etapa compila el código fuente y empaqueta el artefacto ejecutable con Maven y JDK 21 omitiendo la ejecución de tests unitarios (`-DskipTests`).
- **FR-002**: La etapa final de ejecución del backend DEBE utilizar la imagen base ligera `eclipse-temurin:21-jre-alpine`, configurar un usuario de sistema no privilegiado (`easyeat`) para ejecutar el proceso Java, y copiar estrictamente el archivo JAR generado en la etapa de compilación.
- **FR-003**: El comando de inicio (`ENTRYPOINT`) del contenedor backend DEBE configurar la JVM con los argumentos `-XX:+UseContainerSupport` y `-XX:MaxRAMPercentage=75.0` para la gestión automática y segura del heap de memoria.
- **FR-004**: La imagen de contenedor final del backend NO DEBE contener archivos de código fuente (`.java`), herramientas de compilación (`mvn`) ni dependencias transitorias de build, y el directorio `easyeat/backend/` DEBE incluir un archivo `.dockerignore` que excluya `target/`, `.git/`, `.idea/` y artefactos locales del contexto de construcción.

### Módulo de seguridad y CORS dinámico (HU-02)

- **FR-005**: La clase de configuración `SecurityConfig.java` DEBE declarar un bean de tipo `CorsConfigurationSource` que defina los orígenes permitidos, los métodos HTTP `GET`, `POST`, `PUT`, `PATCH`, `DELETE` y `OPTIONS`, cabeceras permitidas en comodín (`*`), credenciales habilitadas (`allowCredentials = true`) y un tiempo de expiración de preflight (`maxAge`) de 3600 segundos (1 hora).
- **FR-006**: La lista de orígenes permitidos de CORS DEBE resolverse dinámicamente a partir de la propiedad de configuración `app.cors.allowed-origins`, vinculada a la variable de entorno `APP_CORS_ALLOWED_ORIGINS` y admitiendo múltiples valores separados por comas, sanitizando y normalizando automáticamente cada entrada mediante eliminación de espacios en blanco (`trim`) y remoción de barras diagonales finales.
- **FR-007**: Si la variable `APP_CORS_ALLOWED_ORIGINS` no está presente o se encuentra en blanco, el backend DEBE establecer por defecto el origen `http://localhost:3000`.
- **FR-008**: La cadena de filtros de seguridad de Spring Security DEBE incorporar explícitamente el soporte de CORS mediante `.cors(cors -> cors.configurationSource(corsConfigurationSource()))`.
- **FR-009**: El filtro de seguridad DEBE responder con código HTTP 200 OK y las correspondientes cabeceras `Access-Control-Allow-Origin` ante cualquier solicitud HTTP con método `OPTIONS` (preflight) proveniente de un origen autorizado, sin requerir token JWT.

### Módulo de puerto dinámico y health check (HU-03)

- **FR-010**: El archivo de configuración `application.yml` del backend DEBE definir la propiedad `server.port: ${PORT:8080}`, permitiendo adoptar el puerto asignado dinámicamente por la plataforma de orquestación con fallback al puerto 8080.
- **FR-011**: El archivo `pom.xml` del backend DEBE incorporar la dependencia oficial de Spring Boot Actuator (`org.springframework.boot:spring-boot-starter-actuator`).
- **FR-012**: La configuración de seguridad en `SecurityConfig.java` DEBE permitir el acceso público sin autenticación (`permitAll()`) al endpoint `/actuator/health`.
- **FR-013**: Una solicitud HTTP `GET /actuator/health` dirigida al backend DEBE responder con código HTTP 200 y cuerpo JSON `{"status":"UP"}` cuando la aplicación y sus dependencias críticas se encuentren operativas.

### Módulo de base de datos en Neon y migraciones (HU-04)

- **FR-014**: La configuración de conexión a la base de datos DEBE utilizar la conexión directa de Neon (sin `-pooler`) para asegurar compatibilidad con bloqueos consultivos de Flyway (`pg_advisory_lock`), admitiendo parámetros de negociación segura SSL en la cadena JDBC provista por la variable `SPRING_DATASOURCE_URL` (incluyendo `sslmode=require`), y tolerando la suspensión por inactividad (scale-to-zero) propia de la capa gratuita mediante validación automática de conexiones en HikariCP (`isValid()`) y un tiempo de espera de conexión de hasta 30 segundos.
- **FR-015**: Las credenciales de acceso a la base de datos DEBEN leerse exclusivamente de las variables de entorno `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`.
- **FR-016**: La configuración de Flyway DEBE mantener activas las rutas de escaneo de migraciones y semillas en `classpath:db/migration` y `classpath:db/dev`.
- **FR-017**: Al iniciar el backend contra la base de datos de Neon, Flyway DEBE ejecutar exitosamente la totalidad de migraciones versionadas desde `V1` hasta `V19` (incluyendo la función wrapper `immutable_unaccent(text)` en `V8__create_establishments.sql` para compatibilidad sin privilegios de propietario sobre extensiones) y los scripts repetibles de datos semilla `R__seed_dev_*`.

### Módulo de contenedorización del frontend y enrutamiento SPA (HU-05)

- **FR-018**: El frontend DEBE empaquetarse mediante un `Dockerfile` multi-etapa ubicado en `easyeat/frontend/`, cuya etapa de compilación ejecuta `npm ci` y `npm run build`, y cuya etapa de ejecución copia el directorio `dist/` a una imagen base `nginx:alpine`, incluyendo además en `easyeat/frontend/` un archivo `.dockerignore` que excluya `node_modules/`, `dist/`, `.git/` y archivos temporales del contexto de construcción.
- **FR-019**: El servidor Nginx DEBE incorporar una configuración (`nginx.conf`) que incluya la directiva de redirección virtual `try_files $uri $uri/ /index.html =404;` para atender la recarga de rutas internas de la SPA, aplicando además la directiva de encabezado `Cache-Control: no-cache` para `index.html` y caché pública e inmutable (`max-age=31536000, immutable`) para los recursos estáticos versionados bajo `/assets/`.
- **FR-020**: El `Dockerfile` del frontend DEBE admitir el argumento de compilación `ARG VITE_API_BASE_URL` para inyectar la URL pública del backend de Render durante el proceso de build.
- **FR-021**: El contenedor de Nginx DEBE escuchar dinámicamente en el puerto indicado por la variable de entorno `PORT` inyectada por Render, aplicando fallback al puerto `80` si dicha variable no está configurada.

### Módulo de sincronización de URLs públicas (HU-06)

- **FR-022**: La variable de entorno `MERCADOPAGO_FRONTEND_BASE_URL` DEBE configurarse en el entorno de despliegue con el dominio público del frontend (sin barra final), y el backend DEBE construir las URLs de retorno (`back_urls`) concatenando `/checkout/payment-return`.
- **FR-023**: El endpoint para webhooks de Mercado Pago DEBE quedar accesible públicamente bajo protocolo HTTPS en la ruta `/api/v1/payments/mercadopago/webhook`, exento de autenticación JWT.
- **FR-024**: Las variables de entorno `APP_PASSWORD_RESET_BASE_URL` y `APP_EMAIL_VERIFICATION_BASE_URL` DEBEN configurarse en el backend apuntando a sus rutas correspondientes en el dominio público del frontend (`https://<frontend>/reset-password` y `https://<frontend>/verificar-email`), y el backend DEBE proveer una implementación de `EmailSender` (`SmtpEmailSender`) activa fuera de los perfiles `dev`, `local` y `test` con inyección opcional de `JavaMailSender` para tolerar despliegues sin servidor SMTP configurado.

---

## Artefactos y entidades de despliegue

- **Artefacto Docker Backend (`easyeat/backend/Dockerfile`)**: Especificación de empaquetado multi-etapa con Maven/JDK 21 en build y Alpine JRE 21 en runtime con optimización de memoria.
- **Artefacto Dockerignore Backend (`easyeat/backend/.dockerignore`)**: Reglas de exclusión de build context (target/, .git/, .idea/, *.md) para compilación Docker limpia y liviana.
- **Artefacto Docker Frontend (`easyeat/frontend/Dockerfile`)**: Especificación de empaquetado multi-etapa con Node.js en build y Nginx Alpine en runtime con argumento de compilación para la URL de API.
- **Artefacto Dockerignore Frontend (`easyeat/frontend/.dockerignore`)**: Reglas de exclusión de build context (node_modules/, dist/, .git/, coverage/) para compilación frontend óptima.
- **Configuración de Servidor Web Frontend (`easyeat/frontend/nginx.conf`)**: Configuración de servidor HTTP Nginx con soporte para enrutamiento SPA (`try_files`) y resolución de puerto.
- **Mapeo de Variables de Entorno de Producción**:
  - `PORT`: Puerto asignado por Render (utilizado por backend y frontend).
  - `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`: Coordenadas seguras de conexión a Neon con SSL.
  - `APP_CORS_ALLOWED_ORIGINS`: Lista blanca de dominios públicos autorizados para invocar la API desde el navegador.
  - `VITE_API_BASE_URL`: URL base pública de la API REST del backend consumida por la SPA.
  - `MERCADOPAGO_FRONTEND_BASE_URL`: Origen público del frontend para retornos de la pasarela de pagos.
  - `APP_PASSWORD_RESET_BASE_URL`, `APP_EMAIL_VERIFICATION_BASE_URL`: Direcciones públicas completas para acciones de usuario enviadas por correo.

---

## Validaciones e invariantes

- **Invariante de limpieza y ligereza del backend**: La imagen de contenedor final del backend no debe contener código fuente, tests ni utilitarios de compilación. Su proceso Java debe ejecutarse obligatoriamente bajo un usuario sin privilegios (`easyeat`) y su tamaño debe mantenerse en el orden mínimo permitido por la distribución JRE Alpine.
- **Invariante de consumo de memoria**: La JVM debe gobernar su consumo de heap respetando la cuota del contenedor mediante `-XX:+UseContainerSupport` y `-XX:MaxRAMPercentage=75.0`, impidiendo la terminación del proceso por OOM.
- **Invariante de retrocompatibilidad local**: La ausencia de variables de entorno específicas de despliegue (`PORT`, `APP_CORS_ALLOWED_ORIGINS`) no debe romper la ejecución local en `http://localhost:3000` y puerto `8080`.
- **Invariante de seguridad de datos**: Toda conexión saliente a la base de datos de Neon debe utilizar obligatoriamente canales cifrados mediante SSL/TLS (`sslmode=require`).
- **Invariante de integridad de esquema**: Al iniciar contra una base de datos vacía o actualizada en Neon, deben ejecutarse sin errores la totalidad de migraciones desde `V1` hasta `V19` y los scripts repetibles `R__seed_dev_*`.
- **Invariante de navegación SPA**: Ninguna recarga manual sobre rutas virtuales registradas en la aplicación cliente debe resultar en un error HTTP 404 del servidor web.
- **Invariante de URLs públicas en producción**: En el entorno desplegado, ningún enlace de retorno de pasarela de pago ni enlace de activación o reseteo de credenciales debe contener referencias a `localhost`.

---

## Comportamiento ante errores

- **Falla de conexión a la base de datos Neon**: Si la cadena JDBC es errónea, las credenciales son inválidas o el certificado SSL falla, Spring Boot interrumpe inmediatamente el arranque registrando el error en el log estándar y terminando con código de salida no nulo, lo que previene que Render considere el servicio como saludable.
- **Rechazo de solicitud por política CORS**: Si una petición proviene de un origen web no listado en `APP_CORS_ALLOWED_ORIGINS`, el backend omite la cabecera `Access-Control-Allow-Origin`, provocando que el navegador bloquee la lectura de la respuesta y proteja la sesión del usuario.
- **Falla en migración de Flyway**: Si un script SQL presenta errores sintácticos o de integridad referencial, Flyway aborta la secuencia de arranque y detiene la aplicación antes de exponer cualquier endpoint al tráfico público.
- **Invocación al health check con aplicación degradada**: Si la aplicación ha sufrido una falla crítica de contexto, el endpoint `/actuator/health` retorna código de estado HTTP 503 (Service Unavailable) con estado `DOWN`, provocando que Render retire la instancia del enrutamiento.

---

## Fuera de alcance

- Creación o aprovisionamiento automatizado de infraestructura mediante herramientas de IaC (Terraform, Pulumi o CloudFormation).
- Pipelines automatizados de integración y despliegue continuo (GitHub Actions CI/CD) para auto-deploy en cada commit.
- Configuración de dominios personalizados con gestión de certificados SSL fuera del esquema nativo gestionado por Render.
- Gestión de balanceo de carga o clustering distribuido entre múltiples réplicas de contenedores.
- Implementación de nuevas migraciones de base de datos o modificaciones al modelo de datos más allá de `V1` a `V19` y las semillas existentes.
- Modificación de las interfaces visuales de usuario (UI/UX) o de las reglas de negocio implementadas en las Specs 001 a 016.
- Integración con pasarelas de pago adicionales o proveedores de correo electrónico distintos a los soportados por el backend.

---

## Supuestos

- Se asume que el repositorio cuenta con una estructura de proyecto Spring Boot 3.x con Java 21 en `easyeat/backend/` y una aplicación React 18 con Vite en `easyeat/frontend/`.
- Se asume que tanto los servicios web de Render como la base de datos PostgreSQL en Neon operan bajo sus respectivos planes gratuitos, por lo cual es comportamiento legítimo y esperado que entren en modo de suspensión o reposo (spin down tras 15 min en Render y scale-to-zero tras 5 min en Neon) ante inactividad, tolerando el backend y el frontend los tiempos de cold start y reconexión correspondientes sin fallos estructurales.
- Se asume que la base de datos provista en Neon es una instancia PostgreSQL versión 15 o superior accesible públicamente mediante cadena de conexión JDBC con soporte de TLS nativo.
- Se asume que la plataforma Render provee un entorno de ejecución de contenedores Docker que inyecta automáticamente la variable de entorno `PORT` y resuelve la terminación SSL pública en los dominios `*.onrender.com`.
- Se asume que los datos semilla de prueba provistos en `R__seed_dev_*` son suficientes para inicializar los usuarios, establecimientos y platos de prueba en el entorno desplegado.

---

## Criterios de éxito

- **SC-001**: El 100% de las construcciones de imagen Docker del backend producen un contenedor ejecutable basado en `eclipse-temurin:21-jre-alpine` sin código fuente ni Maven.
- **SC-002**: El 100% de los arranques del backend en Render respetan el límite de memoria del contenedor mediante los flags JVM sin experimentar terminaciones por Out Of Memory (OOM).
- **SC-003**: El 100% de las solicitudes preflight `OPTIONS` y llamadas reales desde el dominio público del frontend son autorizadas por CORS respondiendo código HTTP 200 sin bloqueos de navegador.
- **SC-004**: El endpoint `GET /actuator/health` responde con código HTTP 200 y cuerpo `{"status":"UP"}` en menos de 2 segundos una vez completado el inicio del backend.
- **SC-005**: El 100% de las migraciones Flyway (desde `V1` hasta `V19`) y los scripts repetibles `R__seed_dev_*` se aplican de manera limpia y sin errores contra la base de datos de Neon.
- **SC-006**: El 100% de las recargas directas en el navegador sobre rutas internas de la SPA (`/checkout`, `/catalog`, `/reset-password`) devuelven el archivo principal `index.html` con código HTTP 200, registrando 0 errores HTTP 404.
- **SC-007**: El 100% de las redirecciones de retorno de Mercado Pago Checkout Pro se efectúan hacia la URL pública del frontend bajo `/checkout/payment-return`, con 0 redirecciones a `localhost`.
- **SC-008**: El 100% de los correos de recuperación de contraseña y verificación emitidos en el entorno de despliegue contienen enlaces estructurados sobre los dominios públicos correspondientes, con 0 enlaces a `localhost`.
- **SC-009**: El endpoint de webhook de Mercado Pago `/api/v1/payments/mercadopago/webhook` es alcanzable públicamente vía HTTPS y procesa notificaciones externas sin requerir tokens de usuario.
- **SC-010**: Ambos contenedores (backend y frontend) enlazan automáticamente la escucha en el puerto provisto por la variable `PORT` de Render y realizan fallback a sus puertos estándar (`8080` y `80`) en ausencia de dicha variable.

---

## Matriz de trazabilidad

| Historia de usuario | Criterio de aceptación fuente | Escenario de aceptación | Requisito funcional | Criterio de éxito |
|---|---|---|---|---|
| HU-01 | CA-01.1 | AS-1 | FR-001, FR-002 | SC-001 |
| HU-01 | CA-01.2 | AS-2 | FR-003 | SC-002 |
| HU-01 | CA-01.3 | AS-3 | FR-004 | SC-001 |
| HU-02 | CA-02.1 | AS-4 | FR-005 | SC-003 |
| HU-02 | CA-02.2 | AS-5 | FR-006 | SC-003 |
| HU-02 | CA-02.3 | AS-6 | FR-007 | SC-003, SC-010 |
| HU-02 | CA-02.4 | AS-7 | FR-008 | SC-003 |
| HU-02 | CA-02.5 | AS-8 | FR-009 | SC-003 |
| HU-03 | CA-03.1 | AS-9 | FR-010 | SC-010 |
| HU-03 | CA-03.2 | AS-10 | FR-011 | SC-004 |
| HU-03 | CA-03.3 | AS-11 | FR-012 | SC-004 |
| HU-03 | CA-03.4 | AS-12 | FR-013 | SC-004 |
| HU-04 | CA-04.1 | AS-13 | FR-014 | SC-005 |
| HU-04 | CA-04.2 | AS-14 | FR-015 | SC-005 |
| HU-04 | CA-04.3 | AS-15 | FR-016 | SC-005 |
| HU-04 | CA-04.4 | AS-16 | FR-017 | SC-005 |
| HU-05 | CA-05.1 | AS-17 | FR-018 | SC-006 |
| HU-05 | CA-05.2 | AS-18 | FR-019 | SC-006 |
| HU-05 | CA-05.3 | AS-19 | FR-020 | SC-006 |
| HU-05 | CA-05.4 | AS-20 | FR-021 | SC-006, SC-010 |
| HU-06 | CA-06.1 | AS-21 | FR-022 | SC-007 |
| HU-06 | CA-06.2 | AS-22 | FR-022 | SC-007 |
| HU-06 | CA-06.3 | AS-23 | FR-023 | SC-009 |
| HU-06 | CA-06.4 | AS-24 | FR-024 | SC-008 |

