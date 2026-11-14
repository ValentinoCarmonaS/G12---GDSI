# Quickstart Validation Guide: 017 Render Deploy Docker

**Branch**: `017-render-deploy-docker` | **Fecha**: 10/11/2026 | **Spec**: [spec.md](./spec.md)  
**Estado**: Completado  

---

## 1. Propósito y Alcance

Esta guía provee escenarios reproducibles de verificación operativa y pruebas de humo para certificar el empaquetado Docker, la configuración dinámica de red, la seguridad de orígenes (CORS), el monitoreo de salud, las migraciones en Neon y la sincronización de URLs públicas de EasyEat tanto en un entorno Docker local como en la plataforma productiva Render.

Cubre de manera integral la verificación de los 24 escenarios de aceptación (**AS-1 a AS-24**) y los 10 criterios de éxito (**SC-001 a SC-010**).

---

## 2. Prerrequisitos

- **Docker Engine**: versión 24+ y Docker Compose (o Docker Desktop en Windows).
- **Herramientas de línea de comandos**: `curl`, `jq`, `grep` (o PowerShell en Windows).
- **Instancia de Base de Datos Neon (para pruebas en la nube)**: endpoint PostgreSQL 15+ con credenciales y soporte de TLS (`sslmode=require`).

---

## 3. Matriz de Validación de Escenarios (AS-1 a AS-24)

| Fase / Módulo | Escenarios Cubiertos | Procedimiento de Validación | Salida Esperada |
|---|---|---|---|
| **Contenedorización Backend** | AS-1, AS-2, AS-3 | Construcción de imagen y chequeo de runtime (`whoami`, `ps`, `find`). | Usuario `easyeat`, flags JVM presentes, cero archivos `.java`, imagen Alpine JRE. |
| **CORS Dinámico** | AS-4, AS-5, AS-6, AS-7, AS-8 | Peticiones HTTP `OPTIONS` con header `Origin` permitido y no permitido. | 200 OK con `Access-Control-Allow-Origin` y `Max-Age: 3600`; sin preflight para orígenes ajenos. |
| **Puerto Dinámico & Health** | AS-9, AS-10, AS-11, AS-12 | Arranque con `PORT=10000` y sin `PORT`; petición a `/actuator/health`. | Enlace en puerto solicitado (o 8080 default); HTTP 200 `{"status":"UP"}` sin token JWT. |
| **Neon & Flyway** | AS-13, AS-14, AS-15, AS-16 | Arranque contra base vacía en Neon con SSL. | Migraciones `V1` a `V19` aplicadas correlativamente + scripts `R__seed_dev_*`. |
| **Contenedorización Frontend** | AS-17, AS-18, AS-19, AS-20 | Construcción con `ARG VITE_API_BASE_URL`; recarga de `/checkout` y `/catalog`. | 200 OK sirviendo `index.html` (cero 404); bundle apunta a API configurada; Nginx en `${PORT:-80}`. |
| **URLs Públicas & Webhook** | AS-21, AS-22, AS-23, AS-24 | Simulación de preferencia MP, webhook anónimo (con y sin `x-signature` inválida) y solicitud de reseteo de clave. | `back_urls` apuntan a frontend público; webhook resuelve slugs y responde `200`/`401` HMAC sin JWT (nunca `404`); link de mail sin `localhost`. |

---

## 4. Guía de Ejecución Local con Docker

### Paso 1: Construcción y Validación de Imagen del Backend (HU-01)
```bash
# 1.1. Construir la imagen del backend
docker build -t easyeat-backend:local easyeat/backend/

# 1.2. Verificar ligereza y ausencia de código fuente (AS-3, SC-001)
docker run --rm easyeat-backend:local sh -c "find / -name '*.java' 2>/dev/null | wc -l"
# Salida esperada: 0

# 1.3. Verificar usuario no root (AS-1, Q9)
docker run --rm easyeat-backend:local whoami
# Salida esperada: easyeat

# 1.4. Verificar flags de memoria JVM (AS-2, SC-002)
docker run --rm easyeat-backend:local sh -c "java -XX:+PrintFlagsFinal -version | grep MaxRAMPercentage"
# Salida esperada: double MaxRAMPercentage = 75.000000
```

---

### Paso 2: Verificación de Puerto Dinámico y Health Check (HU-03)
```bash
# 2.1. Iniciar contenedor con puerto dinámico simulado (PORT=10000) y base local
docker run -d --name test-be-dynamic \
  -e PORT=10000 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/easyeat \
  -e SPRING_DATASOURCE_USERNAME=easyeat \
  -e SPRING_DATASOURCE_PASSWORD=easyeat_dev_password \
  -p 10000:10000 \
  easyeat-backend:local

# Aguardar inicialización del contexto (~15-20 s)
sleep 20

# 2.2. Validar respuesta de Health Check anónimo sin JWT (AS-11, AS-12, SC-004)
curl -i http://localhost:10000/actuator/health
# Salida esperada:
# HTTP/1.1 200 OK
# Content-Type: application/vnd.spring-boot.actuator.v3+json
# {"status":"UP"}

docker stop test-be-dynamic && docker rm test-be-dynamic
```

---

### Paso 3: Verificación de Negociación CORS (HU-02)
```bash
# 3.1. Iniciar backend configurando orígenes permitidos con espacios y barras para probar sanitización (Q6)
docker run -d --name test-be-cors \
  -e APP_CORS_ALLOWED_ORIGINS="https://mi-app.onrender.com/ , http://localhost:3000" \
  -p 8080:8080 \
  easyeat-backend:local

sleep 15

# 3.2. Probar preflight OPTIONS desde origen permitido sanitizado (AS-5, AS-8, SC-003)
curl -I -X OPTIONS http://localhost:8080/api/v1/auth/login \
  -H "Origin: https://mi-app.onrender.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: authorization,content-type"

# Salida esperada:
# HTTP/1.1 200 OK
# Access-Control-Allow-Origin: https://mi-app.onrender.com
# Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
# Access-Control-Max-Age: 3600
# Access-Control-Allow-Credentials: true

# 3.3. Probar preflight desde origen NO permitido (debe denegarse)
curl -I -X OPTIONS http://localhost:8080/api/v1/auth/login \
  -H "Origin: https://sitio-malicioso.com" \
  -H "Access-Control-Request-Method: POST"
# Salida esperada: Sin cabecera Access-Control-Allow-Origin (o 403 Forbidden)

docker stop test-be-cors && docker rm test-be-cors
```

---

### Paso 4: Construcción y Validación de Imagen del Frontend (HU-05)
```bash
# 4.1. Construir la imagen del frontend pasando la URL pública de API como build-arg (AS-19)
docker build \
  --build-arg VITE_API_BASE_URL="https://easyeat-api.onrender.com/api/v1" \
  -t easyeat-frontend:local \
  easyeat/frontend/

# 4.2. Iniciar contenedor asignando puerto dinámico (PORT=9000) (AS-20)
docker run -d --name test-fe \
  -e PORT=9000 \
  -p 9000:9000 \
  easyeat-frontend:local

sleep 3

# 4.3. Probar recarga directa de ruta virtual SPA (/checkout y /catalog) (AS-18, SC-006)
curl -i http://localhost:9000/checkout
# Salida esperada:
# HTTP/1.1 200 OK
# Cache-Control: no-cache
# (Cuerpo con index.html)

curl -i http://localhost:9000/catalog
# Salida esperada: HTTP/1.1 200 OK

# 4.4. Probar política de caché para activos inmutables de Vite (Q5)
ASSET_NAME=$(docker exec test-fe ls /usr/share/nginx/html/assets | grep '\.js$' | head -n 1)
curl -I http://localhost:9000/assets/$ASSET_NAME
# Salida esperada:
# HTTP/1.1 200 OK
# Cache-Control: public, max-age=31536000, immutable

docker stop test-fe && docker rm test-fe
```

---

### Paso 5: Verificación de Webhooks y URLs Públicas (HU-06, Q15)
```bash
# 5.1. Validar accesibilidad pública del webhook de Mercado Pago sin autenticación ni x-signature (AS-23, FR-023, SC-009)
curl -i -X POST http://localhost:8080/api/v1/payments/mercadopago/webhook \
  -H "Content-Type: application/json" \
  -d '{"action":"payment.created","data":{"id":"test_id"}}'
# Salida esperada: 200 OK (no debe responder 401 JWT, 403 ni 404).

# 5.2. Validar resolución de slugs y rechazo HMAC ante firma inválida en /mercadopago/webhook y /mercado-pago/webhook (Q15, FR-023)
curl -i -X POST http://localhost:8080/api/v1/payments/mercadopago/webhook \
  -H "Content-Type: application/json" \
  -H "x-signature: ts=12345,v1=invalid" \
  -d '{"action":"payment.created","data":{"id":"test_id"}}'
# Salida esperada: 401 Unauthorized con cuerpo vacío (rechazo de firma en PaymentWebhookController, nunca 404 ni application/problem+json).

# 5.3. Validar que 404 Not Found quede reservado a proveedor desconocido o sin adaptador registrado (ej. stripe)
curl -i -X POST http://localhost:8080/api/v1/payments/stripe/webhook \
  -H "Content-Type: application/json" \
  -d '{"action":"payment.created","data":{"id":"test_id"}}'
# Salida esperada: 404 Not Found.
```

---

## 5. Procedimiento de Verificación en la Nube (Render + Neon)

### Paso 1: Configuración en Neon (PostgreSQL)
1. Obtener la cadena de conexión JDBC directa (sin `-pooler`) desde la consola de Neon.
2. Certificar que incluya el flag SSL:
   `jdbc:postgresql://ep-xyz.neon.tech/easyeat?sslmode=require`

### Paso 2: Despliegue del Backend en Render (Web Service Docker)
1. Crear un Web Service en Render seleccionando el repositorio y directorio raíz `easyeat/backend`.
2. Establecer **Environment Variables**:
   - `SPRING_DATASOURCE_URL`: `jdbc:postgresql://ep-xyz.neon.tech/easyeat?sslmode=require`
   - `SPRING_DATASOURCE_USERNAME`: `<usuario_neon>`
   - `SPRING_DATASOURCE_PASSWORD`: `<password_neon>`
   - `APP_CORS_ALLOWED_ORIGINS`: `https://easyeat-frontend.onrender.com`
   - `MERCADOPAGO_FRONTEND_BASE_URL`: `https://easyeat-frontend.onrender.com`
   - `APP_PASSWORD_RESET_BASE_URL`: `https://easyeat-frontend.onrender.com/reset-password`
   - `APP_EMAIL_VERIFICATION_BASE_URL`: `https://easyeat-frontend.onrender.com/verificar-email`
3. Configurar **Health Check Path**: `/actuator/health`.
4. Monitorear los logs de despliegue:
   - Validar que Flyway ejecute exitosamente de `V1` a `V19` y `R__seed_dev_*`.
   - Validar que Tomcat enlace en el puerto `$PORT` inyectado por Render.
   - Validar que la sonda de salud reciba HTTP 200 y marque el servicio como **Live**.

### Paso 3: Despliegue del Frontend en Render (Web Service Docker)
1. Crear un Web Service en Render seleccionando el directorio raíz `easyeat/frontend`.
2. Establecer **Build Argument**:
   - `VITE_API_BASE_URL`: `https://easyeat-backend.onrender.com/api/v1`
3. Configurar **Health Check Path**: `/`.
4. Desplegar y validar:
   - Abrir en el navegador `https://easyeat-frontend.onrender.com/catalog`.
   - Recargar la página con `F5` o `Ctrl+R`: debe mantenerse la vista sin error 404.
   - Iniciar sesión con un usuario de prueba de las semillas (`comensal1@easyeat.com` / clave de semilla) y comprobar que la API responda sin bloqueos de CORS.

