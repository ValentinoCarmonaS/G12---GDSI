# Contrato 02: Retorno al Sitio tras Pasarela (Back URLs)

**Ruta de Frontend**: `/checkout/payment-return`  
**Canal**: Redirección HTTP originada por el navegador del comensal desde el entorno de Mercado Pago.  
**Propósito**: Recibir al comensal tras su interacción con la pasarela, revalidar el estado real contra el backend (INV-08), vaciar el carrito si fue aprobado (Q4) o restaurar el checkout con carrito y franja intactos si fue rechazado o abandonado (Q2).  
**Garantía de Aislamiento**: No recibe ni expone ningún dato de tarjeta bancaria (FR-013, INV-11, SC-009).

---

## 1. Parámetros de Redirección (Query Params)

Mercado Pago retorna al comensal a una de las tres `back_urls` configuradas en la preferencia:

| Escenario | URL de Destino | Parámetros Inyectados por MP |
|---|---|---|
| **Aprobado (AS-2)** | `/checkout/payment-return?status=success&orderId={id}` | `payment_id`, `status=approved`, `external_reference`, `preference_id` |
| **Rechazado (AS-3)** | `/checkout/payment-return?status=failure&orderId={id}` | `payment_id`, `status=rejected`, `external_reference`, `preference_id` |
| **Abandono / Cancelación (AS-4)** | `/checkout/payment-return?status=failure&orderId={id}` | `status=null` o ausencia de `payment_id`, `external_reference` |

Ejemplo de URL completa de retorno:
```text
https://easyeat.com/checkout/payment-return?status=success&orderId=3fa85f64-5717-4562-b3fc-2c963f66afa6&collection_id=123456789&collection_status=approved&external_reference=3fa85f64-5717-4562-b3fc-2c963f66afa6&preference_id=123456789-abc-def
```

---

## 2. Protocolo de Verificación e Invariante de Autoridad Externa (INV-08)

El frontend de EasyEat **NUNCA** da por aprobado un pago basándose exclusivamente en los query params de la URL (para prevenir falsificaciones por manipulación de parámetros en el navegador).

### Secuencia de Verificación
1. El componente `PaymentReturnPage.tsx` intercepta los parámetros y extrae el `orderId`.
2. Realiza inmediatamente una petición autenticada al backend:
   ```http
   GET /api/v1/orders/{orderId}/payment-status
   Authorization: Bearer <JWT>
   ```
3. El backend verifica la base de datos interna y (si está en curso) consulta la API de Mercado Pago (`GET /v1/payments/{id}`).
4. El backend responde con el estado consolidado: `APPROVED`, `REJECTED`, `IN_PROCESS`, `CANCELLED` o `EXPIRED`.

---

## 3. Comportamientos y Transiciones en Frontend

### Caso A: Pago Aprobado (`status == 'APPROVED'`) (AS-2, FR-004)
- **Acción sobre el carrito**: El frontend invoca de forma automática e inmediata `clearCart()` en `CartContext` (Q4).
- **Acción sobre la interfaz**:
  - Despliega pantalla de felicitaciones y confirmación de orden:
    - Número de pedido (`orderId`).
    - Franja horaria acordada para el retiro.
    - Importe total abonado con Mercado Pago.
  - Ofrece acceso directo a "Mis Pedidos" o "Seguimiento".

### Caso B: Pago Rechazado (`status == 'REJECTED'`) (AS-3, FR-005, FR-006, Q2)
- **Acción sobre recursos**: El backend ya liberó la reserva temporal y el cupo en el momento de procesar el rechazo.
- **Acción sobre el carrito y la franja**: El carrito de compras y la franja horaria seleccionada en `CheckoutPage` se **mantienen intactos**.
- **Acción sobre la interfaz**:
  - Redirige al comensal a `/checkout`.
  - Muestra un banner de alerta visible y destacado en ámbar/rojo:
    > "El pago no se concretó (fondos insuficientes o denegado por la entidad). Podés intentar nuevamente o seleccionar otro medio de pago."
  - El botón "Pagar" se encuentra **re-habilitado** para permitir un nuevo intento con un solo clic.

### Caso C: Abandono de Pasarela con Retorno (`status == 'CANCELLED'` o sin pago) (AS-4, FR-007, FR-008, Q2)
- **Acción sobre recursos**: Si la reserva sigue activa, se procede a su liberación inmediata (`RELEASED` por `CANCELLED_BY_USER`).
- **Acción sobre el carrito y la franja**: El carrito y la franja horaria se preservan intactos en la sesión.
- **Acción sobre la interfaz**:
  - Redirige a `/checkout`.
  - Despliega banner informativo:
    > "Operación cancelada. Tu pedido sigue guardado en el checkout para cuando quieras completarlo."
  - Botón "Pagar" re-habilitado para generar una nueva reserva con un solo clic mientras los recursos continúen disponibles.

