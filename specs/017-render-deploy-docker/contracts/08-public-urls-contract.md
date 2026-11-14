# Contrato Operativo 08: Sincronización de URLs Públicas

**Artefacto**: `com.easyeat.order.service.MercadoPagoGatewayAdapter`, `PaymentWebhookController`, `PaymentMethod`, `PaymentGatewayResolver`, `application.yml` y `SecurityConfig`  
**Propósito**: Asegurar que las redirecciones de pasarela de pago (Mercado Pago Checkout Pro) y los hipervínculos transaccionales de correo electrónico utilicen los dominios públicos HTTPS de Render, eliminando referencias a `localhost` en producción y manteniendo accesibles los webhooks externos con resolución tolerante de slugs (HU-06, FR-022 a FR-024, AS-21 a AS-24, Q3, Q15).  

---

## 1. Especificación de Endpoints y Composición de URLs

### 1.1. Mercado Pago Checkout Pro (`back_urls`)
- **Variable de Origen**: `MERCADOPAGO_FRONTEND_BASE_URL`
- **Formato esperado en Render**: Origen raíz público sin barra final (ej. `https://easyeat-frontend.onrender.com`).
- **Comportamiento del Backend (`MercadoPagoGatewayAdapter.java`)**:
  - Concatena internamente la ruta fija `/checkout/payment-return` a la variable base para los tres estados de pago:
    - `success`: `https://<frontend>/checkout/payment-return`
    - `pending`: `https://<frontend>/checkout/payment-return`
    - `failure`: `https://<frontend>/checkout/payment-return`
  - Inyecta `auto_return: "approved"` para retorno automático inmediato tras el cobro.

### 1.2. Webhook de Pagos Entrante
- **Ruta del Endpoint**: `POST /api/v1/payments/{provider}/webhook` (incluye la URL estándar `POST /api/v1/payments/mercadopago/webhook` y la forma canónica con guion `POST /api/v1/payments/mercado-pago/webhook`).
- **Protocolo y Red**: HTTPS sobre dominio público del backend (`https://easyeat-backend.onrender.com/api/v1/payments/mercadopago/webhook`).
- **Resolución de proveedor y adaptador (FR-023, Q15)**:
  - `PaymentMethod.fromWebhookSlug(provider)` normaliza el slug eliminando `-` y `_` y convirtiendo a mayúsculas con `Locale.ROOT` (`mercadopago` y `mercado-pago` resuelven a `PaymentMethod.MERCADO_PAGO`).
  - `PaymentGatewayResolver.findAdapter(method)` consulta de forma segura (`Optional<PaymentGatewayPort>`) si existe un adaptador registrado en el contexto para ese método de pago.
- **Seguridad**:
  - Exento de autenticación JWT (`permitAll()` en `SecurityConfig`).
  - Validación criptográfica mediante cabecera `x-signature` (cuando está presente y no en blanco) y validación HMAC-SHA256 con `MERCADOPAGO_WEBHOOK_SECRET` en `WebhookSignatureValidator`.
- **Respuestas HTTP esperadas**:
  - **HTTP 200 OK**: notificación aceptada (firma HMAC válida, o notificación sin cabecera `x-signature` / sin `data.id` según la semántica actual del controlador).
  - **HTTP 401 Unauthorized (cuerpo vacío)**: proveedor y adaptador resueltos (`mercadopago` o `mercado-pago`), pero la cabecera `x-signature` presente no supera la validación HMAC-SHA256. No debe confundirse con un rechazo del filtro JWT (el filtro JWT devuelve HTTP 401 con cuerpo `application/problem+json`).
  - **HTTP 404 Not Found**: reservado exclusivamente para un slug `{provider}` desconocido/inválido o para un `PaymentMethod` conocido sin adaptador registrado en el entorno (por ejemplo, `stripe`). Nunca debe ocurrir para rutas válidas de Mercado Pago.

### 1.3. Enlaces en Correos Electrónicos Transaccionales
- **Recuperación de Contraseña**:
  - Variable: `APP_PASSWORD_RESET_BASE_URL`
  - Formato esperado en Render: URL completa con ruta (`https://easyeat-frontend.onrender.com/reset-password`).
  - Construcción del enlace en el servicio: `${APP_PASSWORD_RESET_BASE_URL}?token=${token}`.
- **Verificación de Casilla de Correo**:
  - Variable: `APP_EMAIL_VERIFICATION_BASE_URL`
  - Formato esperado en Render: URL completa con ruta (`https://easyeat-frontend.onrender.com/verificar-email`).
  - Construcción del enlace en el servicio: `${APP_EMAIL_VERIFICATION_BASE_URL}?token=${token}`.

---

## 2. Invariantes del Contrato

1. **Inv-URL-01 (Cero Referencias a Localhost en Producción)**: En el entorno de Render, ninguna preferencia de Mercado Pago ni correo electrónico generado debe contener cadenas `http://localhost` o `127.0.0.1` en sus enlaces (SC-007, SC-008).
2. **Inv-URL-02 (Disponibilidad Pública de Webhooks)**: Los endpoints `/api/v1/payments/mercadopago/webhook` y `/api/v1/payments/mercado-pago/webhook` deben ser accesibles desde la red pública sin requerir credenciales ni tokens de usuario, resolver al adaptador de Mercado Pago (sin responder `404 Not Found`) y delegar en la validación de firma HMAC (`200 OK` o `401 Unauthorized` ante firma inválida), reservando `404` únicamente para proveedores desconocidos o sin adaptador registrado (AS-23, FR-023, Q15, SC-009).
3. **Inv-URL-03 (Desacoplamiento Estricto de Rutas)**: El backend no debe exigir cambios en los servicios de negocio ni de plantillas de correo; la resolución de dominios públicos se realiza exclusivamente vía configuración de variables de entorno (Q3/A3).

---

## 3. Fallos Esperados y Mitigación

| Escenario de Falla | Comportamiento | Mitigación |
|---|---|---|
| Omisión de `MERCADOPAGO_FRONTEND_BASE_URL` en Render | La pasarela redirige al comensal a `localhost:3000`, provocando pantalla en blanco | Configuración obligatoria en Render Dashboard. |
| Webhook interceptado por Spring Security exigiendo Bearer JWT | Mercado Pago recibe HTTP 401 JWT (`application/problem+json`) y reintenta hasta suspender el webhook | Regla `.requestMatchers("/api/v1/payments/*/webhook", "/api/v1/payments/mercadopago/webhook").permitAll()` en `SecurityConfig`. |
| Slug sin guiones (`/api/v1/payments/mercadopago/webhook`) falla al mapear enum `MERCADO_PAGO` (Q15) | `PaymentMethod.valueOf("MERCADOPAGO")` devuelve HTTP 404 Not Found aunque la ruta esté permitida; o un método sin adaptador (`stripe`) dispara HTTP 400 | Resolución uniforme vía `PaymentMethod.fromWebhookSlug(provider)` y `PaymentGatewayResolver.findAdapter(method)`, retornando `404` exclusivo ante proveedor desconocido o sin adaptador registrado (FR-023, Q15). |

---

## 4. Verificación Observable

1. **Verificación de back_urls en preferencia de Mercado Pago**:
   Crear una preferencia de prueba invocando `POST /api/v1/checkout/pay` (con mock o credenciales) y verificar que el payload generado hacia Mercado Pago incluya:
   ```json
   "back_urls": {
     "success": "https://easyeat-frontend.onrender.com/checkout/payment-return",
     "pending": "https://easyeat-frontend.onrender.com/checkout/payment-return",
     "failure": "https://easyeat-frontend.onrender.com/checkout/payment-return"
   }
   ```
2. **Verificación de acceso anónimo, firma y resolución de webhook (FR-023, Q15)**:
   ```sh
   # Acceso anónimo sin cabecera x-signature: debe responder 200 OK (nunca 401 JWT, 403 ni 404).
   curl -i -X POST http://localhost:8080/api/v1/payments/mercadopago/webhook \
     -H "Content-Type: application/json" \
     -d '{"action":"payment.created","data":{"id":"12345"}}'

   # Acceso con firma inválida (aplica igual a /mercadopago/webhook y /mercado-pago/webhook):
   # debe resolver el adaptador y responder 401 Unauthorized con cuerpo vacío (nunca 404 Not Found).
   curl -i -X POST http://localhost:8080/api/v1/payments/mercadopago/webhook \
     -H "Content-Type: application/json" \
     -H "x-signature: ts=12345,v1=invalid" \
     -d '{"action":"payment.created","data":{"id":"12345"}}'

   # Proveedor desconocido o sin adaptador registrado (ej. stripe): debe responder 404 Not Found.
   curl -i -X POST http://localhost:8080/api/v1/payments/stripe/webhook \
     -H "Content-Type: application/json" \
     -d '{"action":"payment.created","data":{"id":"12345"}}'
   ```
3. **Verificación de enlaces en correo**:
   Solicitar recuperación de contraseña en `POST /api/v1/auth/password-reset/request` y constatar en el log de correo o bandeja de prueba que el link emitido sea:
   `https://easyeat-frontend.onrender.com/reset-password?token=...`

