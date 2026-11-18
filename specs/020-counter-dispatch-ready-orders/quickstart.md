# Guía de validación rápida (Quickstart): despacho a mostrador y sincronización de pedidos listos

**Especificación**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Modelo de datos**: [data-model.md](./data-model.md) | **Contratos**: [contracts/counter-dispatch-contract.md](./contracts/counter-dispatch-contract.md)  
**Fecha de elaboración**: 15/11/2026 | **Rama**: `020-counter-dispatch-ready-orders`  

---

## 1. Propósito y alcance de la guía

Esta guía establece el procedimiento determinista y reproducible para validar de extremo a extremo (E2E) la funcionalidad de despacho en cocina, la sincronización periódica reactiva y el tablero de mostrador con tarjetas plegables, asegurando el cumplimiento íntegro de las historias `CHF-4.1.3`, `CHF-4.2.3`, `ENC-2.1.1` y `ENC-2.1.2`.

---

## 2. Requisitos previos y configuración del entorno

### 2.1 Backend (Spring Boot & PostgreSQL)

1. Base de datos con migración `V26__counter_dispatch_and_security_code.sql` aplicada.
2. Iniciar el backend:
   ```bash
   cd easyeat/backend
   ./mvnw spring-boot:run
   ```
3. Usuarios de prueba con roles autenticados:
   - **Chef**: `chef@easyeat.com` (rol `CHEF`, sucursal asignada en `tenantId`).
   - **Encargado**: `encargado@easyeat.com` (rol `ENCARGADO`, misma sucursal).
   - **Comensal con foto**: `comensal1@easyeat.com` (con avatar cargado en `user_photos`).
   - **Comensal sin foto**: `comensal2@easyeat.com` (sin avatar cargado).

### 2.2 Frontend (React SPA)

1. Iniciar la aplicación de frontend:
   ```bash
   cd easyeat/frontend
   npm run dev
   ```
2. Abrir dos ventanas de navegador independientes:
   - **Ventana A (Cocina)**: sesión iniciada como Chef en `/cocina`.
   - **Ventana B (Mostrador)**: sesión iniciada como Encargado en `/mostrador`.

---

## 3. Escenarios de validación operativa

### Escenario 1: Finalización atómica en cocina y remoción inmediata (`CHF-4.1.3` — `RF-001`, `RF-002`, `RF-003`, `RF-004`)

- **Objetivo**: Verificar que presionar "LISTO" en la Columna 2 de cocina remueve inmediatamente la comanda y actualiza atómicamente `KdsTicket` y `Order` con timestamps coincidentes.
- **Pasos**:
  1. En la Ventana A (`/cocina`), ubicar un ticket en estado de preparación activa en la Columna 2 (por ejemplo, Ticket #104).
  2. Presionar el botón "LISTO" ubicado en la tarjeta.
- **Resultado esperado**:
  - La tarjeta desaparece de inmediato de la Columna 2 sin confirmaciones emergentes ni checklist manual.
  - En base de datos, `kds_tickets.status = 'READY_FOR_PICKUP'` y `orders.status = 'READY_FOR_PICKUP'`.
  - Los campos `kds_tickets.ready_at` y `orders.ready_at` registran el mismo instante exacto (coincidencia temporal inmutable).

---

### Escenario 2: Sincronización automática en mostrador y estado inicial cerrado (`CHF-4.2.3`, `ENC-2.1.1` — `RF-005`, `RF-006`, `RF-008`, `RF-014`)

- **Objetivo**: Verificar que el pedido listo arriba a la pantalla de mostrador sin recargas manuales, mostrando tarjeta compacta cerrada y contador en `0 min`.
- **Pasos**:
  1. Mantener visible la Ventana B (`/mostrador`) sin interactuar con el teclado ni el ratón tras completar el Escenario 1.
  2. Observar la grilla de pedidos listos.
- **Resultado esperado**:
  - En un lapso menor a 5 segundos (ciclo de polling de 4 s), el Ticket #104 aparece automáticamente en la grilla.
  - La tarjeta se renderiza cerrada por defecto, exhibiendo:
    - Número correlativo destacado: `#104`.
    - Franja horaria pactada en rango completo: `13:00 - 13:15`.
    - Contador de tiempo de espera inicial: `0 min`.

---

### Escenario 3: Despliegue interactivo, datos operativos y omisión de precios (`ENC-2.1.1`, `ENC-2.1.2` — `RF-009`, `RF-012`, `RF-013`)

- **Objetivo**: Verificar la expansión de la tarjeta revelando datos de cotejo oral/visual, desglose de platos `[N]x [Plato]` y ausencia estricta de importes monetarios.
- **Pasos**:
  1. En la Ventana B (`/mostrador`), hacer clic sobre la cabecera o cualquier sector de la tarjeta del Ticket #104.
- **Resultado esperado**:
  - La tarjeta se despliega de inmediato exponiendo:
    - Código numérico de seguridad de 4 dígitos (por ejemplo, `4821`).
    - Nombre completo del comensal titular (`Valentino Carmona`).
    - Fotografía de perfil del comensal provista por el endpoint de avatares.
    - Desglose íntegro de platos bajo el formato `[Cantidad]x [Plato]` (por ejemplo: `2x Hamburguesa Completa con Papas`, `1x Limonada con Menta y Jengibre`).
    - Contenedor visual del botón "ENTREGAR PEDIDO".
  - **Agnosticismo monetario**: No se visualiza ningún precio unitario, subtotal ni importe total.

---

### Escenario 4: Omisión limpia ante comensal sin fotografía (`ENC-2.1.1` — `RF-012`, caso borde 3)

- **Objetivo**: Comprobar que comensales sin foto no generan placeholders rotos ni iconos genéricos.
- **Pasos**:
  1. Finalizar en cocina una orden correspondiente al usuario `comensal2@easyeat.com` (Ticket #105, sin foto).
  2. En `/mostrador`, hacer clic para desplegar la tarjeta del Ticket #105.
- **Resultado esperado**:
  - El contenedor de imagen se omite limpiamente en el DOM.
  - El nombre `Lucía Morales` se alinea de forma sobria y directa a la izquierda sin iconos vacíos ni imágenes por defecto.

---

### Escenario 5: Alternancia bidireccional (Toggle) y protección contra colapso interior (`ENC-2.1.1` — `RF-011`)

- **Objetivo**: Asegurar que clics sobre elementos interiores no cierren la tarjeta accidentalmente.
- **Pasos**:
  1. Con la tarjeta del Ticket #104 desplegada, hacer clic sobre el listado de platos o sobre el contenedor del botón de entrega.
  2. Luego, hacer clic sobre la cabecera superior de la tarjeta.
- **Resultado esperado**:
  - Al interactuar con el interior, la tarjeta **permanece desplegada** (`stopPropagation`).
  - Al hacer clic en la cabecera, la tarjeta **se colapsa** retornando a su estado compacto cerrado.

---

### Escenario 6: Despliegue simultáneo y preservación de estado ante polling (`ENC-2.1.1` — `RF-010`, `RF-015`, `RF-016`)

- **Objetivo**: Constatar que múltiples tarjetas pueden estar abiertas y que los ciclos de refresco de fondo no las colapsan ni duplican tarjetas.
- **Pasos**:
  1. Desplegar manualmente las tarjetas de los Tickets #104 y #105.
  2. Aguardar 15 segundos observando la pantalla mientras se ejecutan al menos 3 ciclos de polling silencioso.
- **Resultado esperado**:
  - Ambas tarjetas continúan desplegadas simultáneamente.
  - Ninguna tarjeta se cierra involuntariamente.
  - No se producen parpadeos de interfaz ni duplicados visuales de ningún ticket en la grilla.

---

### Escenario 7: Ordenamiento determinista de despacho FIFO (`ENC-2.1.1` — `RF-017`, `Invariante 9`)

- **Objetivo**: Verificar que las tarjetas se ordenan por mayor tiempo de espera (`ready_at ASC`) con desempate por `ticket_number ASC`.
- **Pasos**:
  1. Observar la disposición en grilla de las órdenes en mostrador.
- **Resultado esperado**:
  - El pedido finalizado con mayor antigüedad (mayor tiempo en mostrador) se ubica en primer lugar (superior izquierda).
  - Si dos pedidos tuvieran marcas idénticas, el de menor `ticketNumber` precede al otro.

---

### Escenario 8: Avance del contador Count-Up (`ENC-2.1.1` — `RF-007`, caso borde 5)

- **Objetivo**: Verificar la progresión determinista del tiempo transcurrido en minutos enteros.
- **Pasos**:
  1. Monitorear el contador de una tarjeta recién finalizada desde `0 min`.
  2. Aguardar hasta transcurrir 60 segundos exactos desde su `ready_at`.
- **Resultado esperado**:
  - El contador conmuta con precisión de `0 min` a `1 min`, manteniéndose consistente sin reinicios ante recargas voluntarias de la pestaña.

---

### Escenario 9: Insignia de desconexión y recuperación reactiva (`CHF-4.2.3` — `RF-018`, caso borde 1)

- **Objetivo**: Validar la alerta sutil no bloqueante ante pérdidas prolongadas de red y su auto-recuperación.
- **Pasos**:
  1. En las herramientas de desarrollador de la Ventana B (DevTools > Network), seleccionar la opción "Offline".
  2. Aguardar 16 segundos sin conexión.
  3. Desactivar el modo "Offline" restaurando la conectividad.
- **Resultado esperado**:
  - A los 15 segundos sin respuesta exitosa, aparece en la cabecera la insignia sutil `"Sin conexión — Reintentando..."`.
  - Las tarjetas en pantalla permanecen visibles y legibles; las tarjetas desplegadas conservan su estado abierto.
  - Al restaurar la red, el siguiente ciclo de polling exitoso oculta automáticamente la insignia sin recargar la pantalla.

---

### Escenario 10: Estado vacío informativo (`ENC-2.1.1` — `RF-019`)

- **Objetivo**: Validar la interfaz ante la ausencia de órdenes listas.
- **Pasos**:
  1. Acceder a `/mostrador` en un establecimiento sin comandas en estado `READY_FOR_PICKUP`.
- **Resultado esperado**:
  - Se visualiza el mensaje neutral `"No hay pedidos listos para entrega"`, preservando la estabilidad del diseño sin saltos visuales.

---

### Escenario 11: Idempotencia estricta ante reintentos de red (`CHF-4.1.3` — `RF-002`, caso borde 10)

- **Objetivo**: Comprobar que solicitudes repetidas de finalización no alteran `ready_at` ni duplican tarjetas en mostrador.
- **Pasos**:
  1. Enviar una petición HTTP `POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup` sobre un ticket que ya fue finalizado.
- **Resultado esperado**:
  - El servidor responde `200 OK` devolviendo exactamente la misma marca temporal `readyAt` previa.
  - La orden en mostrador no duplica su tarjeta ni resetea su contador de espera.

---

### Escenario 12: Exclusión de pedidos con fecha futura (`ENC-2.1.1` — `RF-005`)

- **Objetivo**: Asegurar que pedidos programados para días posteriores no aparezcan en la pantalla operativa de hoy.
- **Pasos**:
  1. Crear un pedido con `pickup_date = hoy + 1 día` y forzar su ticket a `READY_FOR_PICKUP`.
  2. Consultar la pantalla de `/mostrador`.
- **Resultado esperado**:
  - El pedido no aparece en la grilla de mostrador de la jornada en curso.

---

## 4. Registro de verificación automatizada de extremo a extremo (T033)

Fecha de ejecución: 16/11/2026.

| Escenario | Cobertura automatizada | Estado |
| --- | --- | --- |
| Escenario 1 (`CHF-4.1.3` — Finalización atómica y remoción inmediata) | `KitchenReadyForPickupIntegrationTest` + `KitchenDashboardPage.test.tsx` (`T008`–`T014`) | PASS |
| Escenario 2 (`CHF-4.2.3`, `ENC-2.1.1` — Sincronización automática y estado cerrado) | `useCounterReadyOrdersPolling.test.ts` + `CounterTicketCard.test.tsx` (`T015`, `T021`) | PASS |
| Escenario 3 (`ENC-2.1.1`, `ENC-2.1.2` — Despliegue, datos operativos y sin precios) | `CounterOrderControllerIntegrationTest` + `CounterTicketCard.test.tsx` (`T026`, `T027`) | PASS |
| Escenario 4 (`ENC-2.1.1` — Omisión limpia sin fotografía / `404`) | `ProfileAvatarStaffControllerTest` + `CounterTicketCard.test.tsx` (`T025`, `T027`) | PASS |
| Escenario 5 (`ENC-2.1.1` — Alternancia bidireccional y `stopPropagation`) | `CounterTicketCard.test.tsx` (`T021`) | PASS |
| Escenario 6 (`ENC-2.1.1` — Despliegue simultáneo y preservación ante polling) | `CounterBoardPage.test.tsx` (`T021`) | PASS |
| Escenario 7 (`ENC-2.1.1` — Ordenamiento FIFO `ready_at ASC, ticket_number ASC`) | `CounterOrderControllerIntegrationTest` (`T019`) + `CounterBoardPage.test.tsx` (`T021`) | PASS |
| Escenario 8 (`ENC-2.1.1` — Contador Count-Up en minutos enteros) | `counterTimeUtils.test.ts` (`T020`) | PASS |
| Escenario 9 (`CHF-4.2.3` — Insignia de desconexión tras 15 s y auto-recuperación) | `useCounterReadyOrdersPolling.test.ts` (`T015`) + `CounterBoardPage.test.tsx` (`T021`) | PASS |
| Escenario 10 (`ENC-2.1.1` — Estado vacío `"No hay pedidos listos para entrega"`) | `CounterBoardPage.test.tsx` (`T021`) | PASS |
| Escenario 11 (`CHF-4.1.3` — Idempotencia estricta ante reintentos) | `KitchenReadyForPickupIntegrationTest` (`T008`, `T009`) | PASS |
| Escenario 12 (`ENC-2.1.1` — Exclusión de pedidos con fecha futura) | `CounterOrderControllerIntegrationTest` (`T019`) | PASS |

