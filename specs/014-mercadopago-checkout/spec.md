# Feature Specification: Pagar con Mercado Pago en checkout EasyEat

**Feature Branch**: `014-mercadopago-checkout`  
**Created**: 09/11/2026  
**Status**: Approved  
**Input**: User description: "Feature: Pagar con Mercado Pago en checkout EasyEat - Spec ID: 014"

---

## Contexto de Refactorización y Trazabilidad

- **Historia de Usuario Fuente**: COM-7.1 — Pagar con Mercado Pago.
- **Antecedentes de Refactorización**: Esta especificación representa una refactorización directa y unificación formal de las historias de usuario previas:
  - `COM-7.1.1` - Usar método guardado
  - `COM-7.1.2` - Cargar nueva tarjeta
  - `COM-7.1.3` - Opción de recordar pago
  La delegación del flujo de procesamiento hacia Mercado Pago absorbe y simplifica estas responsabilidades, evitando que el sitio del restaurante deba gestionar o interactuar con credenciales bancarias o datos sensibles de tarjetas.
- **Trazabilidad Funcional y Dependencias de Producto**:
  - Se articula de manera armónica sobre las capacidades ya operativas del producto: el carrito de compras del comensal (definido en Spec 009), la configuración horaria y de capacidades de atención (definida en Spec 010), el motor de selección de turnos de retiro en mostrador (definido en Spec 012) y las salvaguardas de disponibilidad y pausa de platos por cocina (definidas en Spec 013).
  - Aquellos aspectos relacionados con carritos con monto cero, transiciones en estado pendiente de resolución incierta en la pasarela externa, tratamientos ante aprobaciones tardías sobre reservas ya expiradas y re-selección de franja horaria ante falta de cupo quedan delegados formalmente a la historia complementaria COM-7.2.1.

---

## Clarifications

### Session 2026-11-09 (Grill-Me & Clarify)

- **Q1: Duración de la ventana de reserva temporal conjunta (TTL de stock y cupo de franja)**: ¿Cuál debe ser la duración de la ventana de la reserva temporal conjunta (TTL de stock y cupo) mientras el comensal completa el pago en Mercado Pago?  
  → **A1**: Se fija una ventana de duración de exactamente 15 minutos, alineada de forma estricta con el TTL de expiración de órdenes pendientes en checkout definido en Spec 012 (A6). Si transcurren los 15 minutos sin confirmación de pago ni retorno del comensal, la reserva temporal expira automáticamente, liberando el stock a la cocina y el cupo a la franja horaria.

- **Q2: Retorno tras rechazo o abandono y experiencia en checkout**: Al retornar al sitio tras un rechazo o un abandono en Mercado Pago, ¿cómo debe presentarse la pantalla de checkout para el reintento de pago?  
  → **A2**: El comensal aterriza directamente en la pantalla de checkout con el carrito de compras y la franja horaria preseleccionada preservados de forma intacta. Se exhibe una alerta visible explicativa ("El pago no se concretó" o "Operación cancelada") y se re-habilita el botón "Pagar", permitiendo al comensal reintentar de inmediato con un solo clic previa revalidación automática de la disponibilidad conjunta de stock y cupo.

- **Q3: Estado visual y acciones en checkout con pago en curso**: Si el comensal reingresa a la pantalla de checkout existiendo un pago en curso en Mercado Pago, ¿qué estado visual y acciones debe presentar el sistema?  
  → **A3**: El sistema despliega un panel informativo de "Pago en curso" sin cuenta regresiva en pantalla, manteniendo el botón "Pagar" estrictamente deshabilitado para impedir cobros o reservas duplicadas. Se ofrecen dos acciones explícitas: "Retomar pago" (para reabrir o volver a la pasarela externa de Mercado Pago) y "Cancelar intento" (acción voluntaria que da por terminado el intento actual y libera de forma inmediata la reserva temporal de franja horaria y stock, re-habilitando el checkout).

- **Q4: Vaciado del carrito local tras pago aprobado**: Tras la confirmación exitosa del pago aprobado en Mercado Pago, ¿cómo debe gestionarse el carrito de compras local del comensal?  
  → **A4**: El carrito de compras de la sesión local se vacía de manera automática e inmediata al confirmarse la orden y consolidarse la reserva en definitiva, garantizando que los ítems ya abonados no persistan en la sesión y dejando el catálogo y el carrito listos para futuros pedidos.

- **Q5: Estrategia de reproducibilidad y verificación en entorno de prueba (RT-1 y RT-3)**: ¿Cuál es la estrategia para asegurar la reproducibilidad determinística de los desenlaces de pago en el entorno de prueba requerida por RT-1 y RT-3?  
  → **A5**: Se adopta el uso de credenciales oficiales de Sandbox de Mercado Pago y las tarjetas de prueba documentadas por la plataforma que fuerzan de manera determinística cada resultado (aprobado, fondos insuficientes, sospecha de fraude), complementado con la simulación controlada de fallos de red/servicio para validar el fallo de inicio (AS-6), garantizando cobertura de punta a punta a costo cero e idéntico comportamiento funcional que con dinero real.

---

## Propósito

Brindar al comensal una vía confiable y segura para completar el pago de su orden a través del ecosistema de Mercado Pago, garantizando que el stock de cocina y la capacidad de la franja horaria queden temporalmente asegurados de forma conjunta durante el proceso de pago, convirtiéndose en definitivos únicamente tras la confirmación exitosa de la transacción, o liberándose inmediatamente ante cualquier interrupción, rechazo o abandono.

---

## Actores

- **Comensal**: Usuario final autenticado que ha conformado un pedido en su carrito y avanza en el checkout para concretar el pago y confirmar su orden.

---

## Escenarios de Aceptación y Pruebas

### User Story 1 - COM-7.1: Pagar con Mercado Pago (Prioridad: P1)

Como comensal,  
quiero pagar mi pedido con el medio de pago que prefiera en Mercado Pago, sin cargar datos de tarjeta en el sitio del restaurante EasyEat,  
para confirmar mi orden.

**Por qué esta prioridad**: Representa el canal esencial de conversión comercial y confirmación operativa del pedido dentro del checkout del restaurante. Sin este mecanismo, las órdenes no pueden ser procesadas ni pasar al circuito formal de elaboración.

**Prueba independiente**: Se puede verificar de punta a punta en un entorno de prueba iniciando el proceso de pago desde el checkout con un carrito con monto mayor a cero, constatando la reserva de stock y cupo, la redirección hacia Mercado Pago, y comprobando la correcta resolución en cada uno de los desenlaces posibles (aprobación, rechazo, abandono con y sin retorno, concurrencia de pago y fallo de inicio).

#### Escenarios de Aceptación

- **AS-1 (Criterio Fuente 1 - Inicio de pago y reserva temporal conjunta)**:  
  **Dado** que el comensal tiene un carrito con monto mayor a cero, y la franja horaria y el stock de la orden están disponibles,  
  **Cuando** presiona "Pagar",  
  **Entonces** el sistema reserva temporalmente la franja horaria y el stock de la orden de forma conjunta y lo redirige a Mercado Pago para completar el pago.

- **AS-2 (Criterio Fuente 2 - Pago aprobado y confirmación de orden)**:  
  **Dado** que el comensal completó el pago y Mercado Pago lo aprobó,  
  **Cuando** vuelve al sitio,  
  **Entonces** la reserva pasa a ser definitiva, el sistema le muestra el resultado del pago y continúa con la confirmación de la orden.

- **AS-2b (derivado de INV-02 e INV-08)**: 
  **Dado** que el comensal completó el pago y Mercado Pago lo aprobó, 
  **Cuando** el comensal no vuelve al sitio, 
  **Entonces** la reserva pasa a ser definitiva y la orden continúa con la confirmación.

- **AS-3 (Criterio Fuente 3 - Pago rechazado y liberación de recursos)**:  
  **Dado** que Mercado Pago rechazó el pago (por ejemplo, por fondos insuficientes o sospecha de fraude),  
  **Cuando** el comensal vuelve al sitio,  
  **Entonces** el sistema informa que el pago no se concretó, no avanza la orden, libera la reserva de franja horaria y stock y le permite intentar con otro medio.

- **AS-4 (Criterio Fuente 4 - Abandono con retorno al sitio)**:  
  **Dado** que el comensal abandona Mercado Pago sin pagar,  
  **Cuando** vuelve al sitio,  
  **Entonces** la orden permanece sin pagar, se libera la reserva de franja horaria y stock y el comensal puede reintentar el pago mientras el carrito, la franja horaria y el stock sigan disponibles.

- **AS-4b (Criterio Fuente 4b - Abandono sin retorno y vencimiento de la ventana)**:  
  **Dado** que el comensal abandona Mercado Pago sin pagar y no vuelve al sitio,  
  **Cuando** vence la ventana de la reserva temporal,  
  **Entonces** la reserva se libera automáticamente y la orden permanece sin pagar.

- **AS-5 (Criterio Fuente 5 - Prevención de cobro y reserva duplicada ante pago en curso)**:  
  **Dado** que el comensal ya tiene un pago en curso para la misma orden,  
  **Cuando** presiona "Pagar" nuevamente,  
  **Entonces** el sistema no genera un segundo cobro ni una segunda reserva.

- **AS-6 (Criterio Fuente 6 - Fallo en el inicio del pago y reversión de reservas)**:  
  **Dado** que el sistema no logra iniciar el pago con Mercado Pago,  
  **Cuando** el comensal presiona "Pagar",  
  **Entonces** el sistema le avisa que el pago no pudo iniciarse, no genera cobro y libera cualquier reserva de franja horaria y stock que se hubiera realizado.

---

## Requisitos Funcionales

- **FR-001**: El sistema debe reservar temporalmente y de forma indivisible la capacidad de la franja horaria y el stock de los productos asociados a la orden únicamente cuando el carrito posea un monto mayor a cero y se haya validado la disponibilidad de ambos recursos.
- **FR-002**: El sistema debe redirigir al comensal a la interfaz externa de Mercado Pago inmediatamente después de haber consolidado con éxito la reserva temporal conjunta.
- **FR-003**: El sistema debe convertir la reserva temporal en definitiva al reconocerse la aprobación del pago por parte de Mercado Pago, haya o no vuelto el comensal al sitio, sin requerir una nueva evaluación de disponibilidad de stock o cupo.
- **FR-004**: El sistema debe mostrar el resultado aprobado al comensal tras el retorno de Mercado Pago, dar curso inmediato al flujo de confirmación de la orden y vaciar de forma automática el carrito de compras local de la sesión.
- **FR-005**: El sistema debe notificar al comensal que el pago no se concretó cuando Mercado Pago informe un rechazo, garantizando que la orden no avance de estado y liberando en ese mismo acto la reserva de franja horaria y stock.
- **FR-006**: El sistema debe permitir al comensal iniciar un nuevo intento de pago desde el sitio tras un rechazo, preservando intactos el carrito y la franja horaria en la pantalla de checkout y mostrando una alerta explicativa, procediendo a una nueva reserva si los recursos siguen disponibles al presionar "Pagar".
- **FR-007**: El sistema debe mantener la orden en estado no pagada y liberar de inmediato la reserva de stock y franja horaria cuando el comensal retorne voluntariamente al sitio habiendo abandonado Mercado Pago sin completar la transacción.
- **FR-008**: El sistema debe permitir al comensal reintentar el pago tras un abandono con retorno manteniendo el carrito y la franja horaria preseleccionada en el checkout, re-habilitando el botón "Pagar" para generar una nueva reserva siempre que los recursos continúen disponibles al efectuar la acción.
- **FR-009**: El sistema debe liberar de manera automática la reserva de franja horaria y stock cuando venza la ventana temporal de reserva de 15 minutos sin que se haya registrado una aprobación del pago, dejando la orden en estado no pagada.
- **FR-010**: El sistema debe impedir la generación de una nueva reserva o un segundo cobro si el comensal interactúa con el checkout existiendo ya un pago en curso para esa misma orden, presentando un panel informativo de "Pago en curso" con el botón "Pagar" deshabilitado, y disponiendo de acciones explícitas para "Retomar pago" o "Cancelar intento" (liberando de inmediato la reserva de recursos).
- **FR-011**: El sistema debe informar al comensal la imposibilidad de iniciar el proceso, no generar cobro y cancelar o restituir cualquier reserva temporal de stock o franja horaria que se hubiera iniciado, si se produce un fallo al intentar establecer la comunicación inicial con Mercado Pago.
- **FR-012**: El sistema debe procesar los retornos y las notificaciones de resultado bajo una semántica estrictamente idempotente, de modo que eventos idénticos o repetidos sobre una misma orden no generen alteraciones sucesivas sobre el inventario ni sobre el cupo de la franja.
- **FR-013**: El sistema no debe recibir, solicitar, almacenar ni exhibir datos de tarjetas de crédito o débito en ninguna pantalla o formulario del sitio del restaurante EasyEat.

---

## Restricciones Transversales

- **RT-1 Costo cero verificable**: Todos los escenarios de aceptación de esta especificación deben poder ejecutarse y verificarse de punta a punta en un entorno de prueba, sin emplear dinero real y sin generar costos para el equipo ni para el restaurante.
- **RT-2 Equivalencia con dinero real**: El comportamiento funcional del sistema debe ser rigurosamente idéntico tanto en el entorno de prueba como con dinero real; la transición desde el entorno de prueba hacia la operación con dinero real no debe requerir modificación alguna en las reglas de negocio ni en el comportamiento funcional aquí especificado.
- **RT-3 Reproducibilidad de resultados**: Los desenlaces de pago correspondientes a aprobación, rechazo por fondos o sospecha de fraude, abandono, vencimiento de la ventana de reserva temporal y fallo en la inicialización deben poder reproducirse de forma controlada y determinística dentro del entorno de prueba mediante el uso de credenciales de Sandbox y las tarjetas de prueba documentadas por Mercado Pago, junto con la simulación controlada de indisponibilidad de red para el fallo de inicio.

---

## Validaciones e Invariantes de Negocio

- **INV-01 (Reserva atómica conjunta)**: Al presionar "Pagar" con carrito mayor a cero y franja y stock disponibles, la franja horaria y el stock de la orden se reservan temporalmente bajo un principio de indivisibilidad: se reservan ambos recursos conjuntamente o no se reserva ninguno.
- **INV-02 (Confirmación exclusiva por aprobación)**: La reserva temporal pasa a ser definitiva únicamente cuando el pago es aprobado formalmente por Mercado Pago; dicha aprobación no requiere ni admite una nueva verificación de disponibilidad de recursos.
- **INV-03 (Restitución integral de recursos)**: La reserva temporal se libera en su totalidad, reintegrando el stock a la cocina y el cupo a la franja horaria, ante cualquier escenario de rechazo, cancelación, abandono, fallo de inicio o vencimiento de la ventana de reserva.
- **INV-04 (No retención indefinida)**: Ninguna reserva temporal puede permanecer retenida de forma indefinida; toda reserva cuenta con una ventana de vigencia temporal de 15 minutos desde su creación.
- **INV-05 (Unicidad de pago en curso por orden)**: Una orden admite como máximo una reserva temporal y un pago en curso simultáneos; reiterar la acción de pago no crea una reserva paralela ni genera cobros duplicados.
- **INV-06 (Idempotencia de estado de reserva)**: Una reserva temporal se libera o se confirma de manera definitiva exactamente una sola vez; la recepción repetida de notificaciones o de retornos de un mismo resultado no debe alterar nuevamente el stock ni el cupo asignado.
- **INV-07 (Resolución de colisiones concurrentes)**: Si dos comensales intentan reservar el último cupo disponible de una franja o la última unidad de stock de un plato, únicamente el primero que complete la validación obtiene la reserva temporal; el comensal restante es bloqueado antes de iniciar el cobro.
- **INV-08 (Autoridad externa sobre el resultado)**: El resultado registrado formalmente en Mercado Pago es la única entidad determinante para que la orden avance a su confirmación; ninguna acción o indicación originada exclusivamente en la interfaz gráfica del usuario puede dar por aprobado un pago.
- **INV-09 (Bloqueo de avance sin cobro)**: Un pago rechazado, abandonado o que no haya logrado iniciarse jamás avanza la orden al estado confirmada ni genera cobro alguno al comensal.
- **INV-10 (Integridad del estado del pedido)**: No se permite persistir órdenes como confirmadas ni registrar cobros completados sin la justificación fehaciente del resultado aprobado de la pasarela.
- **INV-11 (Aislamiento de datos de tarjeta)**: El sistema nunca solicita, visualiza, procesa ni almacena números de tarjeta, códigos de seguridad o datos financieros sensibles en los sistemas de EasyEat.

---

## Comportamiento ante Errores y Casos Borde

- **CB-01 Pago aprobado exitosamente**: Mercado Pago aprueba el monto total; el comensal retorna al sitio, la reserva temporal se afianza en definitiva, se vacía automáticamente el carrito de compras local de la sesión, se presenta el mensaje de confirmación y la orden continúa su ciclo.
- **CB-02 Pago rechazado por fondos insuficientes o prevención de fraude**: Mercado Pago deniega la transacción; el comensal regresa al sitio aterrizando en la pantalla de checkout con su carrito y franja horaria preservados y un mensaje explicativo visible; la orden no avanza, la reserva de stock y cupo se libera de inmediato y el botón "Pagar" se re-habilita para permitir reintentar con otro medio.
- **CB-03 Abandono de la pasarela con retorno voluntario**: El comensal cancela el proceso o vuelve a EasyEat sin pagar; la orden se mantiene no pagada, aterriza en el checkout con el carrito y la franja intactos, la reserva se restituye y el usuario puede reintentar con un solo clic mientras los ítems y la franja sigan disponibles.
- **CB-04 Abandono de la pasarela sin retorno (vencimiento de ventana)**: El comensal cierra el navegador o interrumpe la conexión en Mercado Pago sin haber pagado y nunca vuelve al sitio; al expirar la ventana de la reserva temporal (15 minutos), el sistema libera automáticamente el stock y la franja horaria.
- **CB-04b Pago aprobado sin retorno del comensal**: Mercado Pago aprueba el pago pero el comensal no vuelve al sitio; la aprobación se reconoce igualmente, la reserva pasa a definitiva y la orden continúa su confirmación. Los efectos posteriores (comprobante y seguimiento) se especifican en COM-7.3.1 y COM-7.3.2.
- **CB-05 Presión múltiple o reingreso al checkout con pago en curso**: Con un pago en curso en Mercado Pago, el checkout muestra el panel informativo de "Pago en curso" sin cuenta regresiva en pantalla y con el botón "Pagar" deshabilitado para neutralizar intentos simultáneos de cobro o reserva. El comensal dispone de las opciones para "Retomar pago" o "Cancelar intento" (lo que libera inmediatamente la reserva y restituye el checkout habilitado).
- **CB-06 Fallo en el inicio del pago**: Si la comunicación inicial con Mercado Pago no se puede entablar (por ejemplo por indisponibilidad de servicio o problemas de enlace), se notifica de inmediato al comensal, no se aplica ningún cobro y cualquier reserva iniciada se cancela instantáneamente.
- **CB-07 Notificaciones o retornos repetidos del mismo desenlace**: La llegada reiterada de la confirmación de un mismo pago (por ejemplo, múltiples reenvíos de confirmación o recargas de la página de retorno) procesa la transición una sola vez, manteniendo el stock y el cupo invariables tras la primera confirmación o liberación.
- **CB-08 Reintento tras reserva liberada**: Si un comensal decide reintentar el pago luego de un abandono o rechazo previo, el sistema realiza una nueva verificación integral: si el carrito, la franja y el stock continúan disponibles, se establece una nueva reserva temporal y se redirige nuevamente.
- **CB-09 Disponibilidad asimétrica de recursos (stock disponible sin cupo de franja, o viceversa)**: Si hay stock de cocina pero la franja horaria se encuentra saturada, o si la franja tiene cupo pero se agotó un plato del carrito, el sistema no reserva ninguno de los recursos y no da paso a la redirección.
- **CB-10 Concurrencia sobre el último recurso disponible**: Si dos comensales compiten por la última unidad o cupo y presionan "Pagar" casi simultáneamente, solo aquel que consolide primero la reserva temporal avanza a Mercado Pago. El segundo comensal es informado de la indisponibilidad sin generarse reserva ni cobro, conforme a lo establecido en COM-7.2.1.
- **CB-11 Franja horaria o stock no disponibles al momento de presionar "Pagar"**: El comensal es notificado de la falta de disponibilidad en el checkout y se le impide la redirección; los mecanismos visuales de aviso y la posterior re-selección de turno o ajuste de platos son especificados en COM-7.2.1.
- **CB-12 Aprobación de pago posterior a la expiración de la reserva**: Si una confirmación de pago aprobada llegara luego de haberse vencido y liberado la ventana de reserva temporal, su tratamiento de contingencia y resolución operativa quedan diferidos a COM-7.2.1.
- **CB-13 Retorno al sitio con pago en estado pendiente de resolución**: Si el comensal vuelve al sitio sin que Mercado Pago haya emitido una definición terminal (pago pendiente de acreditación), su tratamiento y visualización se definen en COM-7.2.1.

---

## Entidades Clave de Negocio

- **Orden**: Representa la solicitud de compra conformada por el comensal a partir de los productos seleccionados en su carrito, con su monto total correspondiente y su franja horaria de retiro asignada.
- **Reserva Temporal**: Bloqueo provisorio e indivisible de la capacidad de la franja horaria y del stock de cocina asociado a una orden, vigente únicamente durante una ventana acotada de tiempo mientras el comensal interactúa con la pasarela de pago.
- **Pago**: Registro del intento transaccional gestionado a través de Mercado Pago, cuyo resultado determina de forma excluyente la confirmación de la orden y la suerte de la reserva temporal (definitiva si es aprobado, liberada si es rechazado o cancelado).

---

## Supuestos

- Se asume la preexistencia funcional del carrito de compras, la orden comercial, la franja horaria de entrega y el control de inventario de stock en el producto actual.
- La entidad de reserva temporal conjunta y la integración del resultado externo de pago son introducidas a partir de la presente especificación y su complemento COM-7.2.1.
- La extensión de la ventana temporal de reserva es de exactamente 15 minutos, en estricta consonancia con el TTL de expiración de órdenes pendientes en checkout de Spec 012; un pago está "en curso" desde que el comensal es redirigido a Mercado Pago hasta que se conoce un resultado final o vencen esos 15 minutos.
- La posibilidad de "intentar con otro medio" implica reiniciar el proceso de pago desde el sitio de EasyEat.
- Ante cualquier incertidumbre o falta de respuesta terminal en la comunicación con Mercado Pago, el sistema jamás considerará el pago como aprobado hasta obtener confirmación explícita.

---

## Fuera de Alcance

- Integración o soporte de otros proveedores o pasarelas de pago alternativos fuera de Mercado Pago.
- Captura, ingreso o validación de números de tarjeta en formularios dentro del sitio de EasyEat.
- Almacenamiento, guardado, listado o eliminación de tarjetas o credenciales financieras por parte de EasyEat.
- Mecanismos de devolución de importes, reembolsos, conciliación contable, liquidación bancaria, conversión de monedas, financiación en cuotas o promociones de descuento.
- Autenticación, control de permisos de acceso o funciones administrativas de gestión de pagos.
- Casos especiales de carritos con monto cero (especificados en COM-7.2.1).
- Tratamiento del estado de pago pendiente y resoluciones tardías posteriores al vencimiento de la reserva (especificados en COM-7.2.1).
- Acciones posteriores a la confirmación exitosa de la orden (tales como la deducción física definitiva en el sistema de cocina, despacho de comanda al KDS, generación de comprobantes fiscales o seguimiento logístico), las cuales son abordadas en especificaciones de historias posteriores.

---

## Criterios de Éxito

- **SC-001**: El 100% de los intentos de pago en los que el carrito tenga monto mayor a cero, la franja y el stock estén disponibles y el enlace con Mercado Pago sea exitoso, generan la reserva temporal conjunta y redirigen al comensal a la pasarela.
- **SC-002**: El 100% de los pagos aprobados por Mercado Pago confirman definitivamente la reserva de franja y stock, exhiben el resultado al comensal y avanzan de inmediato con la confirmación de la orden, incluso cuando el comensal no vuelve al sitio.
- **SC-003**: El 100% de los rechazos informados, abandonos voluntarios y fallos de comunicación en el inicio devuelven íntegramente la capacidad a la franja horaria y el stock a la cocina.
- **SC-004**: Cero (0) reservas temporales permanecen retenidas una vez vencida su ventana temporal de vigencia.
- **SC-005**: Cero (0) sobreventas de cupos en franjas horarias y cero (0) sobreventas de stock físico de platos permitidas por el sistema.
- **SC-006**: Cero (0) órdenes avanzan a estado confirmada ante pagos rechazados, abandonados o no iniciados.
- **SC-007**: Cero (0) cobros duplicados y cero (0) reservas duplicadas permitidas al detectarse un pago ya en curso para la misma orden.
- **SC-008**: Cero (0) alteraciones en el stock o en el cupo frente a eventos o retornos notificados de forma repetida sobre una misma transacción.
- **SC-009**: Cero (0) incidentes en que datos de tarjetas bancarias sean solicitados, almacenados o visualizados dentro del sitio del restaurante EasyEat.
- **SC-010**: El 100% de los escenarios de aceptación son ejecutables y verificables de extremo a extremo en un entorno de prueba a costo cero (RT-1), evidenciando cero (0) diferencias en su comportamiento funcional respecto de una operación con dinero real (RT-2).

