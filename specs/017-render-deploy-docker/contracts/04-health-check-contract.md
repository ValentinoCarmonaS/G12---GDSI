# Contrato Operativo 04: Health Check del Backend

**Artefacto**: `com.easyeat.config.SecurityConfig`, `application.yml` y `pom.xml` (`spring-boot-starter-actuator`)  
**Propósito**: Publicar un endpoint liviano de monitoreo de disponibilidad para la plataforma de orquestación Render, informando el estado del backend y la conectividad a la base de datos sin requerir autenticación (HU-03, FR-010 a FR-013, AS-9 a AS-12).  

---

## 1. Especificación del Endpoint de Salud

### 1.1. Ruta y Protocolo
- **Endpoint**: `GET /actuator/health`
- **Autenticación**: Exenta (`permitAll()` en `SecurityConfig`). Cero tokens JWT requeridos.
- **Formato de respuesta**: JSON (`application/vnd.spring-boot.actuator.v3+json` o `application/json`).
- **Visibilidad**: Detalles restringidos a usuarios autorizados (`show-details: when-authorized`) para no exponer información interna de base de datos a sondas públicas.

---

## 2. Invariantes del Contrato

1. **Inv-Health-01 (Respuesta pública sin credenciales)**: La sonda de Render debe poder invocar `/actuator/health` de forma anónima sin recibir errores HTTP 401 Unauthorized ni HTTP 403 Forbidden (AS-11, FR-012).
2. **Inv-Health-02 (Semántica HTTP autoritativa)**:
   - Si la aplicación y la conexión a Neon están operativas: código HTTP 200 OK con payload `{"status":"UP"}` (AS-12, FR-013).
   - Si la base de datos es inalcanzable o el contexto colapsó: código HTTP 503 Service Unavailable con payload `{"status":"DOWN"}`.
3. **Inv-Health-03 (Latencia acotada)**: El endpoint debe responder en menos de 2 segundos una vez completado el arranque inicial (SC-004).

---

## 3. Ejemplos de Respuesta HTTP

### 3.1. Estado Saludable (UP — Servicio Operativo)
```http
GET /actuator/health HTTP/1.1
Host: easyeat-api.onrender.com
User-Agent: Render/1.0 (HealthCheck Probe)

HTTP/1.1 200 OK
Content-Type: application/vnd.spring-boot.actuator.v3+json

{
  "status": "UP"
}
```

### 3.2. Estado Degradado / Caído (DOWN — Falla de Conectividad a Base de Datos)
```http
GET /actuator/health HTTP/1.1
Host: easyeat-api.onrender.com

HTTP/1.1 503 Service Unavailable
Content-Type: application/vnd.spring-boot.actuator.v3+json

{
  "status": "DOWN"
}
```

---

## 4. Fallos Esperados y Mitigación

| Escenario de Falla | Comportamiento | Mitigación |
|---|---|---|
| Intercepción por filtro de seguridad JWT | Responde 401 Unauthorized y Render descarta el contenedor | Regla `.requestMatchers("/actuator/health").permitAll()` en `SecurityConfig`. |
| Base de datos Neon en scale-to-zero durante la sonda | Actuator comprueba la conexión y espera hasta 30 s | HikariCP `connection-timeout: 30000` y tiempos de gracia en Render. |

---

## 5. Verificación Observable

1. **Consulta anónima de salud**:
   ```sh
   curl -i http://localhost:8080/actuator/health
   # Salida esperada:
   # HTTP/1.1 200 OK
   # {"status":"UP"}
   ```
2. **Verificación de ausencia de JWT requerido**:
   ```sh
   curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health
   # Salida esperada: 200
   ```

