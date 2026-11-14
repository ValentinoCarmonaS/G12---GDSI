# Contract 04: Rechazo por Insumos Agotados por Concurrencia (AS-2)

**Endpoint**: `POST /api/v1/checkout/pay` (o `POST /api/v1/checkout/confirm-zero-amount`)  
**Consumidor**: Comensal solitario al presionar el botón "Pagar" / "Confirmar pedido"  
**Seguridad**: Autenticación Bearer JWT (`ROLE_COMENSAL`)  
**Aislamiento Financiero**: Cero cobro bancario, cero datos de tarjeta (INV-05, CB-02, AS-2)  

---

## 1. Descripción

Este contrato gobierna el rechazo inmediato del inicio de pago cuando los insumos de uno o más platos del carrito se agotan debido a una transacción concurrente completada instantes antes, antes de que el comensal pudiera consolidar el bloqueo de su reserva temporal de 15 minutos (AS-2, CB-02, INV-08).  
Reglas operativas obligatorias:
1. **Inocuidad Monetaria y de Stock (INV-05)**: No se debita dinero, no se cobra al comensal y no se descuenta ningún ingrediente del inventario.
2. **Preservación del Carrito (CB-02)**: El carrito se mantiene intacto en la sesión del comensal.
3. **Señalización Visual**: Se informa detalladamente qué platos no pueden prepararse por falta de materias primas para que el comensal modifique su carrito.

---

## 2. Petición de Inicio de Pago

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

## 3. Respuesta de Rechazo por Falta de Insumos (`HTTP 409 Conflict`)

Formato estandarizado según RFC 7807 (`ProblemDetail` / `application/problem+json`):

```json
{
  "type": "https://easyeat.com/errors/insufficient-stock",
  "title": "Insumos agotados por concurrencia",
  "status": 409,
  "detail": "No es posible iniciar el pago: los insumos de uno o más platos se agotaron por alta demanda concurrente antes de consolidar la reserva.",
  "code": "INSUFFICIENT_STOCK",
  "instance": "/api/v1/checkout/pay",
  "timestamp": "2026-11-09T13:47:00Z",
  "unavailableDishes": [
    {
      "dishId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "dishName": "Hamburguesa Clásica",
      "requestedQuantity": 2,
      "limitingIngredient": "Pan de hamburguesa",
      "availableStock": 0
    }
  ],
  "cartPreserved": true,
  "actionRequired": "MODIFY_CART"
}
```

### Campos de Salida

| Campo | Tipo | Descripción |
|---|---|---|
| `status` | Integer | Código HTTP: `409`. |
| `code` | String | Código de error de dominio: `INSUFFICIENT_STOCK`. |
| `detail` | String | Mensaje explicativo para presentar al usuario en el banner de error. |
| `unavailableDishes` | Array | Lista de platos del carrito que carecen de ingredientes suficientes para su elaboración. |
| `unavailableDishes[].dishId` | UUID | Identificador del plato no disponible. |
| `unavailableDishes[].dishName` | String | Nombre legible del plato no disponible. |
| `unavailableDishes[].limitingIngredient` | String | Insumo limitante agotado. |
| `cartPreserved` | Boolean | Rigurosamente `true` (el carrito no se vacía). |
| `actionRequired` | String | Directiva para la UI: `MODIFY_CART`. |

---

## 4. Comportamiento en Frontend

1. El frontend intercepta el error `409` con código `INSUFFICIENT_STOCK`.
2. Muestra un banner de alerta destacado en la cabecera del checkout: *"Algunos platos de tu pedido ya no están disponibles por falta de ingredientes. Por favor, modificá tu carrito para continuar."*
3. Marca visualmente los platos afectados dentro del resumen del pedido.
4. Mantiene el botón de pago deshabilitado hasta que el usuario elimine o reduzca la cantidad de los ítems agotados.

