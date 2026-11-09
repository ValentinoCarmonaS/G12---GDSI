# Contract 05: Interrupción por Franja Horaria Saturada tras Expiración (AS-3)

**Endpoint**: `POST /api/v1/checkout/pay` (o reanudación de pago)  
**Consumidor**: Comensal solitario tras demorar más de 15 minutos en el proceso de pago  
**Seguridad**: Autenticación Bearer JWT (`ROLE_COMENSAL`)  
**Aislamiento Financiero**: Cero cobro, cero débito de stock (INV-05, CB-03, AS-3)  

---

## 1. Descripción

Este contrato gobierna la interrupción inmediata de la transacción cuando el comensal demora más de 15 minutos en la pantalla de pago (provocando la expiración y liberación de su reserva temporal), y la franja horaria previamente elegida alcanza su cupo máximo de reservas por compras de otros usuarios antes de que intente completar la operación (AS-3, CB-03, Q1, INV-08).  
Reglas operativas obligatorias:
1. **Inocuidad Monetaria (INV-05)**: No se debita dinero ni se consolida la transacción.
2. **Inocuidad de Stock**: Cero ingredientes debitados en el inventario.
3. **Preservación del Carrito (CB-03)**: Los platos seleccionados permanecen intactos en el carrito de compras.
4. **Reapertura de Horarios**: Se indica al comensal que la franja horaria original se saturó y se lo redirige/reabre el selector de horarios disponibles.

---

## 2. Petición de Confirmación o Reintento de Pago

```http
POST /api/v1/checkout/pay
Content-Type: application/json
Authorization: Bearer <jwt-token>

{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "paymentMethod": "MERCADO_PAGO"
}
```

---

## 3. Respuesta de Interrupción por Franja Saturada (`HTTP 409 Conflict`)

Formato estandarizado según RFC 7807 (`ProblemDetail` / `application/problem+json`):

```json
{
  "type": "https://easyeat.com/errors/slot-capacity-exceeded",
  "title": "Franja horaria saturada tras expiración de ventana de 15 minutos",
  "status": 409,
  "detail": "La franja horaria previamente seleccionada ha alcanzado su cupo máximo luego de expirar tu reserva de 15 minutos. Por favor, elegí un nuevo horario de retiro.",
  "code": "SLOT_CAPACITY_EXCEEDED",
  "instance": "/api/v1/checkout/pay",
  "timestamp": "2026-11-09T13:48:30Z",
  "expiredSlot": {
    "date": "2026-11-09",
    "timeSlotStart": "14:00:00",
    "timeSlotEnd": "14:30:00",
    "capacity": 10,
    "availableSlots": 0
  },
  "cartPreserved": true,
  "actionRequired": "SELECT_NEW_TIME_SLOT"
}
```

### Campos de Salida

| Campo | Tipo | Descripción |
|---|---|---|
| `status` | Integer | Código HTTP: `409`. |
| `code` | String | Código de error de dominio: `SLOT_CAPACITY_EXCEEDED`. |
| `detail` | String | Mensaje explicativo para presentar al usuario en un modal o alerta destacada. |
| `expiredSlot` | Object | Datos de la franja horaria que se encuentra saturada. |
| `cartPreserved` | Boolean | Rigurosamente `true` (los ítems del carrito no se borran). |
| `actionRequired` | String | Directiva para el cliente: `SELECT_NEW_TIME_SLOT`. |

---

## 4. Comportamiento en Frontend

1. La interfaz intercepta el error `409` con código `SLOT_CAPACITY_EXCEEDED`.
2. Muestra un aviso visible: *"Tu reserva de 15 minutos expiró y el horario seleccionado se completó. Tus platos siguen guardados en el carrito. Por favor, elegí un nuevo horario para continuar."*
3. Abre automáticamente el selector de turnos disponibles de la Spec 012 (`DinerTimeSlotsSelector`).
4. Al seleccionar un nuevo horario válido, el comensal presiona nuevamente "Pagar", generando un nuevo intento limpio (CB-09) sin bloqueos remanentes.

