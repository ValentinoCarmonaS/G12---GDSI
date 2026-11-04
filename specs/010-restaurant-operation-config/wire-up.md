# Wire-up Guide: Restaurant Operation Configuration

**Feature**: `010-restaurant-operation-config` | **Date**: 2026-11-02 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

This is the only integration artifact of the feature. The repository has no orders or checkout module (assumptions A-002, A-003 and A-004 describe them as pre-existing, they are not in this codebase), so this iteration ships the rules, endpoints and UI components and **no orders module, stub, port or adapter**. This document says exactly where the future order flow must call them.

## 1. What this feature provides

| Piece | Where | Used by the order flow for |
|---|---|---|
| `DinerTimeSlotService.availabilityFor(establishmentId, date)` | `com.easyeat.operations.service` | The slots of a date that can still be chosen, with `AVAILABLE` / `SOLD_OUT` (INV-1, INV-3, EC-007) |
| `DinerTimeSlotService.isAdmissible(establishmentId, TimeSlot)` | same | Whether a new order may enter for a slot right now: schedule and expiry only (INV-1) |
| `SlotCapacityService.consumeSlot(establishmentId, TimeSlot)` | same | Counting an approved order against the slot, atomically (INV-2, EC-016) |
| `SlotCapacityService.isSoldOut(establishmentId, TimeSlot)` | same | Read-only projection of INV-3 |
| `PaymentMethodService.getEnabledMethods(establishmentId)` | same | The methods the checkout may show (INV-4) |
| `PaymentMethodService.validateEnabled(establishmentId, PaymentMethod)` | same | Whether the chosen method is enabled at this instant (INV-9) |
| `GET /api/v1/establishments/{tenantId}/time-slots?date=` | REST | Slot selector data |
| `GET /api/v1/establishments/{tenantId}/payment-methods` | REST | Checkout options |
| `POST /api/v1/establishments/{tenantId}/checkout/validate-payment` | REST | Revalidation of the chosen method on submit |
| `DinerSlotSelector` | `easyeat/frontend/src/components/operations` | Slot picker, props-driven |
| `CheckoutPaymentOptions` | same | Payment step, props-driven |

All time decisions use the server `Clock` read in the establishment timezone (`EstablishmentTimeProvider`). No call accepts a time from the client (D-001, EC-018).

## 2. Order creation: is the slot admissible

**Call site**: the service that creates an order for a scheduled pickup or delivery slot, before persisting it.

1. Build the `TimeSlot` from the requested date and start time.
2. Call `DinerTimeSlotService.isAdmissible`. If it is false, reject the order: the day is closed, the slot already ended, or it is outside `[opening, closing)` (INV-1, EC-001, EC-017).
3. Do not consume capacity here. An order that is only created or pending does not count against the slot (INV-2 counts approved orders).

The same rule applies to pickup and delivery; the order's fulfillment type is not an input (INV-10).

## 3. Order approval: counting the slot

**Call site**: inside the transaction that moves an order to the approved state (A-002).

1. Call `SlotCapacityService.consumeSlot` with the order's slot **in the same transaction** as the state change. It is one conditional `UPDATE`, so no read-then-write window exists.
2. If it throws `SlotSoldOutException`, the transaction must roll back and the order must stay un-approved. The exception carries the capacity in its message and maps to HTTP 409 "Franja horaria agotada" (section 6).
3. Do not call `isAdmissible` on approval. An order admitted at 15:58 stays valid when approved at 16:02 (EC-002, US1-AC4).
4. Never decrement the counter and never touch approved orders when the schedule or a capacity changes (INV-8). The counter only grows through `consumeSlot`. Cancelling an approved order is outside this feature; if a later feature needs to release a place it must add an equally atomic release statement to `SlotCapacityRepository`.

## 4. Slot selector: revalidation on submit

**Call site**: the diner screen that hosts `DinerSlotSelector`.

1. Load the slots with `operationsApi.getDinerTimeSlots` and pass `slots`, `isClosed` and `closureReason`.
2. The `revalidate(startTime)` prop must read the time slots again and reject when the chosen slot is no longer `AVAILABLE` (or is no longer listed). The rejection must be an error whose `response.status` is 409; the selector turns that into its message and clears the selection (EC-013).
3. After `revalidate` resolves, `onConfirm(startTime)` creates the order (section 2). The authoritative check is still on the server: creating the order calls `isAdmissible`, and approving it calls `consumeSlot`, so a stale screen can never oversell a slot.

## 5. Checkout: payment methods

**Call site**: the diner checkout screen that hosts `CheckoutPaymentOptions`.

1. Load the methods with `operationsApi.getDinerPaymentMethods` and pass them as `methods`. The server already leaves disabled methods out (INV-4). An empty list shows the blocking message and keeps the confirm button disabled (EC-009).
2. Pass `onRefresh` so the screen reloads the methods; a method enabled meanwhile appears after that (EC-011).
3. The `revalidate(method)` prop must call `operationsApi.validatePaymentMethod`. A 409 means the method was disabled while the diner was on screen: the component shows the message, drops the selection and asks for a refresh (EC-010).
4. After `revalidate` resolves, `onConfirm(method)` submits the order. The order endpoint must itself call `PaymentMethodService.validateEnabled` before charging, because the screen check can be bypassed (INV-9, FR-012).
5. No gateway is contacted by this feature: the flag is the whole configuration (A-005).

## 6. Error handling expectations

| Situation | Exception | HTTP | `title` | Client behaviour |
|---|---|---|---|---|
| Slot reached its capacity, also when the last place was lost to a concurrent approval | `SlotSoldOutException` | 409 | Franja horaria agotada | Reject the approval, keep the order un-approved, tell the admin or diner to choose another slot |
| Payment method disabled before confirming | `PaymentMethodUnavailableException` | 409 | Método de pago no disponible | Block the order, refresh the options, ask for another method |
| Configuration edited by another admin | `OperatingConfigConflictException` | 409 | Conflicto de concurrencia | Admin screens show the reload message |
| Opening not before closing | `InvalidScheduleException` | 400 | Parámetros de horario inválidos | Admin form message |
| Negative or misaligned capacity | `InvalidCapacityException` | 400 | Capacidad inválida | Admin grid message |
| Unknown establishment on the diner endpoints | `EstablishmentNotFoundException` | 404 | Establishment Not Found | Back to the establishment list |

The three 409 problems above come from `GlobalExceptionHandler` and need no extra mapping when the order flow throws these exceptions from its own controllers.

## 7. Explicitly not part of this feature

- No orders, cart, checkout page or approval workflow, and no stub or fake of them.
- No payment gateway SDK, credential, token or bank data.
- No release of a slot place on cancellation (see section 3, step 4).
- No push or real-time update of an open selector: staleness is handled by revalidating on submit.
