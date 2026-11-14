# Contrato Operativo 03: Negociación de CORS Dinámico

**Artefacto**: `com.easyeat.config.SecurityConfig` y `application.yml` (`app.cors.allowed-origins`)  
**Propósito**: Autorizar peticiones cross-origin entre el frontend público de Render y la API REST del backend, resolviendo dominios dinámicos, sanitizando sintaxis y optimizando preflights (HU-02, FR-005 a FR-009, AS-4 a AS-8).  

---

## 1. Especificación del Contrato HTTP

### 1.1. Propiedades de Configuración
- Propiedad en `application.yml`: `app.cors.allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000}`
- Lista blanca: admite múltiples orígenes separados por comas.
- Regla de sanitización sintáctica (Q6/A6):
  1. Cada origen en la lista se recorta de espacios iniciales y finales (`trim()`).
  2. Cada origen se purga de cualquier barra diagonal final (`/+$`).
  3. Si tras sanitizar la lista resultante queda vacía, se adopta `http://localhost:3000` (AS-6).

### 1.2. Parámetros de la Negociación CORS
| Atributo | Valor Fijo / Dinámico | Rationale / Requisito |
|---|---|---|
| `allowedOrigins` | Lista sanitizada desde `app.cors.allowed-origins` | FR-006, AS-5 |
| `allowedMethods` | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` | FR-005, AS-4 |
| `allowedHeaders` | Comodín `*` | FR-005, AS-4 |
| `allowCredentials` | `true` | FR-005, AS-4 (necesario para JWT en cabeceras de autorización) |
| `maxAge` | `3600L` (1 hora) | Q8/A8, FR-005 (reduce solicitudes preflight redundantes) |

---

## 2. Invariantes del Contrato

1. **Inv-CORS-01 (Preflight sin autenticación)**: Toda solicitud HTTP con método `OPTIONS` proveniente de un origen autorizado debe responder HTTP 200 OK inmediatamente, sin requerir token JWT ni cabeceras de autenticación (AS-8, FR-009).
2. **Inv-CORS-02 (Aislamiento de orígenes no autorizados)**: Una petición proveniente de un `Origin` no listado en `app.cors.allowed-origins` no debe recibir el encabezado `Access-Control-Allow-Origin`, provocando el bloqueo automático del navegador.
3. **Inv-CORS-03 (Fallback retrocompatible)**: Si `APP_CORS_ALLOWED_ORIGINS` no se define en el entorno, `http://localhost:3000` debe ser admitido sin errores de configuración (AS-6, FR-007).

---

## 3. Ejemplos de Intercambio HTTP

### 3.1. Preflight Exitoso desde Origen Permitido
```http
OPTIONS /api/v1/checkout/pay HTTP/1.1
Host: easyeat-api.onrender.com
Origin: https://easyeat-frontend.onrender.com
Access-Control-Request-Method: POST
Access-Control-Request-Headers: authorization,content-type

HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://easyeat-frontend.onrender.com
Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
Access-Control-Allow-Headers: *
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
```

### 3.2. Petición Rechazada desde Origen Malicioso / No Autorizado
```http
OPTIONS /api/v1/checkout/pay HTTP/1.1
Host: easyeat-api.onrender.com
Origin: https://evil-site.com
Access-Control-Request-Method: POST

HTTP/1.1 403 Forbidden  (o 200 sin encabezado Access-Control-Allow-Origin)
```

---

## 4. Fallos Esperados y Mitigación

| Escenario de Falla | Comportamiento | Mitigación |
|---|---|---|
| Origen con barra final accidental (`https://app.render.com/`) | Rechazo por falta de coincidencia exacta de strings RFC 6454 | Sanitización automática eliminando `/+$`. |
| Origen con espacios accidentales (`https://a.com , https://b.com`) | Rechazo por espacio espurio | Sanitización automática con `trim()`. |

---

## 5. Verificación Observable

1. **Verificación de preflight permitido**:
   ```sh
   curl -I -X OPTIONS http://localhost:8080/api/v1/establishments \
     -H "Origin: https://easyeat-frontend.onrender.com" \
     -H "Access-Control-Request-Method: GET"
   # Salida esperada: HTTP 200 OK con Access-Control-Allow-Origin: https://easyeat-frontend.onrender.com
   ```
2. **Verificación de expiración de preflight (Max-Age)**:
   ```sh
   curl -I -X OPTIONS http://localhost:8080/api/v1/auth/login \
     -H "Origin: http://localhost:3000" \
     -H "Access-Control-Request-Method: POST" | grep -i "access-control-max-age"
   # Salida esperada: Access-Control-Max-Age: 3600
   ```

