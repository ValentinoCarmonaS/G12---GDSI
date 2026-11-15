# Guía rápida de validación: KDS — cola de cocina y preparación activa

**Especificación**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Rama**: `019-kds-queue-preparation` | **Fecha**: 15/11/2026

Esta guía detalla los prerrequisitos, comandos de arranque y el protocolo paso a paso de verificación manual y automatizada para validar de punta a punta la funcionalidad de cola de cocina y preparación activa.

Para consultar los modelos de datos y los contratos HTTP de este incremento, dirigirse a:
- [data-model.md](./data-model.md)
- [contracts/kds-queue-contract.md](./contracts/kds-queue-contract.md)

---

## 1. Prerrequisitos y arranque del entorno local

### 1.1 Base de datos PostgreSQL

```bash
cd easyeat/backend
docker compose up -d postgres
```

- Endpoint: `localhost:5432`, base de datos: `easyeat`, usuario/contraseña: `easyeat`/`easyeat_dev_password`.
- Las migraciones Flyway (hasta `V23`) se aplican automáticamente al iniciar la aplicación.

### 1.2 Backend (Spring Boot 3.3.4, Java 21)

```bash
cd easyeat/backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- Servidor disponible en `http://localhost:8080`.

### 1.3 Frontend (React 18, Vite, TypeScript)

```bash
cd easyeat/frontend
npm install
npm run dev
```

- Aplicación disponible en `http://localhost:3000`.

---

## 2. Verificación automatizada

### 2.1 Backend

Ejecutar las pruebas unitarias y de integración de KDS, cola, controladores y repositorios:

```bash
cd easyeat/backend
mvn test -Dtest="*Kds*,*Kitchen*"
```

Para ejecutar la verificación completa de integración:

```bash
mvn test -Dtest="KitchenKdsControllerIntegrationTest,KdsIdempotencyIntegrationTest,KitchenReadyForPickupIntegrationTest"
```

### 2.2 Frontend

Ejecutar las suites de Vitest para los componentes, hooks y páginas de cocina:

```bash
cd easyeat/frontend
npm test -- --run tests/pages/KitchenWorkspacePage.test.tsx
npm test -- --run src/test/kds/
```

Validar tipado y compilación limpia:

```bash
npm run build
```

---

## 3. Protocolo de validación manual end-to-end

### 3.1 Acceso al sistema como personal de cocina

1. Iniciar sesión en el frontend (`http://localhost:3000/login`) con un usuario con rol `CHEF` (por ejemplo: `carlos.chef@comedorcentral.com`).
2. Navegar a la ruta `/cocina`.
3. Constatar que se presenta el tablero Kanban de dos columnas operativas fijas:
   - **Columna 1 ("Pendientes")**
   - **Columna 2 ("En preparación")**

---

### 3.2 Escenario 1: Convivencia FIFO unificada y normalización de franja horaria

1. **Creación de pedidos**:
   - Crear un pedido programado (`SCHEDULED`) asignado a la franja `13:00 - 13:15` de hoy (confirmado a las `10:00:00`).
   - Crear un pedido inmediato (`IMMEDIATE`) confirmado en el minuto 13:04 de hoy.
2. **Verificación en Columna 1**:
   - El pedido inmediato normaliza su bloque a `13:00 - 13:15` (`floor(4 / 15) * 15 = 0`).
   - Ambas tarjetas exhiben el turno `13:00 - 13:15`.
   - En la Columna 1, la tarjeta del pedido programado precede a la del pedido inmediato debido a que su `queued_at` fue anterior (`10:00:00` vs `13:04:00`).
   - Se valida el cumplimiento de FIFO unificado por momento de confirmación sin distinción por modalidad (`RF-003`, `RF-005`).

---

### 3.3 Escenario 2: Exclusión estricta de pedidos de fechas futuras

1. Crear un pedido programado (`SCHEDULED`) para el día de mañana (`pickup_date = hoy + 1 día`).
2. Consultar el tablero del KDS en `/cocina`.
3. **Verificación**:
   - La orden de mañana no aparece en ninguna de las dos columnas de la cola activa (`RF-002`, `Invariante 1`).
   - Solo se exhiben pedidos con `pickup_date <= hoy`.

---

### 3.4 Escenario 3: Precedencia absoluta de turnos vencidos incompletos

1. Sembrar en base de datos una comanda en estado `QUEUED` perteneciente a un turno anterior que ya cerró (por ejemplo, franja `12:00 - 12:15` habiendo pasado las `13:00`).
2. Generar nuevas comandas para el turno en curso (`13:00 - 13:15`).
3. **Verificación**:
   - En la Columna 1, la comanda del turno vencido (`12:00 - 12:15`) encabeza la lista en el tope absoluto, por encima de todas las órdenes del turno actual y turnos futuros del día (`RF-004`).

---

### 3.5 Escenario 4: Transición reactiva con botón "EMPEZAR"

1. Ubicar una comanda en la Columna 1 ("Pendientes").
2. Observar su formato compacto: exhibe `#ticketNumber`, franja `HH:mm - HH:mm` y botón "EMPEZAR", sin lista de platos ni campos de texto libre (`RF-006`, `RF-010`).
3. Pulsar el botón **"EMPEZAR"**.
4. **Verificación**:
   - El botón se deshabilita momentáneamente para evitar dobles envíos.
   - La comanda desaparece inmediatamente de la Columna 1 y pasa a la Columna 2 ("En preparación") (`RF-007`).
   - En la Columna 2, la comanda muestra ahora el desglose de platos bajo el formato `[Cantidad]x [Nombre del Plato]`.
   - La cabecera exhibe el cronómetro iniciando en `0 min`.
   - En base de datos, el ticket tiene `status = 'IN_PREPARATION'` y `started_at` registrado; la orden vinculada tiene `status = 'IN_PROGRESS'`.

---

### 3.6 Escenario 5: Idempotencia ante doble clic o reintentos concurrentes

1. Realizar dos pulsaciones rápidas consecutivas sobre el botón "EMPEZAR" de una misma comanda (o simular una petición `POST /api/v1/kitchen/tickets/{id}/start-preparation` concurrente mediante terminal/curl).
2. **Verificación**:
   - El sistema responde exitosamente con `200 OK`.
   - Solo existe una tarjeta en la Columna 2 (sin duplicados).
   - El valor `started_at` persistido en el servidor se mantiene inalterado respecto al primer llamado (`RF-008`, `Invariante 5`).

---

### 3.7 Escenario 6: Cronómetro Count-Up, persistencia ante recarga y multi-pantalla

1. Mantener una comanda en la Columna 2 durante más de 60 segundos.
2. **Verificación**:
   - Al cumplirse los 60 segundos acumulados, el contador pasa reactivamente de `0 min` a `1 min` sin necesidad de recargar la página (`RF-011`).
3. Recargar el navegador (`F5` o `Ctrl+R`) en el minuto 4.
4. **Verificación**:
   - Al cargar la página, el contador muestra de inmediato `4 min` (no vuelve a cero), demostrando que se deriva del `started_at` persistido en el servidor (`RF-012`).
5. Abrir una segunda ventana de incógnito o segundo navegador en `/cocina`.
6. **Verificación**:
   - Ambas pantallas sincronizan exactamente el mismo valor de minutos transcurridos (`SC-002`).

---

### 3.8 Escenario 7: Activación reactiva de alerta ámbar a los 15 minutos exactos

1. Monitorear una comanda en Columna 2 que alcance los 14 minutos transcurridos (o sembrar un ticket con `started_at = now - 14 minutos 50 segundos`).
2. **Verificación a los 14 min**:
   - La tarjeta mantiene contorno neutral estándar (`border-gray-200`) e insignia neutral (`RF-013`).
3. Aguardar a que el reloj alcance exactamente los 15:00 minutos transcurridos (`tiempo >= 15 min`).
4. **Verificación a los 15 min**:
   - El contorno perimetral de la tarjeta y la insignia del tiempo conmutan automáticamente a color ámbar/amarillo (`#EAB308` / `#F59E0B`).
   - El cuerpo de la comanda mantiene el fondo blanco y la tipografía de los platos con contraste nítido y fondo neutro sin teñirse (`RF-014`, `Invariante 7`).

---

### 3.9 Escenario 8: Comportamiento ante columnas vacías

1. En un entorno sin comandas pendientes:
   - La Columna 1 exhibe el mensaje neutral: `"Sin pedidos pendientes"`.
2. En un entorno sin comandas en preparación:
   - La Columna 2 exhibe el mensaje neutral: `"Sin pedidos en preparación"`.
3. **Verificación**:
   - Ambas columnas conservan su división y ancho relativo estándar (50% cada una en escritorio).
   - No se producen saltos de diseño (*layout shifts*) ni colapso de contenedores (`RF-016`).

---

### 3.10 Escenario 9: Ausencia total de placeholders y notas libres

1. Inspeccionar visualmente las tarjetas tanto en Columna 1 como en Columna 2.
2. **Verificación**:
   - Ninguna comanda presenta renglones en blanco, textos `"Sin notas"`, `"Observaciones: ninguna"` ni marcadores vacíos (`RF-010`, Caso Borde 8).
   - Todo el espacio visual se destina exclusivamente a la información operativa del pedido.
