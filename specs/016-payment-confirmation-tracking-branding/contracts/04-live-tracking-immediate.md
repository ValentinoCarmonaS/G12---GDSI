# Contrato 04: Seguimiento en Vivo para Retiro Inmediato y Actualización Silenciosa

**Ruta de Frontend**: `/checkout/confirmacion/:orderId` (Widget de Seguimiento)  
**Endpoint Backend**: `GET /api/v1/orders/{orderId}/live-tracking`  
**Consumidores**: Comensal con pedido de modalidad `IMMEDIATE`  
**Seguridad**: Autenticación JWT Bearer (`ROLE_COMENSAL`) con validación de titularidad  
**Trazabilidad**: Cubre **AS-5**, **AS-7**, **FR-005**, **FR-007**, **INV-005**, **INV-007**, **CB-004**, **CB-007**, **Q6/A6**, **SC-005**, **SC-007**.

---

## 1. Descripción

Este contrato gobierna la consulta periódica (polling) de seguimiento en vivo para órdenes de retiro inmediato.  
- **Saturación Real (INV-005)**: El tiempo de entrega refleja la carga de comandas activas en la cocina y la demora global cargada por el personal operativo.
- **Actualización Silenciosa (INV-007, Q6/A6)**: Si el personal del KDS ajusta los tiempos de preparación globales, el reloj del comensal refresca fluidamente el valor numérico de los minutos restantes sin desplegar modales, alertas emergentes ni exigir recarga manual de la página.

---

## 2. Definición del Endpoint HTTP

```http
GET /api/v1/orders/{orderId}/live-tracking
Authorization: Bearer <jwt-token-comensal>
Accept: application/json
```

### Cabeceras de Control de Caché en Backend
- `Cache-Control: no-cache, no-store, must-revalidate`
- `Pragma: no-cache`

---

## 3. Respuestas

### 3.1. Orden en Elaboración con Saturación Normal (`HTTP 200 OK`)

```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "ticketNumber": 142,
  "status": "IN_PROGRESS",
  "fulfillmentMode": "IMMEDIATE",
  "isCountdownActive": true,
  "estimatedMinutesRemaining": 18,
  "progressPercentage": 35,
  "isReadyForPickup": false,
  "kdsQueueSaturation": {
    "activeOrdersAhead": 2,
    "kitchenStatus": "NORMAL",
    "globalDelayAppliedMinutes": 0
  },
  "message": "Tu comida se está preparando en la cocina.",
  "serverTime": "2026-11-09T14:05:00Z"
}
```

### 3.2. Orden Tras Ajuste Global de Demora en KDS (+10 min) (`HTTP 200 OK`)

El reloj numérico se actualiza automáticamente de 18 a 28 minutos de manera reactiva:

```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "ticketNumber": 142,
  "status": "IN_PROGRESS",
  "fulfillmentMode": "IMMEDIATE",
  "isCountdownActive": true,
  "estimatedMinutesRemaining": 28,
  "progressPercentage": 20,
  "isReadyForPickup": false,
  "kdsQueueSaturation": {
    "activeOrdersAhead": 2,
    "kitchenStatus": "DELAYED",
    "globalDelayAppliedMinutes": 10
  },
  "message": "Tu comida se está preparando en la cocina.",
  "serverTime": "2026-11-09T14:05:05Z"
}
```

---

## 4. Comportamiento en Frontend (`useOrderLiveTrackingPolling`)

1. El comensal visualiza:
   - Una barra de progreso animada con el porcentaje (`progressPercentage`).
   - Un minutero central dinámico (`estimatedMinutesRemaining` min).
   - Un indicador de estado con el color primario de marca configurado.
2. Cada 5000 ms, el hook invoca este endpoint en segundo plano sin bloquear la pantalla (`isRefreshing = true`).
3. Al recibir un nuevo `estimatedMinutesRemaining`, actualiza el valor del estado de React con una transición suave en el texto numérico sin abrir modales ni banners intrusivos (**AS-7**, **Q6/A6**).

