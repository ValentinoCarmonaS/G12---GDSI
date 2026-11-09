# Contrato 04: Panel Informativo "Pago en Curso" y Acciones de Control

**Ruta para Cancelar Intento**: `POST /api/v1/checkout/cancel-payment`  
**Autenticación**: Obligatoria comensal (`ROLE_COMENSAL` vía header `Authorization: Bearer <JWT>`).  
**Propósito**: Regular el comportamiento del comensal cuando reingresa a la pantalla de checkout existiendo un pago en curso en Mercado Pago (AS-5, FR-010, CB-05, Q3). Desplegar el panel informativo sin cuenta regresiva, mantener el botón "Pagar" bloqueado e instrumentar las acciones "Retomar pago" y "Cancelar intento".  
**Garantía de Aislamiento**: No recibe ni expone ningún dato de tarjeta bancaria (FR-013, INV-11, SC-009).

---

## 1. Comportamiento Visual del Checkout en Estado "Pago en Curso" (Q3)

Cuando el comensal reingresa o recarga la pantalla `/checkout` y el backend reporta una orden con `paymentStatus == 'IN_PROCESS'` y reserva temporal `status == 'ACTIVE'`:

1. **Botón "Pagar"**:
   - Estrictamente **deshabilitado** (`disabled={true}`).
   - Estilo visual atenuado e inaccesible con tooltip explicativo: *"Hay un pago en curso en Mercado Pago. No se admiten reservas paralelas."*
   - Neutraliza cobros duplicados y reservas duplicadas (INV-05, SC-007).
2. **Panel Informativo "Pago en Curso"**:
   - Se renderiza en un contenedor destacado en color azul/índigo con ícono de proceso.
   - **Sin cuenta regresiva visible en pantalla** (Q3), para evitar desincronizaciones de reloj entre cliente y servidor.
   - Texto informativo:
     > **Pago en curso en Mercado Pago**  
     > Tenés una transacción iniciada para esta orden. Podés retomarla para completarla en la pasarela o cancelar el intento para liberar tus recursos y volver al checkout.
   - Dispone de dos botones de acción directa:
     - **"Retomar pago"** (botón secundario/outline).
     - **"Cancelar intento"** (botón de acción destructiva controlada / texto rojo).

---

## 2. Acción 1: "Retomar Pago" (Frontend)

- **Comportamiento**: No ejecuta llamadas mutativas en el backend ni crea una nueva reserva.
- **Acción**:
  1. Extrae la URL oficial de redirección provista por el backend (`redirectUrl`, correspondiente a `sandbox_init_point` o `init_point`).
  2. Redirige al comensal o reabre la pasarela de Mercado Pago:
     ```javascript
     window.location.href = redirectUrl;
     ```
  3. El comensal continúa el proceso de pago en la pasarela con el mismo identificador de preferencia y la misma reserva temporal.

---

## 3. Acción 2: "Cancelar Intento" (Backend API)

Permite al comensal desistir voluntariamente del intento de pago en curso (Q3, FR-010).

### Request
```http
POST /api/v1/checkout/cancel-payment HTTP/1.1
Host: api.easyeat.com
Authorization: Bearer <JWT>
Content-Type: application/json
```
```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

### Efectos en Backend
1. Valida que el comensal autenticado sea el titular de la orden.
2. Verifica que la orden tenga una reserva en estado `ACTIVE` y un pago en `IN_PROCESS`.
3. Actualiza `TemporaryReservation`:
   - `status = 'RELEASED'`.
   - `release_reason = 'CANCELLED_BY_USER'`.
   - `released_at = NOW()`.
4. Actualiza `Payment`:
   - `status = 'CANCELLED'`.
5. **Restitución inmediata de recursos (INV-03)**:
   - El cupo de la franja horaria y el stock de los platos en cocina quedan liberados y disponibles para otros comensales en `< 100 ms`.
6. La orden permanece en `PENDING_PAYMENT` para permitir un reintento futuro.

### Response (200 OK)
```http
HTTP/1.1 200 OK
Content-Type: application/json
```
```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "reservationStatus": "RELEASED",
  "paymentStatus": "CANCELLED",
  "message": "Intento de pago cancelado exitosamente. La reserva temporal fue liberada y el checkout ha sido restablecido."
}
```

### Efectos en Frontend tras Cancelar
- Desaparece el panel "Pago en curso".
- Se re-habilita el botón "Pagar".
- El carrito de compras y la franja horaria permanecen seleccionados de forma intacta.
- El comensal puede iniciar un nuevo intento de pago cuando lo desee previa revalidación automática de disponibilidad (FR-008, FR-010).

