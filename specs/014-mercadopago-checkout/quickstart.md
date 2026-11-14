# Quickstart: Validación Ejecutable de Mercado Pago Checkout (Sandbox)

**Feature Branch**: `014-mercadopago-checkout`  
**Fecha**: 09/11/2026  
**Especificación**: [spec.md](./spec.md) | [data-model.md](./data-model.md) | [contracts/](./contracts/)  
**Estado**: Completado  

---

## 1. Prerrequisitos y Configuración de Entorno

### 1.1. Variables de Entorno de Sandbox
Crear o verificar el archivo `easyeat/backend/.env` (o configurar variables de entorno para ejecución local):

```bash
# Entorno de pruebas oficiales de Mercado Pago (RT-1, RT-3)
MERCADOPAGO_SANDBOX=true
MERCADOPAGO_ACCESS_TOKEN=TEST-1234567890123456-110902-abcdef1234567890abcdef1234567890-123456789
MERCADOPAGO_PUBLIC_KEY=TEST-12345678-abcd-1234-ef01-1234567890ab
MERCADOPAGO_WEBHOOK_SECRET=test_whsec_1234567890abcdef1234567890abcdef
EASYEAT_FRONTEND_URL=http://localhost:5173
```

### 1.2. Inicio de Servicios de Desarrollo
```bash
# Terminal 1: Backend con base de datos PostgreSQL local o Testcontainers
cd easyeat/backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2: Frontend SPA
cd easyeat/frontend
npm run dev
```

---

## 2. Escenarios Ejecutables de Validación (AS-1 a AS-6)

Todos los escenarios son ejecutables en el entorno Sandbox sin costo financiero (RT-1) con comportamiento idéntico a producción (RT-2).

---

### Escenario 1: AS-1 — Inicio de Pago y Reserva Temporal Conjunta

**Objetivo**: Constatar que presionar "Pagar" bloquea atómicamente la franja y el stock de la orden y retorna la URL de redirección a Sandbox.

#### Pasos de ejecución
1. Iniciar sesión como comensal (`diner@easyeat.com`).
2. Agregar al carrito 2 unidades de un plato disponible y seleccionar una franja horaria válida.
3. Enviar la petición de inicio de pago (o hacer clic en "Pagar" en el checkout):
   ```bash
   curl -X POST http://localhost:8080/api/v1/checkout/pay \
     -H "Authorization: Bearer $DINER_JWT" \
     -H "Content-Type: application/json" \
     -d '{
       "establishmentId": "'$ESTABLISHMENT_ID'",
       "fulfillmentMode": "SCHEDULED",
       "pickupDate": "2026-11-10",
       "timeSlotStart": "13:00:00",
       "timeSlotEnd": "13:15:00",
       "items": [{"dishId": "'$DISH_ID'", "quantity": 2}]
     }'
   ```

#### Resultado Esperado
- Código HTTP `201 Created`.
- Retorna `orderId`, `reservationId` con `expiresAt` fijado en exactamente 15 minutos en el futuro.
- Retorna `redirectUrl` apuntando a `https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=...`.
- La franja horaria descuenta 1 cupo y el stock de cocina refleja 2 unidades comprometidas.

---

### Escenario 2: AS-2 — Pago Aprobado y Confirmación de Orden (con Retorno)

**Objetivo**: Simular el pago exitoso en Mercado Pago y el retorno al checkout, verificando la consolidación definitiva de recursos y el vaciado del carrito (Q4).

#### Pasos de ejecución
1. Abrir la `redirectUrl` generada en AS-1 en el navegador (ventana de incógnito).
2. Seleccionar "Nueva tarjeta de crédito" e ingresar la tarjeta de prueba de aprobación:
   - **Número**: `4509 9535 6623 3704`
   - **Vencimiento**: `11/30` | **CVV**: `123`
   - **Titular**: `APRO Test` | **DNI**: `12345678`
3. Completar el pago. Mercado Pago aprueba la transacción y redirige automáticamente a:
   `http://localhost:5173/checkout/payment-return?status=success&orderId=...`

#### Resultado Esperado
- La SPA consulta `GET /api/v1/orders/{orderId}/payment-status` y recibe `orderStatus: CONFIRMED` y `reservationStatus: CONFIRMED`.
- La función `clearCart()` de `CartContext` se ejecuta automáticamente (Q4): el carrito local queda en 0 ítems.
- Se despliega la pantalla de confirmación exitosa con el resumen del pedido.

---

### Escenario 3: AS-2b — Pago Aprobado sin Retorno del Comensal (Vía Webhook)

**Objetivo**: Demostrar que si el comensal cierra el navegador tras pagar en Mercado Pago, el Webhook consolida de igual forma la reserva y la orden (FR-003, INV-08).

#### Pasos de ejecución
1. Iniciar un pago para una nueva orden (obteniendo `orderId`).
2. Simular el envío de webhook por parte de Mercado Pago con firma válida:
   ```bash
   PAYMENT_ID="9988776655"
   TS=$(date +%s%3N)
   REQ_ID="test-req-001"
   MANIFEST="id:$PAYMENT_ID;request-id:$REQ_ID;ts:$TS;"
   V1=$(echo -n "$MANIFEST" | openssl dgst -sha256 -hmac "$MERCADOPAGO_WEBHOOK_SECRET" | sed 's/^.* //')

   curl -X POST http://localhost:8080/api/v1/payments/mercadopago/webhook \
     -H "Content-Type: application/json" \
     -H "x-request-id: $REQ_ID" \
     -H "x-signature: ts=$TS,v1=$V1" \
     -d '{
       "action": "payment.updated",
       "data": { "id": "'$PAYMENT_ID'" },
       "type": "payment"
     }'
   ```

#### Resultado Esperado
- Respuesta HTTP `200 OK`.
- En base de datos, `temporary_reservations.status` pasa a `'CONFIRMED'`, `orders.status` pasa a `'CONFIRMED'`.
- La reserva y el cupo se mantienen definitivos sin requerir ninguna acción adicional del comensal.

---

### Escenario 4: AS-3 — Pago Rechazado por Fondos Insuficientes y Restitución

**Objetivo**: Verificar que un pago rechazado restituye inmediatamente los recursos y aterriza en el checkout con el carrito y la franja preservados (Q2, FR-005, FR-006).

#### Pasos de ejecución
1. Iniciar un pago desde el checkout.
2. En la pasarela de Sandbox, pagar con tarjeta de fondos insuficientes:
   - **Número**: `4509 9535 6623 3704` | **Venc**: `11/30` | **CVV**: `123`
   - **Titular**: `FUND Test`
3. Mercado Pago rechaza el cobro. Clic en "Volver al sitio".
4. El navegador aterriza en `http://localhost:5173/checkout/payment-return?status=failure&orderId=...`.

#### Resultado Esperado
- El backend marca `temporary_reservations.status = 'RELEASED'` y `orders.status = 'PENDING_PAYMENT'`.
- El cupo de la franja y el stock de platos se restituyen inmediatamente a la disponibilidad general.
- La pantalla de checkout muestra un banner visible: *"El pago no se concretó (fondos insuficientes)"*.
- El carrito y la franja seleccionada se mantienen intactos. El botón "Pagar" se encuentra re-habilitado.

---

### Escenario 5: AS-4 — Abandono de Pasarela con Retorno Voluntario

**Objetivo**: Comprobar que cancelar la operación en Mercado Pago y volver al sitio libera los recursos y permite reintentar de inmediato.

#### Pasos de ejecución
1. Iniciar el pago y acceder a la interfaz de Mercado Pago.
2. Hacer clic voluntario en el enlace "Cancelar y volver al sitio".
3. Aterrizar en `/checkout/payment-return?status=failure`.

#### Resultado Esperado
- El backend libera la reserva temporal (`RELEASED` por `CANCELLED_BY_USER`).
- El comensal aterriza en `/checkout` con banner: *"Operación cancelada"*.
- El comensal puede reintentar con un solo clic si los recursos continúan libres.

---

### Escenario 6: AS-4b — Abandono sin Retorno y Vencimiento de Ventana (15 Minutos)

**Objetivo**: Verificar que el barredor en background libera automáticamente el cupo y el stock al expirar los 15 minutos (FR-009, INV-04, SC-004).

#### Pasos de ejecución
1. Crear una orden y reserva temporal.
2. No interactuar más (simulando abandono de navegador).
3. Adelantar el reloj o ejecutar el test de expiración:
   ```bash
   cd easyeat/backend
   ./mvnw test -Dtest=OrderExpirationSchedulerTest
   ```

#### Resultado Esperado
- Al cumplirse `expires_at <= now()`, el scheduler `OrderExpirationScheduler` (extendido) marca `temporary_reservations.status = 'EXPIRED'` y `orders.status = 'EXPIRED'`.
- Cero (0) recursos quedan retenidos indefinidamente en el sistema (SC-004).

---

### Escenario 7: AS-5 — Prevención de Cobro y Reserva Duplicada (Panel Pago en Curso)

**Objetivo**: Verificar que un comensal con un pago activo no puede duplicar la reserva ni el cobro, y cuenta con las acciones "Retomar pago" y "Cancelar intento" (Q3, FR-010).

#### Pasos de ejecución
1. Iniciar un pago para la orden A (quedando en estado `IN_PROCESS`).
2. Abrir una nueva pestaña en el navegador y navegar a `/checkout`.

#### Resultado Esperado
- El checkout detecta el pago en curso y despliega el **Panel Informativo "Pago en curso"** sin cuenta regresiva (Q3).
- El botón "Pagar" está estrictamente **deshabilitado**.
- Al presionar **"Retomar pago"**, se reabre la pasarela de Mercado Pago con la misma preferencia.
- Al presionar **"Cancelar intento"**:
  - Llama a `POST /api/v1/checkout/cancel-payment`.
  - La reserva se libera en el acto (`RELEASED`).
  - El panel desaparece y el botón "Pagar" se re-habilita para un nuevo intento.

---

### Escenario 8: AS-6 — Fallo en el Inicio del Pago y Reversión de Recursos

**Objetivo**: Demostrar que ante fallas de red o indisponibilidad de Mercado Pago, cualquier reserva iniciada se cancela instantáneamente (FR-011, CB-06).

#### Pasos de ejecución
1. Ejecutar el test automatizado de resiliencia con simulación de fallo de enlace:
   ```bash
   cd easyeat/backend
   ./mvnw test -Dtest=MercadoPagoGatewayFailureIntegrationTest
   ```

#### Resultado Esperado
- El servicio recibe una excepción de red simulada (`SocketTimeoutException`).
- El backend ejecuta rollback / cancelación inmediata de la reserva temporal (`RELEASED` por `GATEWAY_ERROR`).
- Retorna `HTTP 502 Bad Gateway` con mensaje claro al comensal.
- La base de datos no retiene cupos ni stock.

---

## 3. Verificación de Seguridad y Aislamiento de Tarjetas (INV-11, SC-009)

Para verificar que ninguna traza o payload en el proyecto captura o persiste datos de tarjeta:
```bash
# Búsqueda estricta en el código fuente de backend y frontend de palabras prohibidas
git grep -i -E "cardNumber|creditCardNumber|card_number|securityCode|cvv|cvc" easyeat/
```
**Resultado esperado**: Cero (0) coincidencias en controladores, servicios, repositorios, entidades JPA y componentes de frontend.

