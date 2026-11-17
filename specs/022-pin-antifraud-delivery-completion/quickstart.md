# Guía de validación rápida (Quickstart): protocolo antifraude de entrega y cierre de orden

**Especificación**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Modelo de datos**: [data-model.md](./data-model.md)  
**Contratos**: [01-complete-delivery](./contracts/01-complete-delivery.md) | [02-counter-orders-polling](./contracts/02-counter-orders-polling.md) | [03-order-live-tracking](./contracts/03-order-live-tracking.md)  
**Fecha**: 15/11/2026 | **Estado**: Consolidado  

---

## 1. Prerrequisitos del entorno de pruebas

1. Backend Spring Boot ejecutándose en puerto `8080` (con perfiles `local` o `test`):
   ```bash
   cd easyeat/backend
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```
2. Frontend React ejecutándose en puerto `5173`:
   ```bash
   cd easyeat/frontend
   npm run dev
   ```
3. Base de datos PostgreSQL con migraciones Flyway aplicadas hasta `V28` inclusive (`V28__add_completed_at_to_orders.sql`).
4. Usuarios de prueba disponibles:
   - `comensal@easyeat.com` (Rol `COMENSAL`)
   - `encargado@easyeat.com` (Rol `ENCARGADO`)
   - `chef@easyeat.com` (Rol `CHEF`)

---

## 2. Escenarios de validación operativa end-to-end

### Escenario A — Generación y formato del PIN al confirmar el pedido (`ENC-3.2.1` / `AC-1`)
1. Iniciar sesión como `comensal@easyeat.com`.
2. Realizar una orden en `/catalogo` y completar el checkout de pago en `/checkout`.
3. Navegar a la pantalla de confirmación y seguimiento `/seguimiento/{orderId}`.
4. **Verificación en BD**:
   ```sql
   SELECT id, status, security_code, completed_at FROM orders WHERE id = '{orderId}';
   ```
   - `status` debe ser `PAID` o `IN_PROGRESS`.
   - `security_code` debe ser un entero en el rango cerrado $[1000, 9999]$ (sin ceros a la izquierda).
   - `completed_at` debe ser `NULL`.
5. **Verificación visual**: En la pantalla de seguimiento, si la orden transiciona a lista, el PIN se observa formateado con espaciado amplio y tipografía monospace (`d d d d`).

---

### Escenario B — Exhibición de PIN y botón de entrega en tarjeta de mostrador (`ENC-3.2.1` / `AC-2`)
1. Con la orden en estado `READY_FOR_PICKUP` (tras finalizar cocción en cocina), iniciar sesión como `encargado@easyeat.com` y acceder a `/mostrador`.
2. Localizar la tarjeta correspondiente al pedido `#ticketNumber`.
3. Hacer clic sobre la tarjeta para desplegarla.
4. **Verificación visual**:
   - Se exhibe de forma prominente el código de seguridad con espaciado amplio (ejemplo: `4 8 2 1`) utilizando `<SecurityPinDisplay />`.
   - Se observa el botón "ENTREGAR PEDIDO" habilitado y claramente legible.
   - El comensal recita oralmente su código y el encargado constata la coincidencia visual directa sin digitar texto.

---

### Escenario C — Acción directa, inhabilitación inmediata y distintivo verde (`ENC-3.3.1` / `AC-1`, `AC-2`)
1. En la tarjeta desplegada de mostrador, hacer un único clic sobre "ENTREGAR PEDIDO".
2. **Verificación instantánea (< 100 ms)**:
   - El botón pasa inmediatamente a estado `disabled` impidiendo cualquier clic posterior.
   - La tarjeta muestra de forma inmediata el distintivo en color verde con la leyenda "Entrega Confirmada".
   - No se despliega ningún modal bloqueante de confirmación emergente.
   - En la pestaña Network de DevTools se observa el despacho de `POST /api/v1/counter/orders/{orderId}/complete-delivery`.

---

### Escenario D — Archivando atómico y remoción limpia de tarjeta (`ENC-3.3.2` / `AC-1`, `AC-2`)
1. Mantener tres tarjetas abiertas simultáneamente en `/mostrador`.
2. Presionar "ENTREGAR PEDIDO" en una de ellas.
3. Al recibir la respuesta HTTP `200 OK`:
   - La tarjeta de la orden entregada desaparece automáticamente de la vista activa de despacho.
   - Las otras dos tarjetas abiertas **preservan su estado desplegado**, sus posiciones relativas y sus contadores sin parpadeos ni colapsos.
4. **Verificación en BD**:
   ```sql
   SELECT o.status AS order_status, o.completed_at AS order_completed,
          k.status AS kds_status, k.completed_at AS kds_completed
   FROM orders o
   JOIN kds_tickets k ON k.order_id = o.id
   WHERE o.id = '{orderId}';
   ```
   - `order_status` debe ser `'COMPLETED'`.
   - `kds_status` debe ser `'COMPLETED'`.
   - `order_completed` y `kds_completed` deben registrar exactamente el mismo timestamp no nulo (`Instant.now()`).

---

### Escenario E — Protección síncrona anti doble clic (`ENC-3.3.1` / `RF-008`, `Invariante 10`)
1. En una tarjeta desplegada de mostrador, simular pulsaciones múltiples ultra-rápidas sobre "ENTREGAR PEDIDO" (doble clic o triple clic en menos de 50 ms).
2. **Verificación en Network**:
   - Se despacha **una única** petición `POST complete-delivery`.
   - Las pulsaciones secundarias son completamente neutralizadas por el estado `isSubmitting` del componente.

---

### Escenario F — Idempotencia estricta ante reintentos de red (`ENC-3.3.2` / `AC-2`, `Invariante 9`)
1. Obtener una orden ya completada (`COMPLETED`).
2. Disparar manualmente una segunda petición:
   ```bash
   curl -X POST http://localhost:8080/api/v1/counter/orders/{orderId}/complete-delivery \
        -H "Authorization: Bearer <TOKEN_ENCARGADO>"
   ```
3. **Verificación**:
   - Respuesta HTTP `200 OK` con mensaje idempotente.
   - El timestamp `completed_at` retornado es idéntico al original.
   - En base de datos no se altera ninguna columna.

---

### Escenario G — Conclusión definitiva y cese de polling en seguimiento (`ENC-3.3.2` / `AC-3`, `RF-016`, `RF-017`)
1. Con la pantalla de seguimiento del comensal `/seguimiento/{orderId}` abierta en el navegador:
2. El encargado concreta la entrega en mostrador.
3. En el siguiente ciclo de sondeo del comensal (a los pocos segundos):
   - La interfaz conmuta de inmediato a la vista terminal permanente: `"Pedido Entregado — ¡Que disfrutes tu comida!"`.
   - El código de seguridad de 4 dígitos **desaparece por completo** de la pantalla.
   - Se exhibe el resumen íntegro de la compra (`items`, cantidades, importes).
   - **Verificación en Network**: En DevTools se constata que **cesan absolutamente todos los llamados** a `/live-tracking`. No se vuelve a disparar ningún sondeo periódico.

---

### Escenario H — Resiliencia ante error de comunicación (`RF-018`, `Invariante 11`)
1. En la tarjeta desplegada de mostrador, desconectar la red (o simular modo Offline en DevTools) y pulsar "ENTREGAR PEDIDO".
2. **Verificación**:
   - Al fallar la llamada de red, la tarjeta exhibe un aviso flotante no intrusivo de error de comunicación.
   - El distintivo verde se retira y el botón "ENTREGAR PEDIDO" vuelve a habilitarse (`disabled = false`).
   - La tarjeta permanece visible en la grilla para permitir un reintento manual inmediato al reconectar.

---

### Escenario I — Tolerancia a orden sin PIN (Caso Borde 10)
1. Insertar o preparar en BD una orden en estado `READY_FOR_PICKUP` con `security_code = NULL`.
2. Acceder a `/mostrador` y desplegar su tarjeta.
3. **Verificación**:
   - El contenedor del código exhibe el marcador neutral `- - - -`.
   - La tarjeta se renderiza con total normalidad y el botón "ENTREGAR PEDIDO" permanece habilitado.
   - Al pulsarlo, la orden se completa y archiva exitosamente en el servidor.

---

### Escenario J — Exclusión determinista en polling de mostrador (`RF-005`, `RF-014`)
1. Completar la entrega de un pedido.
2. Observar el tráfico periódico de `/api/v1/counter/orders`:
3. En la siguiente respuesta del backend (a los 3 a 4 segundos), el array devuelto ya no incluye la orden completada, ratificando la consistencia entre la remoción optimista del cliente y el filtrado del servidor.

---

### Escenario K — Aislamiento con KDS de cocina (Spec 019)
1. Completar la entrega de un pedido en mostrador.
2. Acceder a la pantalla de KDS de cocina `/cocina` (o consultar `GET /api/v1/kitchen/orders`):
3. La comanda con estado `COMPLETED` no aparece en ninguna de las columnas de trabajo de la cocina.

---

### Escenario L — Concurrencia entre terminales múltiples
1. Abrir dos navegadores simultáneos en `/mostrador` con la misma orden desplegada.
2. Pulsar "ENTREGAR PEDIDO" en ambos casi simultáneamente.
3. **Verificación**:
   - Gracias al bloqueo `PESSIMISTIC_WRITE` (`findByIdForUpdate`), el primer hilo transiciona la orden y el segundo hilo responde con el `200 OK` idempotente.
   - En base de datos se registra un único `completed_at` sin colisiones ni errores 500.
   - Ambas pantallas remueven la tarjeta limpiamente.
