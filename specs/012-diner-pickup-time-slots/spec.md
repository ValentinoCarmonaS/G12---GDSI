# Feature Specification: Selección de Modalidad de Retiro y Motor de Reservas por Franja Horaria

**Feature Branch**: `012-diner-pickup-time-slots`  
**Created**: 31/10/2026 | **Last Updated**: 31/10/2026  
**Status**: Draft (Alineada con Grill-Me y Scope Oficial)  
**Input**: User description: "Diner Order Fulfillment Mode & Time-Slot Reservation Engine (COM 6.2.1, COM 6.2.2, COM 6.2.3, COM 6.2.4)"

---

## Clarifications

### Session 31/10/2026 (Grill-Me Alignment con Arquitectura y Sprint 3)

- **Q1: Persistencia de la reserva y relación con pagos**: Dado que la pasarela de pagos se incorpora formalmente en el Sprint 4 y el alcance actual excluye transacciones bancarias pero exige garantizar la invariante de capacidad con concurrencia atómica, ¿cómo se persiste la reserva al completar el checkout?  
  → **A1**: Se crea el agregado `Order` en estado `PENDING_PAYMENT` (o `RESERVED`), asignando y asegurando atómicamente el cupo de la franja horaria en la base de datos. En el Sprint 4, la pasarela de pagos consumirá directamente esta orden y transicionará su estado a `PAID`, liberando o confirmando el cupo sin requerir tablas intermedias descartables.

- **Q2: Lead time de preparación y visualización de franjas pasadas**: Para la grilla de reservas del día actual y el margen mínimo de preparación anticipada (lead time), ¿qué valor por defecto y comportamiento visual se adopta?  
  → **A2**: Se fija un lead time predeterminado de 30 minutos a partir de la hora actual del servidor. Las franjas que ya transcurrieron y aquellas comprendidas dentro del buffer de preparación se muestran en la grilla en estado deshabilitado (atenuadas en gris con leyenda explicativa de no disponibilidad), preservando la estructura temporal completa del horario comercial del local.

- **Q3: Estrategia de actualización en tiempo real en checkout**: Considerando que los WebSockets bidireccionales se introducen en el Sprint 5 (KDS de cocina), ¿cómo mantiene el frontend la disponibilidad y saturación de las franjas horarias?  
  → **A3**: Mediante polling periódico liviano (intervalo configurable de 5 a 10 segundos) mientras el comensal permanece en la pantalla de checkout, complementado de forma estricta con revalidación atómica en el backend al momento exacto de enviar la confirmación de la orden.

- **Q4: Acceso al checkout fuera del horario operativo**: Si un comensal con carrito armado ingresa al checkout cuando el establecimiento se encuentra cerrado o fuera de su horario de atención comercial, ¿cómo responde el selector de modalidad?  
  → **A4**: La modalidad "Lo antes posible" se presenta deshabilitada junto a un distintivo visible indicando que el establecimiento está fuera de su horario operativo. Se exige al comensal seleccionar "Para más tarde", permitiéndole elegir un día y turno futuro dentro del calendario comercial habilitado.

- **Q5: Articulación con el modo 'solo lectura' del catálogo fuera de horario (COM-4.1.1 en Spec 008)**: En Spec 008, el catálogo cerrado se definió en 'modo solo lectura' (debido a que en Sprint 2 no existía el carrito de compras ni reservas diferidas). ¿Cómo convive dicha regla con la reserva de pedidos anticipados de Sprint 3?  
  → **A5**: Evolución natural y armónica para Sprint 3: el modo 'solo lectura' de Spec 008 restringe de forma estricta el retiro *inmediato* (en consonancia con la definición original del backlog: *"deshabilita la opción de agregar al carrito para retiro inmediato"* y la nota de Spec 008 *"respetando la coherencia con futuras reservas diferidas (Sprint 3)"*). Estando el establecimiento cerrado, el comensal puede conformar su carrito y avanzar al checkout; es en esta pantalla donde "Lo antes posible" permanece inhabilitado y se lo encauza obligatoriamente a una reserva programada ("Para más tarde") en un turno operativo futuro.

---

## Sprint Scope Delimitation Matrix

Para garantizar el cumplimiento de los Principios I, V y VII de la Constitución del proyecto, la siguiente matriz explicita la cobertura de los criterios de aceptación del módulo, distinguiendo las entregas del presente Sprint 3 de las dependencias diferidas a sprints posteriores:

| Identificador | Criterio / Capacidad Funcional | Sprint Asignado | Tratamiento en Spec 012 |
|---|---|:---:|---|
| **COM-6.2.1** | Seleccionar tipo de retiro (Inmediato o Reserva diferida) | **Sprint 3** | Satisfecho por FR-001, FR-002, FR-003, FR-004 y FR-005. |
| **COM-6.2.2** | Seleccionar día y franja horaria de 15 minutos (hasta 7 días operativos) | **Sprint 3** | Satisfecho por FR-006, FR-007, FR-008, FR-009 y FR-010. |
| **COM-6.2.3** | Indicador visual de demanda/saturación de franja (<80% normal, 80-99% alta demanda) | **Sprint 3** | Satisfecho por FR-011, FR-012, FR-013 y FR-014. |
| **COM-6.2.4** | Bloqueo estricto de sobreventa al 100% y resolución atómica de colisiones FCFS | **Sprint 3** | Satisfecho por FR-015, FR-016, FR-017, FR-018 y FR-019. |
| **COM-7.1.x / COM-7.2.1** | Pasarela de pagos, carga de tarjetas y procesamiento transaccional | Sprint 4 | Fuera de alcance en esta especificación; el checkout concluye con la orden reservada. |
| **CHF-5.1.1** | Descuento automático de stock ACID de recetas tras pago exitoso | Sprint 4 | Fuera de alcance en esta especificación; diferido a la confirmación de cobro. |
| **ADM-2.2.1 / ADM-2.2.2** | Pantallas de administración para configurar horarios y cupos por franja | Sprint 3 (ADM) | Este documento especifica el consumo por el comensal; el back-office administrativo se trata en su respectiva historia. |

---

## User Scenarios & Testing

### User Story 1 - Selección de Modalidad de Retiro Excluyente (Prioridad: P1)

Como comensal que finalizó la carga de platos en su carrito,  
quiero elegir si retiraré mi pedido lo antes posible o en un horario programado para más tarde,  
para adaptar la entrega a mi disponibilidad horaria sin generar confusiones en la cocina.

**Por qué esta prioridad**: Es la bifurcación obligatoria del flujo de checkout. Sin este paso, el sistema no puede determinar cuándo debe ingresar la orden al circuito de elaboración ni validar la capacidad del local.

**Prueba independiente**: Se puede probar accediendo al checkout con un carrito válido y alternando entre "Lo antes posible" y "Para más tarde", verificando que la selección sea mutuamente excluyente, que el selector de turnos se despliegue o colapse dinámicamente y que no sea posible enviar el pedido sin una modalidad activa.

**Criterios de Aceptación**:

1. **Given** un comensal en la pantalla de checkout con ítems activos en su carrito,  
   **When** visualiza el selector de modalidad,  
   **Then** ambas opciones ("Lo antes posible" y "Para más tarde") se presentan de manera clara y deseleccionadas por defecto (o requiriendo confirmación explícita), impidiendo avanzar si ninguna está seleccionada.

2. **Given** que el comensal selecciona "Lo antes posible" y la cocina cuenta con capacidad disponible en la franja en curso,  
   **When** confirma la modalidad,  
   **Then** el sistema asigna el pedido a la preparación inmediata y mantiene oculto el calendario de franjas horarias.

3. **Given** que el comensal seleccionó previamente un día y turno bajo "Para más tarde",  
   **When** cambia su elección a "Lo antes posible",  
   **Then** el sistema limpia inmediatamente la fecha y franja preseleccionadas y repliega el panel de programación de turnos.

4. **Given** que el establecimiento está operando a máxima capacidad en la franja horaria en curso (100% ocupada),  
   **When** el comensal carga la vista de checkout,  
   **Then** el control "Lo antes posible" se presenta deshabilitado junto a un mensaje explicativo ("Cocina al límite de capacidad actual — Seleccioná una reserva programada"), obligando a optar por "Para más tarde".

---

### User Story 2 - Programación de Reserva en Franjas de 15 Minutos (Prioridad: P1)

Como comensal que necesita retirar su comida en un momento específico de su jornada,  
quiero elegir un día operativo y un turno de 15 minutos con la antelación requerida,  
para asegurarme de que mi pedido esté listo exactamente cuando llegue al mostrador.

**Por qué esta prioridad**: Cumple la propuesta central de valor para comensales corporativos y universitarios que organizan sus descansos con horarios fijos.

**Prueba independiente**: Se puede validar seleccionando fechas dentro del rango permitido (hasta 7 días operativos), comprobando que los días no comerciales permanezcan inhabilitados, que las franjas ya vencidas del día no sean elegibles y que se respete el buffer mínimo de preparación de 30 minutos.

**Criterios de Aceptación**:

1. **Given** que el comensal activa la modalidad "Para más tarde",  
   **When** se despliega la interfaz de programación,  
   **Then** se presenta un selector de fechas que abarca únicamente los próximos 7 días operativos del establecimiento, inhabilitando fines de semana o días de cierre comercial.

2. **Given** la selección de una fecha operativa válida,  
   **When** se renderiza la grilla de horarios,  
   **Then** se listan intervalos continuos de 15 minutos que van desde el horario de apertura hasta el cierre comercial del establecimiento (por ejemplo: "13:00 - 13:15", "13:15 - 13:30").

3. **Given** que el comensal consulta las franjas para el día de la fecha,  
   **When** el reloj del servidor marca las 12:10 y el lead time configurado es de 30 minutos,  
   **Then** todas las franjas cuyo inicio sea inferior a las 12:40 (pasadas o dentro del buffer de preparación) se muestran deshabilitadas y no clickeables.

4. **Given** una franja horaria disponible y válida seleccionada por el comensal,  
   **When** revisa el resumen de su checkout,  
   **Then** la fecha y el rango horario elegido ("Día DD/MM/YYYY de HH:mm a HH:mm") se reflejan claramente en el resumen antes de la confirmación final.

---

### User Story 3 - Visualización de Demanda y Alerta Preventiva de Saturación (Prioridad: P2)

Como comensal que evalúa distintos turnos de retiro,  
quiero ver el nivel de concurrencia de cada franja horaria antes de confirmar,  
para anticipar posibles demoras y elegir turnos con menor congestión si tengo poco tiempo.

**Por qué esta prioridad**: Reduce la frustración del usuario en mostrador, transparenta el ritmo de la cocina y distribuye de forma orgánica la demanda evitando picos concentrados.

**Prueba independiente**: Se puede probar simulando turnos con diferente porcentaje de ocupación (<80% y entre 80% y 99%), verificando el cambio de estilo visual a tono ámbar y la aparición de la advertencia preventiva sin impedir la selección.

**Criterios de Aceptación**:

1. **Given** una franja horaria con ocupación inferior al 80% de su capacidad máxima,  
   **When** se dibuja en la pantalla del comensal,  
   **Then** se presenta con el diseño neutral estándar de disponibilidad normal y permite su selección directa sin advertencias adicionales.

2. **Given** una franja horaria cuya ocupación actual se encuentra entre el 80% y el 99% de su cupo máximo,  
   **When** el comensal la visualiza en la grilla,  
   **Then** se destaca visualmente en color de advertencia (ámbar/naranja) con la insignia visible "Alta Demanda".

3. **Given** que el comensal selecciona una franja clasificada como "Alta Demanda",  
   **When** la marca en la grilla,  
   **Then** el sistema incorpora un aviso preventivo no bloqueante en el resumen del checkout indicando: "Esta franja registra un alto volumen de cocina. Podrían producirse leves demoras en mostrador."

---

### User Story 4 - Garantía de Capacidad Cero Overbooking y Resolución de Concurrencia (Prioridad: P1)

Como responsable de la cocina y comensal del establecimiento,  
quiero que el sistema impida terminantemente la reserva de franjas que alcanzaron el 100% de su cupo,  
para garantizar que la cocina nunca reciba más pedidos de los que humanamente puede despachar por turno.

**Por qué esta prioridad**: Es la restricción técnica y operativa más crítica del sistema (invariante fundamental de capacidad). Su quiebre provocaría colapso en la cocina y pedidos no entregados a tiempo.

**Prueba independiente**: Se puede verificar saturando un turno al 100% y comprobando que quede estrictamente deshabilitado, además de lanzar dos solicitudes concurrentes sobre el último cupo disponible validando que solo una prospere y la otra sea rechazada limpiamente con aviso explicativo.

**Criterios de Aceptación**:

1. **Given** una franja horaria cuya cantidad de órdenes confirmadas iguala a su capacidad máxima configurada (100%),  
   **When** cualquier comensal visualiza la grilla de horarios,  
   **Then** la tarjeta del turno se renderiza atenuada en gris, deshabilitada y con la indicación explícita "Agotado", impidiendo cualquier selección.

2. **Given** un turno con un único cupo restante (por ejemplo: 9/10 órdenes confirmadas) y dos comensales (A y B) con dicho turno seleccionado en sus pantallas,  
   **When** el comensal A confirma su pedido milisegundos antes que el comensal B,  
   **Then** la transacción de A asigna exitosamente el último cupo y crea su orden en estado `PENDING_PAYMENT`.

3. **Given** el escenario anterior donde el cupo fue tomado por el comensal A,  
   **When** la solicitud de confirmación del comensal B alcanza el servidor,  
   **Then** el backend aborta atómicamente la operación, no persiste ninguna orden para B y devuelve un mensaje de conflicto de capacidad.

4. **Given** el rechazo por colisión concurrente en la pantalla del comensal B,  
   **When** la interfaz procesa la respuesta,  
   **Then** despliega la notificación modal o banner destacado: *"La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar."*, reabriendo de inmediato el selector con el estado de las franjas actualizado.

5. **Given** un turno que se encontraba agotado al 100%,  
   **When** una orden previa perteneciente a esa franja es cancelada o liberada,  
   **Then** en el siguiente ciclo de actualización del cliente (polling o recarga) la franja vuelve a mostrarse habilitada respetando el porcentaje de demanda resultante.

---

## Edge Cases

- **Desfase horario entre cliente y servidor**: Si el dispositivo del usuario tiene una hora local retrasada o adelantada, todas las comparaciones de vigencia, cálculo de lead time y franjas pasadas se evalúan exclusivamente contra la hora oficial devuelta por el servidor del establecimiento (`timezone` oficial).
- **Envejecimiento del turno en pantalla (tiempo muerto en checkout)**: Si un comensal selecciona un turno válido para el día de hoy y permanece inactivo en el checkout durante 25 minutos de modo que el inicio de la franja queda dentro del buffer de preparación (lead time) o en el pasado, la validación del servidor al momento de confirmar rechaza el pedido informando que el turno ya no cumple con el margen mínimo de anticipación y solicita elegir una nueva franja.
- **Transición concurrente a saturación durante la navegación**: Si el comensal visualiza un turno en estado normal o alta demanda y este se agota mientras completa sus datos, el mecanismo de confirmación atómica en backend detiene la operación antes de persistir la orden.
- **Establecimiento cerrado para pedidos inmediatos**: Si el usuario llega al checkout con un carrito armado pero el local está cerrado por horario, "Lo antes posible" queda deshabilitado con un badge de local fuera de horario y solo se permite "Para más tarde".
- **Pedidos cercanos al horario de cierre**: Las franjas horarias disponibles no pueden exceder el horario de cierre comercial del establecimiento. La última franja del día debe finalizar a más tardar en el horario de cierre (`closing_time`).
- **Pérdida temporal de conexión en checkout**: Si el polling periódico falla por corte momentáneo de red, la interfaz no bloquea la interacción del usuario y reintenta en el siguiente ciclo. Si falla al momento de la confirmación final, se notifica el fallo de red sin duplicar solicitudes.
- **Alternancia repetitiva entre modos**: Si el comensal cambia repetidamente entre "Lo antes posible" y "Para más tarde", el estado interno limpia y restablece consistentemente los campos de fecha y turno sin dejar valores residuales huérfanos.

---

## Requirements

### Functional Requirements

- **FR-001**: El sistema DEBE requerir de forma obligatoria la selección explícita de la modalidad de retiro ("Lo antes posible" o "Para más tarde") como condición previa para confirmar el checkout.
- **FR-002**: El sistema DEBE garantizar que las modalidades "Lo antes posible" y "Para más tarde" sean mutuamente excluyentes en todo momento.
- **FR-003**: Cuando el comensal seleccione "Lo antes posible", el sistema DEBE verificar que la franja horaria en curso del establecimiento posea ocupación inferior al 100%.
- **FR-004**: Si la franja horaria en curso alcanza el 100% de ocupación, el sistema DEBE inhabilitar la opción "Lo antes posible" y mostrar el mensaje explicativo: *"Cocina al límite de capacidad actual — Seleccioná una reserva programada"*.
- **FR-005**: Si el comensal conmuta de "Para más tarde" a "Lo antes posible", el sistema DEBE purgar inmediatamente la fecha y franja horaria previamente seleccionadas y contraer la interfaz de turnos.
- **FR-006**: Al activar "Para más tarde", el sistema DEBE desplegar un selector de fechas que permita elegir exclusivamente entre los próximos 7 días operativos del establecimiento.
- **FR-007**: El sistema DEBE inhabilitar en el selector de fechas cualquier día no operativo, fin de semana o feriado en el que el establecimiento permanezca cerrado.
- **FR-008**: Para cada día operativo seleccionado, el sistema DEBE generar y presentar la grilla de franjas horarias en intervalos homogéneos de 15 minutos, acotados entre el `opening_time` y el `closing_time` del local.
- **FR-009**: Para reservas del día en curso, el sistema DEBE inhabilitar todas las franjas horarias cuyo horario de inicio sea anterior a la hora actual del servidor más el margen de preparación anticipada (`lead_time`, por defecto 30 minutos).
- **FR-010**: Las franjas inhabilitadas por haber transcurrido o por violar el lead time DEBEN mostrarse en estado deshabilitado (gris atenuado) con una leyenda indicativa de turno no disponible.
- **FR-011**: El sistema DEBE calcular dinámicamente el porcentaje de ocupación de cada franja horaria según la relación: `(Órdenes Confirmadas / Capacidad Máxima del Turno) * 100`.
- **FR-012**: Las franjas con ocupación inferior al 80% DEBEN desplegarse con el estilo neutral estándar de disponibilidad normal.
- **FR-013**: Las franjas con ocupación mayor o igual al 80% y menor al 100% DEBEN destacarse en color preventivo (ámbar) e incorporar la insignia visible *"Alta Demanda"*.
- **FR-014**: Al seleccionar una franja marcada con "Alta Demanda", el sistema DEBE incorporar en el resumen del checkout una advertencia informativa preventiva sobre posibles demoras menores en mostrador.
- **FR-015**: Las franjas que hayan alcanzado el 100% de su capacidad máxima DEBEN mostrarse deshabilitadas con el distintivo *"Agotado"*, bloqueando cualquier intento de selección.
- **FR-016**: El sistema DEBE validar de forma atómica en el backend la disponibilidad de cupo en la franja seleccionada al momento exacto de procesar la confirmación del pedido.
- **FR-017**: En caso de colisión concurrente sobre el último cupo disponible de una franja, el sistema DEBE aplicar el criterio First-Come, First-Served (FCFS), asignando el cupo a la primera solicitud confirmada y abortando la transacción concurrente posterior.
- **FR-018**: Ante una colisión de cupo abortada, el backend DEBE rechazar la creación de la orden y el frontend DEBE presentar la alerta: *"La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar."*, refrescando automáticamente los estados de las franjas.
- **FR-019**: Al confirmar exitosamente el checkout, el sistema DEBE crear el registro de la orden en estado `PENDING_PAYMENT` (o `RESERVED`), computando de forma indivisible el cupo correspondiente a la franja.
- **FR-020**: El frontend DEBE realizar consultas periódicas livianas (polling de 5 a 10 segundos) durante la estancia en checkout para mantener sincronizado el estado de saturación de las franjas horarias visibles.
- **FR-021**: El sistema DEBE tomar la hora del servidor como única autoridad temporal para todas las evaluaciones de apertura, cierre, franjas vigentes y lead time, ignorando cualquier desfase del reloj del cliente.
- **FR-022**: El sistema DEBE conservar íntegro el contexto del establecimiento (`tenant_id`), comensal autenticado y los ítems del carrito durante toda la experiencia de configuración de retiro.
- **FR-023**: Cuando el establecimiento se encuentre fuera de su horario comercial de atención, el sistema DEBE permitir la conformación del carrito para reservas programadas, deshabilitando de forma estricta la modalidad 'Lo antes posible' en el checkout con un distintivo explicativo y obligando a elegir un turno en 'Para más tarde'.

### Invariants

1. **Invariante Fundamental de Capacidad**: Para cualquier franja horaria $S$, el total de órdenes confirmadas en $S$ jamás debe exceder la capacidad máxima configurada:  
   $$\text{Total Órdenes Confirmadas}(S) \le \text{Capacidad Máxima}(S)$$
2. **Invariante de Modalidad Obligatoria**: Ningún pedido puede ser enviado, persistido o validado sin contener una modalidad de retiro asignada (`IMMEDIATE` o `SCHEDULED`).
3. **Invariante de Disponibilidad Inmediata**: La modalidad `IMMEDIATE` solo puede confirmarse si la ocupación de la franja horaria en curso es estrictamente inferior al 100%.
4. **Invariante de Lead Time Mínimo**: Para todo turno programado `SCHEDULED` con horario de inicio $T_{inicio}$, debe cumplirse:  
   $$T_{inicio} \ge T_{servidor} + \text{Lead Time Buffer}$$
5. **Invariante de Ventana de Planificación**: La fecha de retiro $D_{reserva}$ debe pertenecer al intervalo:  
   $$D_{hoy} \le D_{reserva} \le D_{hoy} + 7\text{ días operativos}$$
6. **Invariante de Asignación Atómica (FCFS)**: La adjudicación de cupos en base de datos es serializable o atómica mediante bloqueo optimista/pesimista, garantizando cero sobreventa ante concurrencia extrema.

---

## Key Entities

- **Order (Orden / Pedido)**: Representa la transacción comercial iniciada por el comensal. Atributos conceptuales clave en esta iteración: identificador único, identificador del establecimiento (`tenant_id`), identificador del comensal, modalidad de retiro (`IMMEDIATE` o `SCHEDULED`), fecha de retiro programada, hora de inicio y fin de la franja asignada, estado de la orden (`PENDING_PAYMENT` / `RESERVED`), total y detalle de platos del carrito.
- **TimeSlotAvailability (Disponibilidad de Franja Horaria)**: Proyección operativa de un intervalo de 15 minutos en una fecha determinada. Atributos: fecha, hora de inicio, hora de fin, capacidad total de pedidos, cantidad de pedidos confirmados, porcentaje de saturación y estado operativo (`AVAILABLE`, `HIGH_DEMAND`, `SOLD_OUT`, `EXPIRED_OR_LEAD_TIME`).
- **EstablishmentOperatingSchedule (Configuración Operativa del Local)**: Define las restricciones temporales del establecimiento. Atributos: días operativos de atención, hora de apertura (`opening_time`), hora de cierre (`closing_time`), zona horaria (`timezone`, por defecto `America/Argentina/Buenos_Aires`), margen de anticipación mínimo (`lead_time_minutes`, por defecto 30 minutos) y cupo máximo de pedidos por franja de 15 minutos (por defecto 10 pedidos).

---

## Success Criteria

### Measurable Outcomes

- **SC-001 (Zero Overbooking)**: En el 100% de las pruebas automatizadas de estrés y concurrencia (mínimo 20 solicitudes simultáneas disputando el último cupo), la cantidad final de órdenes creadas en la franja es exactamente igual a la capacidad máxima, sin permitir un solo boleto excedente.
- **SC-002 (Resolución de Colisión Concurrente)**: Cuando un comensal es superado por una confirmación concurrente en el último cupo, el sistema detiene la transacción y presenta la alerta explicativa en menos de 500 ms, recargando las franjas sin requerir recarga completa del navegador.
- **SC-003 (Cumplimiento de Lead Time)**: Ninguna orden programada para el día actual puede ser confirmada si su hora de inicio dista menos de 30 minutos respecto del reloj del servidor al momento del impacto en base de datos.
- **SC-004 (Claridad en la Demanda)**: El 100% de las franjas con ocupación entre el 80% y 99% exhiben la insignia "Alta Demanda" y despliegan el mensaje de aviso preventivo en el resumen del checkout.
- **SC-005 (Mantenibilidad Arquitectónica)**: La orden queda persistida en estado `PENDING_PAYMENT` con todos sus datos de franja validados, permitiendo que la futura integración del gateway de pagos en el Sprint 4 opere como un consumidor directo sin requerir migraciones de datos ni descarte de modelos.
- **SC-006 (Latencia de Refresco de Estado)**: Las consultas periódicas de sincronización de franjas horarias responden en menos de 100 ms en condiciones normales de red sin degradar el rendimiento del backend.

---

## Assumptions

- **Alineación con Sprint 2 (Spec 008)**: Se asume que el comensal ya cuenta con un establecimiento seleccionado y platos cargados en su `CartContext` con persistencia en sesión, conservando el `tenant_id` y las restricciones dietarias evaluadas en specs previas.
- **Capacidad por defecto**: Mientras no se complete la interfaz de administración `ADM-2.2.2`, los establecimientos activos operan con una capacidad predeterminada de 10 pedidos por franja de 15 minutos, configurable a nivel de entidad.
- **Zona horaria estándar**: Las operaciones se normalizan en la zona horaria del establecimiento (`America/Argentina/Buenos_Aires`).
- **Exclusión de Pagos y Stock ACID**: Se asume expresamente que el cobro electrónico bancario y el descuento atómico de ingredientes de la receta pertenecen al Sprint 4 (`COM-7.x` y `CHF-5.1.1`), concluyendo esta especificación en la reserva formal de la orden.

