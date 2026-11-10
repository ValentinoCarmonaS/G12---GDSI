# Contrato Operativo 08: Sincronización de URLs Públicas

**Artefacto**: `com.easyeat.order.service.MercadoPagoGatewayAdapter`, `application.yml` y `SecurityConfig`  
**Propósito**: Asegurar que las redirecciones de pasarela de pago (Mercado Pago Checkout Pro) y los hipervínculos transaccionales de correo electrónico utilicen los dominios públicos HTTPS de Render, eliminando referencias a `localhost` en producción y manteniendo accesibles los webhooks externos (HU-06, FR-022 a FR-024, AS-21 a AS-24, Q3).  

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
- **Ruta del Endpoint**: `POST /api/v1/payments/{provider}/webhook` y `POST /api/v1/payments/mercadopago/webhook`
- **Protocolo y Red**: HTTPS sobre dominio público del backend (`https://easyeat-backend.onrender.com/api/v1/payments/mercadopago/webhook`).
- **Seguridad**:
  - Exento de autenticación JWT (`permitAll()` en `SecurityConfig`).
  - Validación criptográfica mediante cabecera `x-signature` y validación HMAC-SHA256 con `MERCADOPAGO_WEBHOOK_SECRET` en `WebhookSignatureValidator`.
- **Respuesta esperada ante notificación válida**: HTTP 200 OK.

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
2. **Inv-URL-02 (Disponibilidad Pública de Webhooks)**: El endpoint `/api/v1/payments/mercadopago/webhook` debe ser accesible desde la red pública sin requerir credenciales ni tokens de usuario, permitiendo que los servidores de Mercado Pago confirmen las transacciones (AS-23, FR-023, SC-009).
3. **Inv-URL-03 (Desacoplamiento Estricto de Rutas)**: El backend no debe exigir cambios en los servicios de negocio ni de plantillas de correo; la resolución de dominios públicos se realiza exclusivamente vía configuración de variables de entorno (Q3/A3).

---

## 3. Fallos Esperados y Mitigación

| Escenario de Falla | Comportamiento | Mitigación |
|---|---|---|
| Omisión de `MERCADOPAGO_FRONTEND_BASE_URL` en Render | La pasarela redirige al comensal a `localhost:3000`, provocando pantalla en blanco | Configuración obligatoria en Render Dashboard. |
| Webhook interceptado por Spring Security exigiendo Bearer JWT | Mercado Pago recibe HTTP 401 y reintenta infinitamente hasta suspender el webhook | Regla `.requestMatchers("/api/v1/payments/*/webhook", "/api/v1/payments/mercadopago/webhook").permitAll()` en `SecurityConfig`. |

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
2. **Verificación de acceso anónimo a webhook**:
   ```sh
   curl -i -X POST http://localhost:8080/api/v1/payments/mercadopago/webhook \
     -H "Content-Type: application/json" \
     -d '{"action":"payment.created","data":{"id":"12345"}}'
   # No debe retornar 401 ni 403.
   ```
3. **Verificación de enlaces en correo**:
   Solicitar recuperación de contraseña en `POST /api/v1/auth/password-reset/request` y constatar en el log de correo o bandeja de prueba que el link emitido sea:
   `https://easyeat-frontend.onrender.com/reset-password?token=...`

