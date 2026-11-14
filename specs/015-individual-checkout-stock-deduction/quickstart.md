# Quickstart & Verification Guide: Pago de Carrito Individual y Descuento de Stock Transaccional

**Feature Branch**: `015-individual-checkout-stock-deduction`  
**Fecha**: 09/11/2026  
**Especificación**: [spec.md](./spec.md) | [plan.md](./plan.md)  
**Estado**: Completado  

---

## 1. Propósito y Alcance de la Guía

Esta guía proporciona un procedimiento paso a paso ejecutable y determinístico para validar de punta a punta la totalidad de los escenarios de aceptación de la Spec 015:
- **AS-1**: Pago aprobado con débito real de insumos por receta, orden en curso y despacho automático a la cola del KDS.
- **AS-1b**: Pago aprobado sin retorno del comensal a la interfaz (procesamiento autónomo en servidor vía webhook).
- **AS-2**: Rechazo de cobro por insumos de receta agotados por concurrencia al presionar "Pagar", con carrito preservado y cero cobro.
- **AS-3**: Interrupción de cobro por saturación de franja horaria tras superar la ventana de 15 minutos, con carrito preservado y selector reabierto.
- **AS-4**: Confirmación directa de orden con monto cero por bonificación del 100% omitiendo la pasarela de pagos.
- **AS-5**: Retención provisoria de inventario ante estado pendiente de Mercado Pago sin débito real ni envío al KDS, y su resolución posterior.
- **AS-6**: Descuento atómico y estricto de ingredientes sobre recetas enteras y fraccionarias en base de datos.
- **AS-6b**: Descuento atómico de stock en órdenes confirmadas con bonificación del 100%.
- **No persistencia financiera**: Verificación concluyente de cero almacenamiento o tránsito de datos de tarjetas (PCI-DSS SAQ A).

La totalidad de los pasos se ejecutan a **costo cero (RT-1)** en el Sandbox oficial de Mercado Pago, con **equivalencia funcional idéntica a producción (RT-2)** y **reproducibilidad determinística (RT-3)**.

---

## 2. Prerrequisitos y Configuración de Entorno

### 2.1. Variables de Entorno en Backend (`easyeat/backend/.env`)

Asegurar que el backend cuente con el perfil de pruebas/sandbox configurado:

```bash
# Entorno y base de datos
SPRING_PROFILES_ACTIVE=local
DB_URL=jdbc:postgresql://localhost:5432/easyeat_db
DB_USERNAME=easyeat_user
DB_PASSWORD=easyeat_pass

# Mercado Pago Sandbox Credentials (Test User Argentina)
MERCADOPAGO_ACCESS_TOKEN=TEST-1234567890123456-110913-abcdef0123456789abcdef0123456789-123456789
MERCADOPAGO_PUBLIC_KEY=TEST-abcdef01-2345-6789-abcd-ef0123456789
MERCADOPAGO_WEBHOOK_SECRET=test_whsec_abcdef1234567890abcdef1234567890
MERCADOPAGO_SANDBOX=true
MERCADOPAGO_BASE_URL=https://api.mercadopago.com
EASYEAT_FRONTEND_URL=http://localhost:5173
```

### 2.2. Levantar Servicios

```bash
# 1. Base de datos PostgreSQL con Docker Compose
cd easyeat/backend
docker compose up -d postgres

# 2. Compilar e iniciar Backend Spring Boot (aplica migraciones Flyway V1 a V23)
./mvnw spring-boot:run

# 3. En terminal separada: iniciar Frontend React + Vite
cd easyeat/frontend
npm install
npm run dev
```

---

## 3. Matriz de Tarjetas de Prueba Oficiales (Mercado Pago Argentina)

| Escenario | Tarjeta de Prueba | Fecha Vto. | CVV | Nombre del Titular | DNI | Resultado Esperado |
|---|---|---|---|---|---|---|
| **AS-1 / AS-1b (Aprobado)** | `4509 9535 6623 3704` | `11/30` | `123` | `APRO Test` | `12345678` | Cobro aprobado (`approved`), orden `IN_PROGRESS`, stock debitado, comanda en KDS. |
| **AS-5 (Pendiente)** | `4509 9535 6623 3704` | `11/30` | `123` | `PEND Test` | `12345678` | Cobro en revisión (`pending`), orden `PENDING_ACCREDITATION`, sin débito, sin KDS. |
| **Rechazo por Fondos** | `4509 9535 6623 3704` | `11/30` | `123` | `FUND Test` | `12345678` | Cobro rechazado (`rejected`), orden `CANCELLED`, recursos liberados. |
| **Rechazo por Fraude** | `5031 7557 3453 0604` | `11/30` | `123` | `OTHE Test` | `12345678` | Cobro rechazado (`rejected`), alerta en pantalla, carrito preservado. |

---

## 4. Escenarios de Validación Paso a Paso

### 4.1. Escenario AS-1: Pago Aprobado, Débito de Stock, Orden en Curso y Envío a KDS

1. **Estado Inicial**:
   - Ingrediente `Pan de hamburguesa`: Stock inicial = `20.0000` unidades.
   - Ingrediente `Carne vacuna`: Stock inicial = `5.0000` kg.
   - Receta de `Hamburguesa Clásica`: 1 pan, 0.1500 kg carne.
2. **Acción**:
   - Iniciar sesión como comensal en `http://localhost:5173/login`.
   - Agregar 2 `Hamburguesa Clásica` al carrito.
   - Seleccionar horario de retiro y presionar **"Pagar con Mercado Pago"**.
   - En la pasarela de Sandbox, pagar con tarjeta `APRO Test`.
   - Retornar automáticamente al sitio (`/checkout/payment-return`).
3. **Verificación Esperada**:
   - Pantalla muestra mensaje de confirmación exitosa del pedido.
   - El carrito local queda automáticamente vacío (`clearCart()`).
   - En base de datos:
     - `orders.status` transiciona a `IN_PROGRESS`.
     - `ingredients`: `Pan de hamburguesa` = `18.0000` (`20 - 2`); `Carne vacuna` = `4.7000` kg (`5 - 0.3`).
     - `stock_audit_entries`: 2 nuevos registros con `adjustment_mode = 'SUBTRACT'` y los deltas exactos.
     - `kds_tickets`: Nuevo registro creado con `status = 'QUEUED'` y `order_id` asociado.
   - En la vista del Chef (`/cocina`): La tarjeta "En Cola" se incrementa en +1.

---

### 4.2. Escenario AS-1b: Pago Aprobado sin Retorno del Comensal

1. **Acción**:
   - El comensal realiza el pago en la pasarela de Sandbox con tarjeta `APRO Test`.
   - **Inmediatamente tras completarse el pago en MP, el usuario cierra la pestaña del navegador sin hacer clic en "Volver al sitio"**.
   - Mercado Pago envía de forma autónoma el webhook a `POST /api/v1/payments/mercadopago/webhook`.
2. **Verificación Esperada**:
   - El backend procesa el webhook de forma autónoma en el servidor (C5).
   - Valida la firma HMAC-SHA256 (`x-signature`) y reconcilia contra la API de MP.
   - Ejecuta el débito atómico de ingredientes en `ingredients`.
   - Pasa la orden a `IN_PROGRESS` y crea la comanda en `kds_tickets`.
   - Al volver a ingresar el comensal a su perfil (`/profile/orders`), su pedido figura como "En preparación".

---

### 4.3. Escenario AS-2: Insumos Agotados por Concurrencia al Iniciar Cobro

1. **Estado Inicial**:
   - Ingrediente `Pan artesanal de papa`: Stock disponible = `1.0000` unidad.
   - Dos comensales (Usuario A y Usuario B) tienen en su carrito 1 `Hamburguesa Especial` (que requiere 1 pan).
2. **Acción**:
   - Usuario A presiona "Pagar" a las 13:50:00 y consolida la reserva de los 15 minutos.
   - Usuario B presiona "Pagar" a las 13:50:01 antes de que Usuario A complete su pago.
3. **Verificación Esperada**:
   - Usuario B recibe de inmediato un error `HTTP 409 Conflict` con código `INSUFFICIENT_STOCK`.
   - **Cero dinero cobrado a Usuario B** (INV-05).
   - El carrito de Usuario B **se conserva intacto** en su pantalla (CB-02).
   - Un banner destacado alerta: *"El plato 'Hamburguesa Especial' ya no cuenta con stock de insumos disponible. Modificá tu carrito para continuar."*
   - El plato aparece marcado en el catálogo como **"Agotado"** (FR-013).

---

### 4.4. Escenario AS-3: Demora Superior a 15 Minutos y Saturación de Franja Horaria

1. **Estado Inicial**:
   - Franja horaria `14:00 - 14:30` con cupo máximo de 10 reservas. 9 cupos ocupados.
   - Usuario C inicia checkout a las 13:00:00 y obtiene el último cupo con reserva de 15 min (`expires_at = 13:15:00`).
2. **Acción**:
   - Usuario C demora inactivo en la pantalla de pago hasta las 13:16:00 (vence su reserva).
   - A las 13:15:30, Usuario D reserva y confirma el último cupo libre.
   - A las 13:16:30, Usuario C intenta completar su cobro o reanudar el pago.
3. **Verificación Esperada**:
   - El backend detecta que la reserva de Usuario C expiró y que la franja alcanzó su cupo máximo (`10/10`).
   - Interrumpe la transacción de inmediato con `HTTP 409 Conflict` (`SLOT_CAPACITY_EXCEEDED`).
   - **Cero cobro monetario y cero débito de stock** (INV-05).
   - El carrito de Usuario C se preserva íntegro.
   - Se reabre el selector de turnos con el mensaje: *"La franja horaria original se saturó tras expirar tu turno de 15 minutos. Por favor, elegí un nuevo horario."*

---

### 4.5. Escenario AS-4 y AS-6b: Carrito con Monto Cero por Bonificación del 100%

1. **Estado Inicial**:
   - Carrito con monto total igual a `0.00` por promoción comercial del 100%.
2. **Acción**:
   - El comensal revisa su pedido en el Checkout. El botón exhibe: **"Confirmar pedido ($0)"**.
   - El comensal hace clic en confirmar.
3. **Verificación Esperada**:
   - **Omisión total de la pasarela** (FR-005): El usuario jamás es redirigido a Mercado Pago.
   - El sistema ejecuta el débito atómico de materias primas por receta sobre `ingredients` (AS-6b).
   - La orden pasa directamente a `IN_PROGRESS`.
   - Se crea la comanda en `kds_tickets` con `status = 'QUEUED'`.
   - El comensal ve la confirmación directa y su carrito local se vacía.

---

### 4.6. Escenario AS-5: Estado Pendiente y Retención Provisoria

1. **Acción**:
   - El comensal abona en Sandbox utilizando la tarjeta de prueba de resolución pendiente (`PEND Test`).
   - Mercado Pago devuelve `status = 'pending'`, `status_detail = 'pending_contingency'`.
2. **Verificación Esperada**:
   - La orden transiciona a `PENDING_ACCREDITATION`.
   - La retención provisoria de stock y de franja horaria **se mantiene congelada/activa**.
   - **Cero débito de stock real** en la tabla `ingredients` (INV-03).
   - **Cero comandas en el KDS** (`kds_tickets` no contiene la orden, INV-04).
   - La pantalla muestra: *"Tu pago está en proceso de acreditación. Tu pedido entrará a cocina tan pronto como se reciba la aprobación final."*
3. **Resolución Posterior**:
   - Al simular el webhook posterior de aprobación (`status = 'approved'`), el sistema debita el stock real, pasa la orden a `IN_PROGRESS` y emite la comanda a KDS.
   - Si se simula el webhook de rechazo (`status = 'rejected'`), la orden pasa a `CANCELLED` y los recursos de stock y franja se liberan de inmediato (`RELEASED`).

---

### 4.7. Escenarios AS-6 y CB-08: Descuento Atómico y Estricto (Todo o Nada)

1. **Configuración de Prueba**:
   - Plato que requiere 3 ingredientes: $A$ (disponible: 10), $B$ (disponible: 10), $C$ (disponible: 0).
2. **Acción**:
   - Intentar confirmar la orden con el plato.
3. **Verificación Esperada**:
   - La transacción falla con `InsufficientIngredientStockException`.
   - Se ejecuta rollback completo.
   - El ingrediente $A$ sigue teniendo 10 unidades, el ingrediente $B$ sigue teniendo 10 unidades. **Cero descuentos parciales** (INV-01, FR-008).

---

### 4.8. Escenario CB-12 e INV-07: Idempotencia Transaccional de Inventario y KDS

1. **Acción**:
   - Para una orden ya confirmada y en estado `IN_PROGRESS`, emitir manualmente o retransmitir 3 veces consecutivas el mismo webhook de confirmación.
2. **Verificación Esperada**:
   - El backend responde `HTTP 200 OK` en todas las llamadas.
   - El stock de materias primas solo se debitó **una única vez**.
   - La tabla `kds_tickets` contiene **un único ticket** para esa orden.

---

### 4.9. Verificación de Aislamiento Financiero (PCI-DSS SAQ A)

1. **Auditoría de Esquema y Logs**:
   - Inspeccionar la tabla `orders`, `payments`, `temporary_reservations` y `kds_tickets`.
   - Comprobar que no existen columnas de PAN, CVV, titular ni fecha de vencimiento.
   - Comprobar mediante test automatizado que ninguna traza de log registra datos de tarjeta:
   ```bash
   ./mvnw test -Dtest=CardDataIsolationSecurityTest
   ```

