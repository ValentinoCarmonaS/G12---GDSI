# Guía de validación rápida (Quickstart): seguimiento en vivo y demora granular (+5)

**Identificador de especificación**: `021-live-tracking-delay-adjustment`  
**Rama de trabajo**: `021-live-tracking-delay-adjustment`  
**Fecha de elaboración**: 15/11/2026  
**Estado**: Completo  

---

## 1. Propósito y alcance de la validación

Esta guía define los escenarios reproducibles de verificación integral end-to-end para validar el seguimiento en vivo adaptativo (`COM-8.1.1`) y el ajuste granular de demoras culinarias (`COM-7.3.2 (BIS)`), vinculando la interacción táctil en la pantalla de cocina (`/cocina`) con la reactividad en la pantalla del comensal (`/seguimiento/{orderId}`).

Para consultar los contratos de red y el modelo de datos detallado, referirse a:
- [contracts/live-tracking-and-delay-contract.md](./contracts/live-tracking-and-delay-contract.md)
- [data-model.md](./data-model.md)

---

## 2. Prerrequisitos y configuración del entorno

### 2.1 Backend (Spring Boot & PostgreSQL)

1. Base de datos PostgreSQL activa con migraciones Flyway aplicadas (hasta `V27__kds_specific_delay_minutes.sql`).
2. Backend ejecutándose en puerto `8080`:
   ```bash
   cd easyeat/backend
   ./mvnw spring-boot:run
   ```
3. Usuarios de prueba disponibles:
   - Chef: `chef@test.com` (Rol `CHEF`, asociado a `Establishment 1`)
   - Comensal: `diner@test.com` (Rol `COMENSAL`)

### 2.2 Frontend (React & Vite)

1. Dependencias instaladas y servidor de desarrollo activo en puerto `5173`:
   ```bash
   cd easyeat/frontend
   npm run dev
   ```

---

## 3. Escenarios de validación guiada

### Escenario A — Orden en cola con pedidos previos ($N > 0$)
- **Objetivo**: Verificar que en estado `QUEUED` se muestra la posición FIFO y que el minutero y barra permanecen estrictamente ocultos.
- **Acción**: Comensal confirma un pedido inmediato mientras existen 3 pedidos pendientes previos en la cola de cocina. Ingresa a `/seguimiento/{orderId}`.
- **Resultado esperado**:
  - Encabezado exhibe `#ticketNumber` y resumen de platos.
  - Insignia visible: `"En cola en cocina"`.
  - Leyenda explícita: `"Tenés 3 pedidos por delante"`.
  - El temporizador dinámico de minutos y la barra de progreso **no se renderizan** en ninguna parte de la interfaz.

---

### Escenario B — Orden en cola al tope de la cola ($N = 0$)
- **Objetivo**: Verificar el caso borde de cero pedidos precedentes sin fallback a valores vacíos.
- **Acción**: Comensal confirma un pedido cuando no hay ninguna comanda pendiente previa en la cocina. Consulta `/seguimiento/{orderId}`.
- **Resultado esperado**:
  - Exhibe exactamente la leyenda literal: `"Tenés 0 pedidos por delante"`.
  - Minutero y barra permanecen ocultos.

---

### Escenario C — Transición a preparación activa ("EMPEZAR" en cocina)
- **Objetivo**: Conmutación automática y silenciosa a `IN_PREPARATION` en $\le 8$ segundos.
- **Acción**: 
  1. En `/cocina`, el chef presiona "EMPEZAR" en la comanda de la Columna 1.
  2. Mantener la pantalla `/seguimiento/{orderId}` abierta en otra pestaña o ventana sin recargarla.
- **Resultado esperado**:
  - En un lapso $\le 8$ s (siguiente ciclo silencioso), la pantalla conmuta suavemente:
  - La leyenda `"Tenés N pedidos por delante"` desaparece definitivamente.
  - La insignia conmuta a `"Cocinando / En preparación"` acompañada del icono dinámico culinario.
  - Se activan el minutero dinámico (marcando 20 min restantes al inicio) y la barra de progreso porcentual (marcando 15%).

---

### Escenario D — Control táctil "+5" en cocina y visualización de demora
- **Objetivo**: Verificar incremento acumulativo e insignia en la comanda activa del KDS.
- **Acción**: En `/cocina` (Columna 2), el chef presiona el botón "+5" en el pie de la comanda activa. Luego vuelve a presionarlo una segunda vez.
- **Resultado esperado**:
  - Tras la primera pulsación, aparece la insignia contigua rotulada `+5 min`.
  - Tras la segunda pulsación, la insignia conmuta a `+10 min`.
  - El incremento es inmediato y sin recargar el tablero Kanban.

---

### Escenario E — Reflejo reactivo de la demora en la pantalla del comensal
- **Objetivo**: Actualización fluida de $M \to M + 5$ y recálculo proporcional de barra.
- **Acción**: Con la orden marcando $M = 16$ min restantes en el comensal, el chef presiona "+5" en cocina.
- **Resultado esperado**:
  - En $\le 8$ s, el temporizador del comensal salta silenciosamente a $21 \text{ min}$ ($16 + 5$).
  - La barra porcentual recalcula suavemente su avance respecto al nuevo total ($20 + 5 = 25 \text{ min}$).
  - Sin recarga de página, sin parpadeos visuales y sin reinicio de la animación del icono.

---

### Escenario F — Consolidación determinista de clics rápidos (+15 min exactos)
- **Objetivo**: Comprobar la ausencia de *lost updates* bajo concurrencia rápida.
- **Acción**: El chef pulsa el botón táctil "+5" tres veces en rápida sucesión (dentro de un intervalo de 2 segundos).
- **Resultado esperado**:
  - La insignia de cocina consolida exactamente `+15 min`.
  - En la siguiente sincronización del comensal, el minutero refleja exactamente $M + 15$ minutos adicionales.

---

### Escenario G — Cota inferior (piso en 1 min) y cota superior (techo en 92%)
- **Objetivo**: Verificar estabilidad del minutero si la cocción supera el tiempo previsto sin pulsar "LISTO".
- **Acción**: Dejar transcurrir más de 20 minutos (o simular `now - startedAt > 20 min`) mientras el ticket sigue en `IN_PREPARATION`.
- **Resultado esperado**:
  - El minutero se detiene y estabiliza en `"1 min restante"` (nunca desciende a 0 ni muestra números negativos).
  - La barra de progreso no supera el 92% de llenado visual.

---

### Escenario H — Finalización ("LISTO") y purga integral de métricas
- **Objetivo**: Transición a `READY_FOR_PICKUP` y purga de indicadores de tiempo y demora.
- **Acción**: El chef presiona "LISTO" en la comanda activa de cocina.
- **Resultado esperado**:
  - En $\le 8$ s, la pantalla del comensal conmuta de inmediato al contenedor verde de éxito:
  - Mensaje destacado: `"¡Tu pedido está listo para retirar!"`.
  - Exhibe claramente el código numérico de seguridad de 4 dígitos para recitar en mostrador.
  - El minutero, la barra de progreso y cualquier mención de demora (+5) se purgan por completo.
  - Se conservan el número correlativo `#ticketNumber` y el desglose de platos.

---

### Escenario I — Salto terminal directo (+5 y LISTO en la misma ventana de sondeo)
- **Objetivo**: Comprobar que no hay saltos espurios efímeros si la cocina finaliza casi en el mismo instante.
- **Acción**: El chef presiona "+5" e inmediatamente después presiona "LISTO" antes de que transcurran 5 segundos.
- **Resultado esperado**:
  - El comensal pasa directamente al estado verde `READY_FOR_PICKUP` en el siguiente sondeo, sin mostrar un incremento efímero de minutos.

---

### Escenario J — Resiliencia ante cortes de red e insignia de desconexión
- **Objetivo**: Probar la tolerancia a fallos y la señalización no disruptiva.
- **Acción**:
  1. Desconectar la interfaz de red del cliente por 8 segundos y reconectar.
  2. Desconectar la red por 18 segundos.
- **Resultado esperado**:
  1. En el corte de 8 s (< 15 s): la pantalla retiene inmóvil el último estado y minutero, sin errores ni pantallas en blanco. Al reconectar, sincroniza con normalidad.
  2. En el corte de 18 s (> 15 s): aparece una insignia sutil en cabecera: `"Sin conexión — Reintentando..."`, conservando los datos vigentes en pantalla. Al reconectar, la insignia se retira automáticamente en la primera respuesta exitosa.

---

### Escenario K — Polling sostenido e idempotencia visual
- **Objetivo**: Verificar ausencia de degradación de interfaz o memory leaks.
- **Acción**: Dejar la pantalla de seguimiento abierta durante 5 minutos en el mismo estado operativo.
- **Resultado esperado**:
  - Cero saltos de diseño (*layout shifts*).
  - Cero reinicios espurios de la animación del icono culinario.
  - Cero duplicaciones de texto en la lista de platos.

---

### Escenario L — Frontera con Spec 020 (Mostrador)
- **Objetivo**: Garantizar que `specific_delay_minutes` no altera el cálculo de tiempo en mostrador.
- **Acción**: Orden con `+15 min` de demora pasa a `READY_FOR_PICKUP`. Verificar `/mostrador`.
- **Resultado esperado**:
  - `/mostrador` computa `tiempo_en_mostrador` únicamente desde `ready_at`. No muestra demoras de cocina ni altera su ordenamiento cronológico.

---

### Escenario M — Frontera con Spec 019 (KDS Cocina)
- **Objetivo**: Confirmar que "+5" no distorsiona el cronómetro Count-Up de cocina.
- **Acción**: Observar el cronómetro Count-Up de la Columna 2 de cocina antes y después de presionar "+5".
- **Resultado esperado**:
  - El cronómetro Count-Up sigue marcando los minutos físicos transcurridos desde `started_at` sin alterarse por la adición de "+5".

---

## 4. Matriz de evidencia automatizada y verificación de escenarios (T027)

| Escenario | Nombre | Suite de prueba automatizada | Resultado |
|---|---|---|---|
| Escenario A | Cola con pedidos previos ($N > 0$) | `OrderLiveTrackingControllerIntegrationTest.java` & `LiveTrackingWidget.test.tsx` | PASS (100%) |
| Escenario B | Tope de cola ($N = 0$) | `KdsQueueFifoPositionIntegrationTest.java` & `LiveTrackingWidget.test.tsx` | PASS (100%) |
| Escenario C | Transición a preparación ("EMPEZAR") | `KitchenKanbanBoard.test.tsx` & `OrderLiveTrackingControllerIntegrationTest.java` | PASS (100%) |
| Escenario D | Control táctil "+5" e insignia acumulada | `KitchenTicketDelayControl.test.tsx` | PASS (100%) |
| Escenario E | Reflejo reactivo $M \to M+5$ en comensal | `LiveTrackingWidgetDelay.test.tsx` & `LiveTrackingDelayCalculationTest.java` | PASS (100%) |
| Escenario F | Clics rápidos (+15 min) y bloqueo pesimista | `KdsDelayAdjustmentIntegrationTest.java` & `KitchenTicketDelayControl.test.tsx` | PASS (100%) |
| Escenario G | Piso en 1 min y techo en 92% | `LiveTrackingDelayCalculationTest.java` & `LiveTrackingWidgetDelay.test.tsx` | PASS (100%) |
| Escenario H | Finalización ("LISTO") y purga métricas | `LiveTrackingWidget.test.tsx` & `LiveTrackingWidgetDelay.test.tsx` | PASS (100%) |
| Escenario I | Salto terminal directo (+5 y LISTO) | `OrderLiveTrackingService.java` & `LiveTrackingWidgetDelay.test.tsx` | PASS (100%) |
| Escenario J | Cortes de red e insignia de desconexión >15 s | `useOrderLiveTrackingPolling.test.ts` & `LiveTrackingWidget.test.tsx` | PASS (100%) |
| Escenario K | Polling sostenido e idempotencia visual | `useOrderLiveTrackingPolling.test.ts` | PASS (100%) |
| Escenario L | Frontera técnica Spec 020 (Mostrador) | `KdsBoundariesTest.java` (`readyAt` inmutable) | PASS (100%) |
| Escenario M | Frontera técnica Spec 019 (KDS Cocina) | `KdsBoundariesTest.java` (`startedAt` inmutable) | PASS (100%) |

