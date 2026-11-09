# Quickstart: Guía de Validación y Ejecución de la Spec 016

**Feature Branch**: `016-payment-confirmation-tracking-branding`  
**Fecha**: 09/11/2026  
**Especificación**: [spec.md](./spec.md) | [plan.md](./plan.md)  
**Estado**: Completado  

---

## 1. Propósito y Alcance

Esta guía describe los procedimientos operativos ejecutables para verificar de extremo a extremo los 9 escenarios de aceptación (**AS-1** a **AS-9**), los 9 requerimientos funcionales (**FR-001** a **FR-009**), las 10 invariantes de negocio (**INV-001** a **INV-010**) y los casos borde (**CB-001** a **CB-012**) de la **Spec 016**, abarcando:
1. Pantalla verde de confirmación de pago y número de orden (**AS-1**, **AS-3**).
2. Emisión garantizada de comprobante por correo ante interrupciones de navegación (**AS-2**).
3. Bloqueo preventivo de interfaz durante el procesamiento de cobro (**AS-4**).
4. Seguimiento en vivo para retiro inmediato con tiempo dinámico de KDS (**AS-5**).
5. Actualización reactiva y silenciosa del reloj dinámico ante ajustes globales de cocina (**AS-7**).
6. Horario pactado estático y cuenta regresiva a 15 minutos en reservas programadas (**AS-6**).
7. Transición irrevocable a "Listo para retirar" y número de orden gigante (**AS-8**).
8. Configuración de branding en "Mi Local", cálculo de contraste y aplicación en el catálogo (**AS-9**).
9. Fallback institucional neutro de EasyEat ante ausencia de branding (**CB-011**, **INV-010**).
10. Convivencia unificada y persistencia ante recarga o reingreso (**CB-012**, **Q2/A2**).

---

## 2. Prerrequisitos y Configuración de Entorno

### 2.1. Backend (Spring Boot 3.3.4 / Java 21)
```bash
# Ubicación
cd easyeat/backend

# Aplicar migraciones Flyway (incluyendo V20) e iniciar backend con perfil dev/local
./mvnw clean spring-boot:run -Dspring-boot.run.profiles=local
```
El backend estará disponible en `http://localhost:8080`.

### 2.2. Frontend (React 18 / TypeScript / Vite)
```bash
# Ubicación
cd easyeat/frontend

# Instalar dependencias si correspondiera e iniciar servidor de desarrollo
npm install
npm run dev
```
La aplicación web estará disponible en `http://localhost:5173`.

### 2.3. Cuentas de Prueba Preexistentes en Base de Datos
- **Comensal**: `diner@easyeat.com` / `Password123!` (Rol: `ROLE_COMENSAL`)
- **Administrador**: `admin@easyeat.com` / `Password123!` (Rol: `ROLE_ADMINISTRADOR`, `tenantId: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`)
- **Cocinero / Chef**: `chef@easyeat.com` / `Password123!` (Rol: `ROLE_CHEF`, `tenantId: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`)

---

## 3. Escenarios Ejecutables de Verificación

---

### Escenario 1: Bloqueo de Interfaz Durante el Procesamiento (AS-4, INV-003, CB-003)

1. Iniciar sesión como comensal en `http://localhost:5173/login`.
2. Navegar a `/catalogo` de "Café Central Palermo", agregar platos al carrito e ingresar a `/checkout`.
3. Seleccionar modalidad `IMMEDIATE` y presionar el botón **"Confirmar y Pagar"**.
4. **Verificación visual y funcional**:
   - De manera inmediata, el botón cambia su contenido a un spinner animado con el texto `"Procesando pago seguro..."`.
   - El atributo `disabled` se activa (`disabled={true}`).
   - Intentar clics repetidos rápidos o presionar repetidamente la tecla `Enter`: constatar que la interfaz no envía peticiones redundantes ni se generan cobros duplicados en la pasarela (**SC-004**).

---

### Escenario 2: Aprobación de Pago, Pantalla Verde y Franja Horaria (AS-1, AS-3, INV-001, INV-004)

1. En el checkout de prueba, simular la respuesta autoritativa exitosa de Mercado Pago (`status = APPROVED`).
2. El sistema redirige automáticamente a la ruta unificada `/checkout/confirmacion/{orderId}`.
3. **Verificación visual y funcional**:
   - Se despliega un contenedor verde de confirmación con animación de verificación y mensaje de pago aprobado (**AS-1**).
   - Se exhibe claramente el número de orden asignado (ej. `#142`).
   - Se detalla la franja horaria pactada de retiro (ej. `"Hoy entre las 14:00 y 14:30 hs"`).
   - El widget de seguimiento en vivo se encuentra incrustado y activo de forma visible en la misma pantalla (**AS-3**, **Q2/A2**).

---

### Escenario 3: Interrupción de Navegador y Comprobante Garantizado por Correo (AS-2, INV-002, CB-002)

1. Realizar una compra en checkout.
2. En el instante exacto en que la pasarela aprueba el cobro, **cerrar bruscamente la pestaña del navegador o desconectar la conexión de red** antes de que cargue la pantalla verde.
3. **Verificación en el servidor / consola**:
   - Observar los logs del backend: `OrderPaymentApprovedEvent` se procesa de forma autónoma en segundo plano vía `@Async`.
   - `OrderReceiptEmailService` despacha el correo a la casilla `diner@easyeat.com` con `idempotency_key = receipt-{orderId}`.
   - En entorno local, verificar la traza de log generada por `LoggingEmailSender`:
     ```text
     [OrderReceiptEmailService] Dispatched order receipt email for Order 3fa85f64... to diner@easyeat.com. Tracking URL: http://localhost:5173/pedidos/3fa85f64.../seguimiento
     ```
   - Consultar la tabla `order_receipts`: constatar que el registro figura con `status = 'SENT'`.
4. Enviar un webhook duplicado de pasarela con el mismo `orderId`: constatar que no se genera un segundo correo (garantía de idempotencia **INV-002**).

---

### Escenario 4: Convivencia y Persistencia ante Recarga o Reingreso (CB-012, Q2/A2)

1. Con la pantalla de confirmación desplegada, presionar **F5** (recargar página) o abrir una nueva ventana con el enlace recibido en el correo (`/pedidos/{orderId}/seguimiento`).
2. **Verificación**:
   - La pantalla reconstruye sin fisuras la cabecera verde de confirmación aprobada con su número de orden junto con el widget vivo de seguimiento en su estado actual, demostrando persistencia total sin pérdida de contexto (**Q2/A2**).

---

### Escenario 5: Retiro Inmediato y Tiempo Dinámico según KDS (AS-5, INV-005, CB-004)

1. Consultar una orden con modalidad `IMMEDIATE` recién confirmada.
2. Constatar que el widget exhibe una barra de progreso inicial y un tiempo estimado de entrega (ej. `18 min`), calculado a partir de la cola de comandas del KDS.
3. Simular saturación creando 3 comandas adicionales en estado `QUEUED` en el KDS para ese establecimiento.
4. En la siguiente consulta de seguimiento: verificar que el minutero refleja el incremento proporcional en los minutos de preparación (**INV-005**).

---

### Escenario 6: Actualización Silenciosa del Reloj ante Ajustes en Cocina (AS-7, INV-007, Q6/A6)

1. Mantener abierta la vista de seguimiento del comensal (con el reloj marcando, por ejemplo, `18 min`).
2. Abrir otra ventana como personal de cocina (`/cocina`) e introducir un ajuste global de tiempo de `+10 min` en el KDS:
   ```bash
   curl -X PUT http://localhost:8080/api/v1/kitchen/delay \
     -H "Authorization: Bearer <jwt-token-chef>" \
     -H "Content-Type: application/json" \
     -d '{"establishmentId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", "globalDelayMinutes": 10}'
   ```
3. Observar la pantalla del comensal:
   - Dentro de los siguientes 5 segundos, el reloj se actualiza de `18 min` a `28 min` de manera fluida y reactiva.
   - **Verificación clave**: No se muestran popups, carteles emergentes ni modales de alerta; la actualización es completamente silenciosa (**AS-7**, **Q6/A6**).

---

### Escenario 7: Reserva Programada: Horario Estático y Cuenta Regresiva a 15 Min (AS-6, INV-006, Q1/A1, CB-005, CB-006)

1. Crear y confirmar una orden programada (`SCHEDULED`) para las 16:00 hs (siendo actualmente las 14:00 hs, faltan 120 minutos).
2. **Verificación (> 15 min)**:
   - La pantalla muestra fija la tarjeta con el horario acordado: `"Turno programado: 16:00 - 16:30 hs"`.
   - No hay ningún contador regresivo activo (**INV-006**).
3. Avanzar el tiempo del servidor o simular orden con turno dentro de 14 minutos (ej. turno a las 14:14 hs siendo las 14:00 hs):
   - **Verificación ($\le$ 15 min)**: El widget conmuta de forma reactiva a una cuenta regresiva dinámica en tiempo real que descuenta minuto a minuto hacia las 14:14 hs (**AS-6**, **Q1/A1**).

---

### Escenario 8: Finalización en Cocina: Transición a "Listo para Retirar" (AS-8, INV-008, CB-008)

1. Con el comensal observando la vista de seguimiento, el personal de cocina marca el pedido como finalizado en el KDS:
   ```bash
   curl -X POST http://localhost:8080/api/v1/kitchen/tickets/{ticketId}/ready-for-pickup \
     -H "Authorization: Bearer <jwt-token-chef>"
   ```
2. **Verificación observable en la pantalla del comensal**:
   - El tiempo estimado y la barra de progreso desaparecen de inmediato (**AS-8**).
   - La pantalla conmuta a un estado destacado verde con el mensaje `"¡Tu pedido está listo para retirar!"`.
   - Se exhibe el número de orden en tipografía de gran formato (ej. `#142` en tamaño gigante).
   - Las solicitudes periódicas de polling se detienen.
   - El estado es irreversible (**INV-008**).

---

### Escenario 9: Configuración de Branding en "Mi Local" y Adopción en Catálogo (AS-9, ADM-2.1.1, Q5/A5, Q7/A7, INV-009)

1. Iniciar sesión como administrador (`admin@easyeat.com`) y acceder a `/admin/mi-local`.
2. Completar los campos de marca:
   - **Nombre de fantasía**: `"Café Central Palermo"`
   - **Descripción**: `"Café de especialidad y brunch de autor"`
   - **Color Primario (Acciones/CTAs)**: `#2563EB` (Azul)
   - **Color Secundario (Cabecera)**: `#1E3A8A` (Azul Marino Oscuro)
   - **Color de Acento (Badges/Bordes)**: `#F59E0B` (Ámbar)
   - **Logotipo**: Subir archivo de imagen válido (PNG, 500x500 px).
3. Presionar **"Guardar Configuración"**:
   - El sistema valida el formato hexadecimal `#RRGGBB`.
   - Calcula automáticamente el contraste: `#FFFFFF` para primario y secundario, `#000000` para acento (**Q5/A5**).
4. Abrir una ventana como comensal e ingresar a `/catalogo` de dicho restaurante:
   - **Cabecera**: Adopta el fondo `#1E3A8A` con textos en blanco y exhibe el logo y nombre de fantasía.
   - **Botón "Agregar al carrito" y "Pagar"**: Adoptan el fondo `#2563EB` con texto en blanco.
   - **Badges de alérgenos y etiquetas**: Adoptan el fondo `#F59E0B` con texto oscuro legible (**AS-9**, **Q7/A7**).

---

### Escenario 10: Fallback Institucional Neutro de EasyEat (INV-010, Q3/A3, CB-011)

1. Ingresar como comensal al catálogo de un restaurante que aún no ha cargado branding (ej. "Bistró San Telmo").
2. **Verificación**:
   - La interfaz despliega la identidad visual base de EasyEat:
     - Color primario: Verde Esmeralda `#10B981` (contraste blanco).
     - Color secundario: Verde Oscuro `#065F46` (contraste blanco).
     - Color de acento: Ámbar `#F59E0B` (contraste oscuro).
     - Isotipo institucional neutro de EasyEat.
     - Nombre comercial del local registrado en su alta operativa.
   - Cero componentes rotos o vacíos (**INV-010**).

---

## 4. Ejecución Automatizada de la Suite de Pruebas

Para validar la integridad de extremo a extremo sin intervención manual:

```bash
# Backend: Tests unitarios, integración MockMvc y cobertura de contratos
cd easyeat/backend
./mvnw test -Dtest="*PaymentConfirmation*,*OrderLiveTracking*,*RestaurantBranding*,*OrderReceipt*"

# Frontend: Tests de componentes, hooks de polling y comportamiento observable
cd easyeat/frontend
npm run test -- --run src/test/order/OrderConfirmationTracking.test.tsx src/test/admin/AdminBrandingPage.test.tsx
```

