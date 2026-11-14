# Guía rápida de configuración y validación: checkout Mercado Pago, webhook y pedidos

**Especificación**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Rama**: `018-checkout-mp-tracking-orders` | **Fecha**: 11/11/2026

Esta guía centraliza la ejecución local, la configuración productiva en Render con Mercado Pago y los pasos de verificación integral del incremento `018`. *(Las especificaciones cerradas de `017-render-deploy-docker` se mantienen sin modificaciones por decisión de alcance).*

---

## 1. Requisitos e inicio local

### 1.1 Infraestructura auxiliar (PostgreSQL y Mailpit)

```bash
cd easyeat/backend
docker compose up -d
```

- PostgreSQL local: `localhost:5432` (base `easyeat`, usuario `easyeat`, contraseña `easyeat_dev_password`).
- Mailpit SMTP/Web UI: `localhost:1025` / `http://localhost:8025` (opcional para ver correos de comprobante localmente).

### 1.2 Backend (Spring Boot)

```bash
cd easyeat/backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Variables recomendadas para probar checkout y webhook contra ngrok/túnel o sandbox local (ver referencia en `easyeat/backend/.env.example`):

| Variable | Uso local (`dev`) | Uso productivo / Render |
| --- | --- | --- |
| `MERCADOPAGO_ACCESS_TOKEN` | Vacío (simulado) o token de prueba `TEST-...` / `APP_USR-...` | **Obligatorio**: Access Token del vendedor |
| `MERCADOPAGO_PUBLIC_KEY` | Vacío o Public Key de prueba | **Obligatorio**: Public Key de la aplicación |
| `MERCADOPAGO_WEBHOOK_SECRET` | Secreto de prueba si se envían webhooks firmados | **Obligatorio**: clave secreta del webhook configurada en Mercado Pago Developers (`401` fail-closed si falta) |
| `MERCADOPAGO_SANDBOX` | `true` (o `false` con credenciales `APP_USR-...` de prueba modernas) | Según tipo de credencial (`false` para producción o prueba Checkout Pro moderno con `APP_USR-...`) |
| `MERCADOPAGO_FRONTEND_BASE_URL` | `http://localhost:3000` (sin importar barra final) | URL pública HTTPS del frontend en Render |
| `MERCADOPAGO_BACKEND_BASE_URL` | Vacío (omite `notification_url`) o URL HTTPS de túnel | URL pública HTTPS del backend en Render (respaldo automático con `RENDER_EXTERNAL_URL` si no se define explícitamente) |

### 1.3 Frontend (React + Vite)

```bash
cd easyeat/frontend
npm install
npm run dev
```

Disponible en `http://localhost:3000` con proxy `/api` hacia `http://localhost:8080`.

---

## 2. Configuración en Render y panel de Mercado Pago Developers

1. En el servicio Web de backend en Render, verificar que estén definidas:
   - `MERCADOPAGO_ACCESS_TOKEN`
   - `MERCADOPAGO_PUBLIC_KEY`
   - `MERCADOPAGO_WEBHOOK_SECRET` *(obligatoria: ante un encabezado `x-signature`, si esta variable falta o está vacía, el backend rechazará con `401 Unauthorized` y emitirá una alerta explícita en los logs)*
   - `MERCADOPAGO_FRONTEND_BASE_URL` (ejemplo: `https://easyeat-frontend.onrender.com`, sin necesidad de preocuparse por barra final)
   - `MERCADOPAGO_BACKEND_BASE_URL` (ejemplo: `https://easyeat-backend.onrender.com`; si Render expone `RENDER_EXTERNAL_URL`, actúa como respaldo automático)
   - `MERCADOPAGO_SANDBOX` (`false` al operar con credenciales `APP_USR-...` estándar o de usuario de prueba moderno)
2. En **Mercado Pago Developers → Tu integración → Webhooks**:
   - Registrar la URL de notificaciones: `<URL_PUBLICA_BACKEND>/api/v1/payments/mercadopago/webhook`
   - Activar el evento **Pagos (`payment`)**.
   - Copiar la **Clave secreta** generada e introducirla exactamente en `MERCADOPAGO_WEBHOOK_SECRET` dentro de Render.
3. Si SMTP (`SPRING_MAIL_HOST`) no está habilitado en Render, el backend registrará igualmente el comprobante en `order_receipts` de forma asincrónica (`AFTER_COMMIT`) y el comensal contará con visibilidad completa desde `/checkout/confirmacion/:orderId` y `/perfil#pedidos`.

---

## 3. Verificación automatizada

### 3.1 Backend

```bash
cd easyeat/backend
mvn test -Dtest="*Order*,*Payment*,*Webhook*,*Kds*,*MercadoPago*,*Checkout*"
```

Luego ejecutar la suite completa de regresión cuando corresponda antes de integrar a `017-render-deploy-docker`:

```bash
cd easyeat/backend
mvn test
```

### 3.2 Frontend

Vitest ejecuta automáticamente ambas carpetas de pruebas (`src/test/` y `tests/`):

```bash
cd easyeat/frontend
npm test -- --run
npm run build
```

---

## 4. Validación manual de punta a punta en Render

1. **Aislamiento multi-rol previo**: Abrir una pestaña normal con sesión `ADMINISTRADOR` o `CHEF` (por ejemplo `/cocina`) y otra pestaña normal con rol `COMENSAL`. Confirmar que las pestañas mantienen sesiones independientes.
2. **Prueba de pago con ventana de incógnito (`APP_USR-...` de prueba)**:
   - Agregar platos al carrito como `COMENSAL`, ir a `/checkout` y confirmar el pago.
   - Copiar o seguir la redirección hacia Checkout Pro (`init_point`).
   - En credenciales de prueba modernas `APP_USR-...`, abrir o completar el flujo de Mercado Pago en una **ventana de incógnito** limpia iniciando sesión con una **cuenta compradora de prueba** de Mercado Pago o formulario limpio de tarjeta de prueba (evitando conflicto de cookies con la cuenta vendedora).
3. **Verificación de retorno aprobado y webhook**:
   - Al completar el pago aprobado, el comensal regresa a `/checkout/payment-return`, se vacía el carrito y redirige automáticamente a `/checkout/confirmacion/:orderId`.
   - Confirmar en los logs del backend ausencia de `401` en webhook, stock descontado una única vez, creación única de comanda en `/cocina` y número real `#ticketNumber` visible en pantalla.
4. **Verificación de acreditación pendiente (`PENDING_ACCREDITATION`)**:
   - Con un medio/tarjeta en revisión (`pending`), verificar que `/checkout/payment-return` ofrece `"Ver seguimiento"` y `"Ir a Mis pedidos"`, y que `/pedidos/:orderId/seguimiento` muestra la vista provisional sin banner verde, sin aviso de comprobante ni barra de cocina activa, y con `ticketNumber` oculto hasta ingreso real a cocina.
5. **Recuperación de `409` y persistencia multi-pestaña**:
   - Iniciar un pago sin completarlo; abrir una nueva pestaña del mismo navegador (reautenticando si la sesión de pestaña no existe). Verificar que el carrito (`localStorage`) conserva los platos, la pestaña de `CHEF`/`ADMINISTRADOR` sigue aislada, y `/checkout` muestra inmediatamente el panel de orden en curso con `"Retomar pago"` (solo si existe `redirectUrl`) y `"Cancelar intento"`.
   - Probar `"Cancelar intento"`, verificar liberación del cupo y creación de un nuevo pedido.
6. **Sección `"Mis pedidos"` en escritorio y móvil**:
   - Verificar que el enlace `"Mis pedidos"` (`/perfil#pedidos`) está visible también en ancho móvil (`< 640px`), abre directamente la pestaña de pedidos en `/perfil` y ofrece acciones acordes a cada estado.

