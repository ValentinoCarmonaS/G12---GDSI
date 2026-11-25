# Contrato 02 — Franjas horarias de retiro en Checkout

**Trazabilidad**: US2, FR-003, FR-005, FR-009, FR-010, SC-002, SC-003.

---

## 1. Catálogo diario de franjas horarias de retiro

- **Method**: `GET`
- **Path**: `/api/v1/establishments/{establishmentId}/pickup-slots`
- **Autenticación y roles**: `hasRole('COMENSAL')` (validado vía `UserSessionCache`).
- **Headers relevantes**:
  - Request: `Authorization: Bearer <jwt>`, `Accept-Encoding: gzip`
  - Response: `Content-Type: application/json`, `Content-Encoding: gzip` (si cuerpo ≥ `1024 bytes`)
- **Request schema**:
  - Path param: `establishmentId` (`UUID`, obligatorio).
  - Query param: `date` (`YYYY-MM-DD`, opcional; por defecto fecha actual en la zona horaria del establecimiento).
- **Response schema (`200 OK` — `PickupSlotCatalogResponse`)**:
  ```json
  {
    "establishmentId": "uuid",
    "date": "2026-11-24",
    "serverTime": "2026-11-24T16:45:00-03:00",
    "timezone": "America/Argentina/Buenos_Aires",
    "isEstablishmentOpen": true,
    "isOperatingDay": true,
    "leadTimeMinutes": 30,
    "slots": [
      {
        "startTime": "17:15:00",
        "endTime": "17:30:00",
        "date": "2026-11-24",
        "maxCapacity": 10,
        "bookedCount": 8,
        "occupancyPercentage": 80.0,
        "status": "AVAILABLE | HIGH_DEMAND",
        "isSelectable": true
      }
    ]
  }
  ```
- **Status codes**:
  - `200 OK`: catálogo diario resuelto con exactamente **1 consulta agrupada** (`GROUP BY o.timeSlotStart`) para todas las franjas de 15 minutos, horarios desde `OperatingScheduleCache` y lectura del establecimiento.
  - `400 Bad Request` (`application/problem+json`): fecha anterior a `today`, fecha posterior a `today + 7` o establecimiento inexistente.
  - `401 Unauthorized` / `403 Forbidden` (`application/problem+json`): fallo de sesión o permisos.
- **Notas de preservación FR-010**:
  - Estructura JSON, formatos de fecha/hora, orden ascendente de `slots` y reglas de negocio idénticas a Spec 012/015.
  - Se siguen excluyendo de `slots` las franjas no seleccionables (`EXPIRED_OR_LEAD_TIME` y `SOLD_OUT`, regla C-12).
  - El conteo agrupado incluye exactamente los mismos estados computables (`CONFIRMED`, `PAID`, `IN_PROGRESS`, `PENDING_ACCREDITATION`, `READY_FOR_PICKUP`, y `PENDING_PAYMENT` con `expiresAt IS NULL OR expiresAt > :now`), siempre leído en tiempo real sin caché temporal en servidor (SC-005).
  - El cálculo de ocupación y disponibilidad permanece exclusivamente en el backend como fuente de autoridad.
