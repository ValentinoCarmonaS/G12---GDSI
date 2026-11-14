# Contrato 06: Transición a "Listo para Retirar" y Supresión de Tiempo

**Ruta de Frontend**: `/checkout/confirmacion/:orderId` (Widget de Seguimiento)  
**Endpoint de Notificación KDS**: `POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup`  
**Endpoint de Consulta Comensal**: `GET /api/v1/orders/{orderId}/live-tracking`  
**Consumidores**: Personal de KDS (Cocina/Mostrador) y Comensal en espera  
**Seguridad**: `ROLE_CHEF` o `ROLE_ENCARGADO` para disparar la finalización; `ROLE_COMENSAL` para consultar  
**Trazabilidad**: Cubre **AS-8**, **FR-008**, **INV-008**, **CB-008**, **SC-008**.

---

## 1. Descripción

Este contrato gobierna la culminación del ciclo de preparación en cocina y la transición definitiva de la vista del comensal.  
- **Irreversibilidad (INV-008)**: Al marcarse la comanda como lista, el tiempo dinámico y la barra de progreso se suprimen irrevocablemente.
- **Destaque Visual de Mostrador (CB-008)**: La interfaz adopta un contenedor verde vibrante de alta visibilidad, con el número de orden destacado en tipografía de gran formato (`text-5xl font-black`) y aviso de retiro inmediato.
- **Cese de Polling**: El cliente detiene las solicitudes HTTP periódicas al alcanzar este estado terminal de cocina.

---

## 2. Acción de Cocina: Marca de Finalización en KDS

```http
POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup
Authorization: Bearer <jwt-token-chef>
Content-Type: application/json
```

### Respuesta Exitosa (`HTTP 200 OK`)
```json
{
  "ticketId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "ticketNumber": 142,
  "status": "READY_FOR_PICKUP",
  "readyAt": "2026-11-09T14:22:15Z"
}
```

---

## 3. Consulta de Seguimiento por el Comensal (`HTTP 200 OK`)

En el siguiente ciclo de polling (o al recargar la vista), el comensal recibe:

```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "ticketNumber": 142,
  "status": "READY_FOR_PICKUP",
  "fulfillmentMode": "IMMEDIATE",
  "isCountdownActive": false,
  "estimatedMinutesRemaining": null,
  "progressPercentage": 100,
  "isReadyForPickup": true,
  "message": "¡Tu pedido está listo! Por favor, acercate al mostrador para retirar tu comida.",
  "serverTime": "2026-11-09T14:22:20Z"
}
```

---

## 4. Comportamiento Observable en Frontend

1. **Desaparición de Minuteros y Barras**: Se remueven por completo la barra de progreso y el minutero regresivo.
2. **Despliegue del Banner de Retiro en Mostrador**:
   ```html
   <div class="bg-emerald-600 text-white rounded-2xl p-8 text-center shadow-lg animate-pulse-once">
     <div class="inline-flex p-3 bg-white/20 rounded-full mb-3">
       <CheckCircle class="w-12 h-12 text-white" />
     </div>
     <h2 class="text-2xl font-bold tracking-tight">¡Tu comida está lista para retirar!</h2>
     <p class="text-emerald-100 text-sm mt-1">Acercate al mostrador con tu número de orden:</p>
     <div class="mt-4 py-3 px-6 bg-white rounded-xl shadow-inner inline-block">
       <span class="text-5xl font-black text-gray-900 tracking-wider">#142</span>
     </div>
   </div>
   ```
3. **Detención Inmediata del Hook de Polling**: `useOrderLiveTrackingPolling` cancela los timers pendientes y detiene las peticiones de red.

