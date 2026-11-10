# Contrato 05: Seguimiento en Vivo para Reserva Programada y Umbral T-15

**Ruta de Frontend**: `/checkout/confirmacion/:orderId` (Widget de Seguimiento)  
**Endpoint Backend**: `GET /api/v1/orders/{orderId}/live-tracking`  
**Consumidores**: Comensal con pedido de modalidad `SCHEDULED`  
**Seguridad**: Autenticación JWT Bearer (`ROLE_COMENSAL`) con validación de titularidad  
**Trazabilidad**: Cubre **AS-6**, **FR-006**, **INV-006**, **Q1/A1**, **CB-005**, **CB-006**, **SC-006**.

---

## 1. Descripción

Este contrato gobierna la exhibición y conmutación reactiva del estado de seguimiento para órdenes programadas a futuro.  
- **Fase Estática (> 15 minutos, INV-006, CB-005)**: Mientras falten más de 15 minutos para el inicio de la franja horaria pactada, el sistema exhibe de manera fija la fecha y el rango horario del turno acordado. Queda estrictamente prohibido mostrar un contador regresivo.
- **Fase de Proximidad Activa ($\le$ 15 minutos, Q1/A1, CB-006)**: Exactamente al alcanzar los 15 minutos previos al inicio de la franja, el backend activa `isCountdownActive = true` y el frontend conmuta automáticamente a una cuenta regresiva dinámica en tiempo real hacia el turno.

---

## 2. Respuestas

### 2.1. Fase Estática: Más de 15 Minutos para el Turno (`HTTP 200 OK`)

Ejemplo: Son las 12:30 hs y el turno pactado es a las 14:00 hs (faltan 90 minutos):

```json
{
  "orderId": "7c1e3b5a-4977-44bc-b12a-579e0a0d92f5",
  "ticketNumber": 156,
  "status": "IN_PROGRESS",
  "fulfillmentMode": "SCHEDULED",
  "pickupDate": "2026-11-09",
  "timeSlotStart": "14:00:00",
  "timeSlotEnd": "14:30:00",
  "isCountdownActive": false,
  "estimatedMinutesRemaining": null,
  "progressPercentage": 10,
  "isReadyForPickup": false,
  "message": "Tu turno de retiro está programado para hoy entre las 14:00 y las 14:30 hs.",
  "serverTime": "2026-11-09T12:30:00Z"
}
```

#### Comportamiento en Frontend
- El widget oculta el reloj regresivo.
- Muestra tarjeta destacada con icono de calendario:
  - **Fecha**: `Lunes 09/11/2026`
  - **Horario Pactado**: `14:00 - 14:30 hs`
  - **Nota**: *"El contador en vivo se activará 15 minutos antes de tu turno (a las 13:45 hs)."*

---

### 2.2. Fase de Proximidad: 15 Minutos o Menos para el Turno (`HTTP 200 OK`)

Ejemplo: Son las 13:46 hs y el turno pactado es a las 14:00 hs (faltan 14 minutos):

```json
{
  "orderId": "7c1e3b5a-4977-44bc-b12a-579e0a0d92f5",
  "ticketNumber": 156,
  "status": "IN_PROGRESS",
  "fulfillmentMode": "SCHEDULED",
  "pickupDate": "2026-11-09",
  "timeSlotStart": "14:00:00",
  "timeSlotEnd": "14:30:00",
  "isCountdownActive": true,
  "estimatedMinutesRemaining": 14,
  "progressPercentage": 60,
  "isReadyForPickup": false,
  "message": "¡Tu turno de retiro está próximo! La cocina está comenzando la preparación de tu orden.",
  "serverTime": "2026-11-09T13:46:00Z"
}
```

#### Comportamiento en Frontend
- Conmutación automática a reloj regresivo en vivo sin recarga de pantalla (**AS-6**, **CB-006**).
- Minutero: `14:00` descendente segundo a segundo.
- Cadencia de polling acelerada a 3000 ms para reflejar transiciones inmediatas.

