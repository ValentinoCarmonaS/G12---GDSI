# Contract 07: Despacho y Encolado de Comandas en KDS

**Tipo de Contrato**: Contrato de Servicio Transaccional (`KdsQueueService`) y Endpoint REST para Cocina  
**Componente Responsable**: `com.easyeat.order.service.KdsQueueService`  
**Consumidores**: `CheckoutPaymentService` (al confirmarse la orden) y Pantalla de KDS (`KitchenWorkspacePage.tsx`)  
**Seguridad**: Autorización por roles (`ROLE_CHEF` para lectura/gestión de cocina)  

---

## 1. Descripción

Este contrato gobierna la creación y el despacho atómico de una comanda hacia la cola del Kitchen Display System (KDS) ni bien una orden alcanza de forma justificada el estado `IN_PROGRESS` con débito real de ingredientes concretado (INV-04, FR-001, FR-005, CB-01, CB-04).  
Reglas operativas obligatorias:
1. **Condición Inseparable (INV-04)**: Una comanda solo se crea si el cobro fue aprobado (o bonificado al 100%) y el débito de materias primas fue exitoso.
2. **Idempotencia Absoluta (INV-07)**: La tabla `kds_tickets` cuenta con una restricción `UNIQUE (order_id)`. Reenvíos de webhooks o eventos duplicados no generan más de una comanda para un mismo pedido.
3. **Ordenamiento de Cola**: Los tickets en cola se ordenan primariamente por fecha y hora de entrega (`pickup_date ASC, time_slot_start ASC`), priorizando la modalidad `IMMEDIATE` sobre `SCHEDULED` para una misma franja horaria.

---

## 2. Invocación de Despacho (Java Domain Contract)

```java
public interface KdsQueueService {

    /**
     * Encola de forma atómica e idempotente la orden confirmada en la cola del KDS.
     *
     * @param order Orden confirmada en estado IN_PROGRESS.
     * @return Comanda encolada generada o existente.
     */
    KdsTicket dispatchOrderToKitchen(Order order);
}
```

---

## 3. Endpoint de Consulta de Comandas para el Chef (KDS View)

```http
GET /api/v1/kitchen/orders?status=QUEUED&page=0&size=20
Authorization: Bearer <jwt-token-chef>
```

### Respuesta Exitosa (`HTTP 200 OK`)

```json
{
  "content": [
    {
      "ticketId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "ticketNumber": 142,
      "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "status": "QUEUED",
      "fulfillmentMode": "SCHEDULED",
      "pickupDate": "2026-11-09",
      "timeSlotStart": "14:00:00",
      "timeSlotEnd": "14:30:00",
      "queuedAt": "2026-11-09T13:45:00Z",
      "items": [
        {
          "dishId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
          "dishName": "Hamburguesa Clásica",
          "quantity": 2
        },
        {
          "dishId": "f7e8d9c0-b1a2-3f4e-5d6c-7b8a9f0e1d2c",
          "dishName": "Papas Fritas Rústicas",
          "quantity": 1
        }
      ]
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1,
  "totalPages": 1
}
```

---

## 4. Endpoint de Métricas del Dashboard de KDS (`KitchenWorkspacePage.tsx`)

Alimenta en tiempo real las cuatro tarjetas de estado del KDS:

```http
GET /api/v1/kitchen/dashboard/summary
Authorization: Bearer <jwt-token-chef>
```

### Respuesta Exitosa (`HTTP 200 OK`)

```json
{
  "queuedOrdersCount": 3,
  "inPreparationOrdersCount": 2,
  "readyForPickupOrdersCount": 1,
  "completedTodayCount": 28
}
```

---

## 5. Semántica de Idempotencia y Manejo de Conflictos

- Si se intenta despachar una orden que ya posee un ticket en `kds_tickets`, el método captura `DataIntegrityViolationException` (o verifica previamente la existencia mediante `existsByOrderId`) y devuelve el ticket existente sin arrojar error ni duplicar el registro en la cola.

