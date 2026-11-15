# Especificación funcional: protocolo antifraude de entrega y cierre de orden

**Identificador de especificación**: `022-pin-antifraud-delivery-completion`  
**Rama de trabajo**: `022-pin-antifraud-delivery-completion`  
**Fecha de creación**: 15/11/2026  
**Estado**: Draft  
**Entrada**: Requerimientos funcionales derivados de las historias de usuario `ENC-3.2.1`, `ENC-3.3.1` y `ENC-3.3.2`.

---

## Contexto y objetivo

El ciclo de vida de un pedido gastronómico culmina con la entrega material de los alimentos elaborados al cliente que realizó la compra. En un entorno de alto tránsito y retiro presencial por mostrador, garantizar que cada pedido sea entregado a su comensal legítimo sin recurrir a hardware óptico complejo (escáneres de códigos de barras o lectores QR dedicados) requiere un protocolo antifraude ágil, de verificación oral humana y costo operativo nulo.

La presente especificación establece el comportamiento funcional observable, las reglas de negocio deterministas, los invariantes transaccionales y los criterios de aceptación para el cierre definitivo de la orden dentro del sistema. Se monta de manera armónica sobre la tarjeta desplegada de mostrador construida en la especificación 020, sin redefinir su anatomía visual básica ni su mecánica de colapso/despliegue, y coordina el estado terminal tanto en el puesto de mostrador como en la pantalla de seguimiento del cliente (establecida en las especificaciones 016 y 021).

Los objetivos funcionales centrales de esta especificación son:
1. **Protocolo antifraude de verificación oral (`ENC-3.2.1`)**: Proveer un código numérico de seguridad aleatorio de 4 dígitos (rango 1000 a 9999) generado al registrar y pagar la orden, accesible para el cliente en su pantalla de seguimiento y exhibido de forma destacada en la tarjeta desplegada de mostrador para su contraste visual y verbal directo en ventanilla.
2. **Confirmación visual y protección de entrega (`ENC-3.3.1`)**: Habilitar en la tarjeta desplegada el botón "ENTREGAR PEDIDO", el cual, al ser accionado por el personal de mostrador tras validar la coincidencia verbal, exhibe de forma inmediata una confirmación visual en color verde con la leyenda "Entrega Confirmada" y se deshabilita instantáneamente para inmunizar la operación contra pulsaciones accidentales o dobles clics.
3. **Archivado atómico, idempotencia y cierre de ciclo (`ENC-3.3.2`)**: Ejecutar el archivado terminal del pedido actualizando de manera atómica e indivisible el estado de la orden y de su comanda de cocina asociada a "completada", registrando el instante exacto de entrega, removiendo la tarjeta de la vista activa de despacho en mostrador, garantizando respuestas idempotentes ante reintentos de red, y conmutando la pantalla de seguimiento del comensal a su estado de cierre definitivo con el mensaje "Pedido Entregado — ¡Que disfrutes tu comida!" y el resumen de compra.

Esta especificación es 100% agnóstica a la tecnología, no define endpoints, esquemas de bases de datos, librerías, clases de estilo ni componentes técnicos, y se enfoca exclusivamente en reglas de negocio y comportamiento observable.

---

## Clarifications

### Sesión 15/11/2026

- Q: ¿El sistema realiza alguna validación automatizada o algorítmica sobre el código numérico de seguridad al momento de presionar "ENTREGAR PEDIDO"? → A: No. El protocolo antifraude se basa estrictamente en el cotejo oral y visual humano realizado por el encargado de mostrador en ventanilla. El sistema no provee campo de texto para digitar el código ni efectúa comparación algorítmica previa; el encargado escucha el código recitado por el comensal, lo contrasta visualmente con el código exhibido en la tarjeta desplegada y pulsa el botón "ENTREGAR PEDIDO".
- Q: ¿Qué sucede con el código de seguridad a lo largo del tiempo de vida de la orden? → A: El código numérico de seguridad de 4 dígitos es estrictamente inmutable: se genera aleatoriamente en el rango de 1000 a 9999 al momento de confirmarse y pagarse la orden, y permanece idéntico sin posibilidad de regeneración, rotación, modificación ni expiración durante todo el ciclo de vida del pedido.
- Q: ¿Cómo se comporta la interfaz de mostrador entre la pulsación del botón "ENTREGAR PEDIDO" y la confirmación efectiva del archivado terminal? → A: En el instante de la pulsación, el botón se deshabilita de inmediato para evitar pulsaciones múltiples y la tarjeta despliega una confirmación visual en color verde con la leyenda "Entrega Confirmada". Una vez procesada la respuesta exitosa del archivado, la tarjeta se retira de la vista activa de despacho de mostrador sin alterar el estado de despliegue de las restantes tarjetas abiertas.
- Q: ¿Qué sucede si la terminal de mostrador emite solicitudes repetidas de entrega para una orden ya completada (por reintentos automáticos de red o reanudación tras corte de conexión)? → A: El sistema opera de manera estrictamente idempotente: toda solicitud de entrega sobre una orden ya completada responde afirmativamente con éxito, sin mutar datos, sin sobrescribir el instante de entrega original y sin duplicar efectos secundarios.
- Q: ¿Qué muestra la pantalla de seguimiento del comensal una vez que la orden transiciona a estado "completada"? → A: La pantalla reemplaza los paneles transitorios de preparación y aviso de retiro para exhibir de forma permanente y definitiva el mensaje "Pedido Entregado — ¡Que disfrutes tu comida!" acompañado del resumen de compra de la orden, cesando las sincronizaciones periódicas de estado al haber alcanzado la orden su estado terminal inmutable.
- Q: ¿Qué ocurre si ocurre una falla irrecuperable de conectividad en mostrador justo al pulsar "ENTREGAR PEDIDO"? → A: Si la solicitud de cierre no logra comunicarse con el servidor o resulta en un fallo transitorio de enlace, la interfaz muestra un aviso no intrusivo de error de comunicación, reestablece el botón para permitir un reintento manual y conserva la tarjeta en la vista activa de despacho.

---

## Personas

1. **Encargado de mostrador que verifica identidad del comensal**: Personal de atención en ventanilla que, al recibir al cliente que se presenta a retirar su comida, despliega la tarjeta correspondiente al pedido y escucha atentamente el código numérico de 4 dígitos recitado de forma oral por el comensal para contrastarlo con el código exhibido en pantalla.
2. **Encargado de mostrador que valida coincidencia y ejecuta la entrega**: Operador que, habiendo constatado la coincidencia exacta entre el código oral provisto y el código exhibido, acciona el botón "ENTREGAR PEDIDO" para registrar la entrega física con total certeza operativa y sin requerir tipeos mecánicos.
3. **Encargado de mostrador que archiva definitivamente el pedido entregado**: Personal de mostrador que requiere que, una vez concretada la entrega, la tarjeta desaparezca limpiamente de su tablero de pedidos activos para focalizar su atención únicamente en las órdenes que aún aguardan retiro.
4. **Comensal que recita su código de seguridad y consulta su pantalla de seguimiento**: Cliente que acude a la ventanilla con el código numérico de seguridad visible en su teléfono celular, lo recita verbalmente al encargado para acreditar su titularidad y posteriormente visualiza en su pantalla la confirmación definitiva de entrega junto con el resumen de su compra.
5. **Sistema como custodio de la integridad del cierre**: Componente operativo y transaccional que asegura la atomicidad indivisible del cambio de estado entre orden y ticket, preserva la inmutabilidad de la entrega, garantiza la idempotencia ante reintentos y mantiene sincronizados los tableros de mostrador y seguimiento.

---

## Historias de usuario y escenarios de aceptación

### Historia de usuario 1 — Código numérico de seguridad de retiro oral (Protocolo Antifraude) (Prioridad: P1)

**Identificador de referencia**: `ENC-3.2.1`  
**Personas**: Comensal que recita su código de seguridad y consulta su pantalla de seguimiento / Encargado de mostrador que verifica identidad del comensal.

Como encargado de mostrador, quiero cotejar visualmente en la tarjeta desplegada el PIN numérico de 4 dígitos que el comensal me recita de forma oral, para garantizar que le entrego el pedido a su dueño legítimo sin requerir lectores ópticos ni escáneres QR.

**Por qué tiene prioridad P1**: Constituye la salvaguarda de seguridad esencial que previene el retiro fraudulento o la suplantación de pedidos en ventanilla. Permite validar la titularidad de la orden con fricción cero y sin depender de accesorios de hardware adicionales.

**Prueba independiente**: Se puede verificar generando una orden confirmada y pagada: el sistema almacena un código de 4 dígitos en el rango de 1000 a 9999 y lo presenta en la pantalla de seguimiento del comensal. Al acceder a la pantalla de mostrador y desplegar la tarjeta correspondiente a dicha orden, se observa el mismo código destacado prominentemente, permitiendo contrastar visualmente el valor sin intervención de escáneres ópticos.

**Escenarios de aceptación**:

1. **AC-1 (Generación y disponibilidad del código numérico de seguridad)**:
   - **Dado** que un comensal confirma y paga un pedido,
   - **Cuando** la orden queda registrada en el sistema,
   - **Entonces** el sistema genera y almacena un código numérico de seguridad aleatorio de 4 dígitos, con rango comprendido entre 1000 y 9999 inclusive, manteniéndolo accesible y visible en la pantalla de seguimiento del comensal.

2. **AC-2 (Cotejo visual y oral humano en ventanilla)**:
   - **Dado** que un comensal se presenta en el mostrador y recita verbalmente su código de seguridad de 4 dígitos,
   - **Cuando** el encargado de mostrador despliega la tarjeta correspondiente a dicho pedido,
   - **Entonces** visualiza de forma clara y destacada el código numérico de seguridad almacenado para contrastarlo directamente con el código oral provisto por el comensal.

---

### Historia de usuario 2 — Marcar pedido como "Entregado" y archivar ciclo de orden (Prioridad: P1)

**Identificador de referencia**: `ENC-3.3.2`  
**Personas**: Encargado de mostrador que archiva definitivamente el pedido entregado / Comensal que recita su código de seguridad y consulta su pantalla de seguimiento / Sistema como custodio de la integridad del cierre.

Como encargado de mostrador, quiero que el sistema archive definitivamente el pedido en la base de datos al entregarlo, para retirarlo de mi tablero activo, completar la transacción y actualizar el estado final en la app del comensal.

**Por qué tiene prioridad P1**: Representa el cierre transaccional definitivo del ciclo de vida del pedido. Sin esta acción, las órdenes listas permanecerían de manera indefinida en el tablero de mostrador, impidiendo la finalización operativa y dejando la experiencia del comensal inconclusa.

**Prueba independiente**: Se puede verificar tomando una orden en estado "listo para retirar" y ejecutando la acción de entrega desde la tarjeta desplegada de mostrador: de forma atómica la orden y su ticket de cocina pasan al estado "completada" con su instante de cierre registrado; la tarjeta es removida de la vista activa de despacho; solicitudes de entrega subsiguientes para la misma orden responden exitosamente sin producir cambios colaterales; y la pantalla del comensal se actualiza para mostrar el mensaje de entrega finalizada y el resumen de compra.

**Escenarios de aceptación**:

1. **AC-1 (Transición atómica e indivisible al estado completado)**:
   - **Dado** un pedido en estado "listo para retirar",
   - **Cuando** se confirma la entrega desde la tarjeta de mostrador,
   - **Entonces** el sistema actualiza de forma atómica e indivisible el estado de la orden a "completada", el estado del ticket de cocina asociado a "completada", y registra el instante exacto de cierre como el momento de la entrega confirmada, asegurando que o bien se aplican todas estas actualizaciones en su totalidad, o bien no se modifica ningún dato del sistema.

2. **AC-2 (Remoción de mostrador e idempotencia operativa)**:
   - **Dado** que la entrega del pedido fue confirmada,
   - **Cuando** la vista de mostrador procesa la confirmación afirmativa del sistema,
   - **Entonces** la tarjeta correspondiente desaparece de la vista activa de despacho de pedidos listos; y si el sistema recibe solicitudes de cierre repetidas o duplicadas para una orden ya completada, responde exitosamente sin alterar datos preexistentes ni disparar efectos colaterales adicionales.

3. **AC-3 (Conclusión definitiva en la pantalla de seguimiento del comensal)**:
   - **Dado** que la orden transitó al estado "completada",
   - **Cuando** el comensal consulta o sincroniza su pantalla de seguimiento,
   - **Entonces** visualiza de forma permanente el mensaje definitivo "Pedido Entregado — ¡Que disfrutes tu comida!" junto con el resumen íntegro de su compra, concluyendo exitosamente el ciclo de vida de la orden.

---

### Historia de usuario 3 — Confirmación de coincidencia y acción directa de entrega (Prioridad: P2)

**Identificador de referencia**: `ENC-3.3.1`  
**Persona**: Encargado de mostrador que valida coincidencia y ejecuta la entrega.

Como encargado de mostrador que validó la identidad del comensal, quiero pulsar un botón directo "ENTREGAR PEDIDO" en la tarjeta desplegada y ver un distintivo verde de confirmación, para registrar la entrega sin tipeos mecánicos repetitivos y con total certeza operativa.

**Por qué tiene prioridad P2**: Agrega refinamiento ergonómico y seguridad visual en la estación de entrega. Provee retroalimentación sensorial inmediata al operador para confirmar la acción antes de que la tarjeta se descarte y blinda la interfaz contra interacciones no deseadas o pulsaciones duplicadas por reflejo motriz.

**Prueba independiente**: Se puede comprobar desplegando una tarjeta en mostrador y pulsando el botón "ENTREGAR PEDIDO": el botón queda deshabilitado en el mismo instante del clic impidiendo pulsaciones posteriores, e inmediatamente se exhibe en la tarjeta un distintivo verde con el texto "Entrega Confirmada" mientras se tramita el archivado terminal.

**Escenarios de aceptación**:

1. **AC-1 (Confirmación visual inmediata con distintivo verde)**:
   - **Dado** que el encargado validó visualmente la coincidencia del código oral de 4 dígitos,
   - **Cuando** pulsa el botón "ENTREGAR PEDIDO" en la tarjeta desplegada,
   - **Entonces** la tarjeta exhibe de forma inmediata una confirmación visual en color verde con la leyenda "Entrega Confirmada" e inicia de inmediato el archivado terminal de la orden.

2. **AC-2 (Protección instantánea contra doble clic)**:
   - **Dado** que se pulsa el botón "ENTREGAR PEDIDO",
   - **Cuando** se dispara la solicitud de cierre de la orden,
   - **Entonces** el botón se deshabilita de manera instantánea e irreversible en la interfaz, evitando duplicaciones de peticiones o errores derivados de clics múltiples o repetitivos.

---

## Requisitos funcionales

- **RF-001**: El sistema DEBE generar un código numérico de seguridad aleatorio compuesto exactamente por 4 dígitos dentro del rango cerrado de 1000 a 9999 inclusive para cada orden al momento de confirmarse y pagarse el pedido.
- **RF-002**: El código numérico de seguridad DEBE permanecer inmutable, estable y sin alteraciones durante todo el ciclo de vida de la orden, prohibiéndose su regeneración, recálculo, reemplazo o expiración anticipada.
- **RF-003**: La pantalla de seguimiento del comensal DEBE exhibir el código numérico de seguridad de 4 dígitos correspondiente a la orden para que el cliente pueda consultarlo y recitarlo verbalmente en mostrador.
- **RF-004**: La tarjeta desplegada de mostrador DEBE mostrar de forma prominente y legible a simple vista el código numérico de seguridad de 4 dígitos almacenado en la orden para facilitar el contraste oral humano por parte del operador.
- **RF-005**: El sistema NO DEBE exigir lectores ópticos, escáneres de código de barras, lectores de códigos QR ni dispositivos externos auxiliares para validar la entrega en mostrador.
- **RF-006**: La tarjeta desplegada de mostrador DEBE incorporar un control de acción directa identificado unívocamente con el rótulo "ENTREGAR PEDIDO".
- **RF-007**: Al pulsarse el botón "ENTREGAR PEDIDO", la interfaz DEBE deshabilitar dicho botón de forma instantánea, impidiendo cualquier interacción posterior o envío múltiple derivado de clics consecutivos.
- **RF-008**: Al pulsarse el botón "ENTREGAR PEDIDO", la tarjeta desplegada DEBE exhibir de forma inmediata una insignia o elemento de retroalimentación visual en color verde con el texto explícito "Entrega Confirmada".
- **RF-009**: La acción de confirmación de entrega DEBE desencadenar una operación de cierre atómica e indivisible que transicione de forma simultánea el estado de la orden a "completada" y el estado del ticket de cocina asociado a "completada".
- **RF-010**: La operación de cierre DEBE registrar el instante exacto en que la entrega quedó confirmada como la marca temporal de cierre del pedido (`completed_at`).
- **RF-011**: Si cualquier porción de la operación de cierre falla, el sistema DEBE asegurar la indivisibilidad transaccional, dejando la orden y el ticket de cocina exactamente en el estado previo a la solicitud sin registrar cierres parciales.
- **RF-012**: Una vez procesada la respuesta afirmativa de cierre, la tarjeta del pedido entregado DEBE removerse de inmediato y de forma automática de la vista activa de despacho en la pantalla de mostrador.
- **RF-013**: La remoción de una tarjeta entregada en mostrador NO DEBE alterar, colapsar ni modificar el estado de despliegue de las demás tarjetas que el personal mantenga abiertas en la pantalla.
- **RF-014**: El proceso de cierre terminal DEBE ser deterministamente idempotente: cualquier solicitud subsiguiente de cierre recibida para una orden que ya ostenta el estado "completada" DEBE responder con éxito afirmativo, sin alterar los datos existentes, sin modificar el instante original de cierre registrado y sin duplicar efectos en el sistema.
- **RF-015**: Al transicionar la orden al estado "completada", la pantalla de seguimiento del comensal DEBE conmutar a la vista definitiva de cierre, exhibiendo el mensaje "Pedido Entregado — ¡Que disfrutes tu comida!" y el resumen de compra.
- **RF-016**: La vista definitiva de cierre en la pantalla de seguimiento del comensal DEBE ser terminal y permanente, cesando los sondeos periódicos automáticos de actualización al haber finalizado el ciclo operativo de la orden.
- **RF-017**: En caso de error de comunicación o fallo de enlace de red al accionar "ENTREGAR PEDIDO", la interfaz de mostrador DEBE notificar la incidencia mediante un aviso no intrusivo, reestablecer la interactividad del botón de entrega y conservar la tarjeta en la vista activa de despacho para admitir un reintento manual.

---

## Invariantes y reglas deterministas

1. **Rango numérico estricto del código de seguridad**: Todo código de seguridad generado satisface la condición matemática $1000 \le \text{código} \le 9999$. No existen códigos de menos de 4 dígitos, con ceros a la izquierda, ni de más de 4 dígitos.
2. **Inmutabilidad y estabilidad del código de seguridad**: Una vez asignado el código de seguridad a una orden confirmada, dicho código permanece invariable hasta el archivado terminal y no puede ser recalculado, reseteado ni regenerado.
3. **Cotejo oral humano**: La verificación de identidad es un acto estrictamente sensorial y operativo ejecutado por el personal de mostrador. El sistema no intercepta la voz ni compara cadenas de texto tipeadas por el operador en ventanilla.
4. **Atomicidad e indivisibilidad del cierre terminal**: El pasaje a estado "completada" de la orden y a "completada" del ticket de cocina asociado, junto con el registro del instante de entrega, constituyen una única unidad indivisible: o se aplican todos los cambios en conjunto, o no se aplica ninguno.
5. **Irreversibilidad del estado completado**: El estado "completada" es terminal. Una orden en estado "completada" no puede revertirse a "listo para retirar", "en preparación", "en cola" ni admitir reaperturas o cancelaciones.
6. **Inmutabilidad del instante de cierre**: El instante temporal registrado en la primera confirmación exitosa de entrega es inmutable. Solicitudes idempotentes posteriores jamás actualizan ni sobreescriben este valor.
7. **Idempotencia transaccional absoluta**: La recepción de $N$ solicitudes de cierre idénticas para una misma orden culmina en el mismo resultado exacto que recibir una sola solicitud, respondiendo afirmativamente y sin duplicar registros de auditoría ni alterar estados.
8. **Protección anti doble clic en cliente**: Una vez accionado el control "ENTREGAR PEDIDO", su estado pasa inmediatamente a inhabilitado, impidiendo eventos de clic adicionales durante el procesamiento de la solicitud.
9. **Independencia del estado de visualización en mostrador**: El archivado y desaparición de una tarjeta de entrega confirmada preserva intactas las tarjetas adyacentes, sus tiempos de espera calculados y su condición de desplegadas o cerradas.
10. **Finalización del seguimiento en comensal**: La pantalla de seguimiento del comensal concluye su ciclo en la vista de entrega finalizada. Al llegar a este estado, se inhibe cualquier ciclo de actualización subsecuente.

---

## Casos borde

1. **Código numérico en los límites del rango (1000 y 9999)**:
   - El generador de códigos asigna los valores frontera 1000 o 9999.
   - *Comportamiento esperado*: El sistema los procesa, persiste y visualiza exactamente como valores válidos de 4 dígitos sin truncamiento ni formateo erróneo en la pantalla del comensal y en la tarjeta de mostrador.
2. **Coincidencia oral exacta entre lo recitado y lo mostrado**:
   - El comensal recita "4521" y la tarjeta desplegada muestra "4521".
   - *Comportamiento esperado*: El encargado constata la identidad legítima, presiona "ENTREGAR PEDIDO", se exhibe el indicador verde "Entrega Confirmada", el botón se deshabilita y se concreta el archivado terminal.
3. **Discrepancia entre el código oral y el código mostrado**:
   - El comensal recita un código erróneo o inexistente (por ejemplo, "1234" frente a "8765" exhibido).
   - *Comportamiento esperado*: El sistema no bloquea ni valida automáticamente; el encargado de mostrador, ejerciendo el protocolo humano, no pulsa el botón "ENTREGAR PEDIDO", retiene la bandeja física y solicita al comensal verificar el código en su pantalla de seguimiento.
4. **Doble clic o pulsaciones rápidas repetidas sobre "ENTREGAR PEDIDO"**:
   - El operador pulsa el botón dos o más veces en rápida sucesión antes de que el sistema procese la primera petición.
   - *Comportamiento esperado*: El primer clic deshabilita el botón de manera instantánea en la interfaz del cliente. Cualquier pulsación subsecuente es ignorada y se despacha únicamente una solicitud de cierre.
5. **Reintento de red de la solicitud de cierre sobre una orden ya completada**:
   - La solicitud de cierre llega al servidor y se procesa exitosamente, pero la respuesta de red se pierde; la terminal reintenta la solicitud.
   - *Comportamiento esperado*: Por la regla de idempotencia, el sistema identifica que la orden ya se encuentra en estado "completada", retorna confirmación exitosa de inmediato sin modificar datos y la interfaz de mostrador procede a remover la tarjeta.
6. **Recepción de solicitud de entrega para orden previamente completada**:
   - Se recibe una petición de cierre dirigida a una orden cuyo ciclo ya concluyó con anterioridad.
   - *Comportamiento esperado*: El sistema responde de forma afirmativa (éxito) sin alterar el estado, sin recalcular el instante de cierre original (`completed_at`) y sin emitir advertencias de error.
7. **Cierre de orden con múltiples tarjetas desplegadas simultáneamente en mostrador**:
   - El encargado mantiene tres tarjetas abiertas en mostrador y ejecuta la entrega sobre una de ellas.
   - *Comportamiento esperado*: La tarjeta correspondiente al pedido entregado desaparece de la pantalla; las restantes dos tarjetas permanecen en la misma posición relativa, conservan su estado desplegado y continúan reflejando sus datos sin colapso ni parpadeo.
8. **Cierre ejecutado mientras la pantalla de seguimiento del comensal permanece abierta**:
   - El comensal tiene su teléfono abierto en la pantalla de seguimiento visualizando el aviso verde de pedido listo con el código de retiro cuando el encargado concreta la entrega en mostrador.
   - *Comportamiento esperado*: En el siguiente ciclo de sincronización del comensal (o al recargar), la vista conmuta automáticamente al mensaje definitivo "Pedido Entregado — ¡Que disfrutes tu comida!" junto al resumen de compra, desactivando ciclos posteriores.
9. **Consulta del comensal después del cierre efectivo**:
   - El comensal accede a su enlace de seguimiento horas o días después de haber retirado su comida.
   - *Comportamiento esperado*: La pantalla renderiza de manera estable y directa el estado de entrega concluida con el mensaje "Pedido Entregado — ¡Que disfrutes tu comida!" y el detalle de su compra.
10. **Orden en estado "listo para retirar" sin código de seguridad asignado**:
    - Se detecta una orden preexistente o inconsistente que no posee un código numérico registrado.
    - *Comportamiento esperado*: Se considera una inconsistencia de datos previa ajena a esta especificación. En la interfaz de mostrador, el contenedor del código se presenta con un indicador neutral de ausencia de código sin bloquear el renderizado de la tarjeta ni impedir la pulsación de entrega si el operador decide proceder manualmente.

---

## Entidades clave

- **Orden (`Order`)**: Entidad transaccional central de la compra. Para esta especificación, contiene su identificador, el número correlativo de ticket (#ticketNumber), el código numérico de seguridad de 4 dígitos (`security_code`), su estado operativo unificado (transicionando de `READY_FOR_PICKUP` a `COMPLETED`), el instante de entrega confirmada (`completed_at`) y el resumen de platos adquiridos.
- **Comanda de cocina (`KdsTicket`)**: Entidad operativa de elaboración gastronómica vinculada a la orden. Transiciona de forma atómica y sincronizada con la orden desde su estado de elaboración previa hacia el estado `COMPLETED`.
- **Código numérico de seguridad (`security_code`)**: Valor numérico aleatorio de 4 dígitos en el intervalo [1000, 9999], inmutable y generado al registrar y pagar la orden, utilizado como factor de autenticación oral en ventanilla.
- **Tarjeta de mostrador**: Componente visual de la pantalla de mostrador que presenta la información del pedido listo, expone el código de seguridad para cotejo y aloja el botón "ENTREGAR PEDIDO" con su retroalimentación verde "Entrega Confirmada".
- **Pantalla de seguimiento del comensal**: Vista accesible por el cliente que expone el código de retiro mientras la orden está lista y exhibe de forma terminal la leyenda "Pedido Entregado — ¡Que disfrutes tu comida!" junto al resumen de platos al completarse la entrega.

---

## Criterios de éxito medibles

- **SC-001**: El 100% de las órdenes confirmadas y pagadas disponen de un código numérico de seguridad de exactamente 4 dígitos dentro del rango determinista de 1000 a 9999, sin excepciones ni valores nulos.
- **SC-002**: El código numérico de seguridad permanece inalterado en el 100% de los pedidos a lo largo de todo su ciclo de vida, verificando cero casos de regeneración o rotación no autorizada.
- **SC-003**: En el 100% de las tarjetas de pedidos listos desplegadas en mostrador, el código numérico de seguridad se renderiza de forma clara y destacada para el cotejo visual por parte del operador.
- **SC-004**: Al pulsar el botón "ENTREGAR PEDIDO", el 100% de las interacciones disparan la deshabilitación inmediata del control e introducen la confirmación verde "Entrega Confirmada" en menos de 100 milisegundos a nivel de interfaz de usuario.
- **SC-005**: El 100% de las transiciones de cierre exitosas actualizan de forma estrictamente atómica e indivisible tanto el estado de la orden como el del ticket de cocina a "completada", registrando la marca temporal de cierre sin inconsistencias parciales.
- **SC-006**: Tras recibir la respuesta afirmativa de entrega, la tarjeta correspondiente desaparece del tablero activo de despacho en el 100% de los casos sin requerir recargas manuales de página.
- **SC-007**: El 100% de las solicitudes repetidas o duplicadas sobre órdenes ya completadas son resueltas de manera idempotente con respuesta afirmativa sin alterar los datos preexistentes.
- **SC-008**: El 100% de los comensales cuya orden transiciona a "completada" visualizan en su pantalla de seguimiento el mensaje "Pedido Entregado — ¡Que disfrutes tu comida!" y el resumen de compra en su siguiente ciclo de actualización.

---

## Supuestos y dependencias

- **Autenticación y permisos de mostrador**: Se asume que el operador que accede a la pantalla de mostrador cuenta con una sesión válida y autorizada con el rol de encargado de mostrador, conforme a las especificaciones 002 y 020.
- **Persistencia previa de órdenes y pagos**: Se asume que la orden que arriba a mostrador ha superado exitosamente el flujo de confirmación y cobro especificado en las especificaciones 014, 015 y 016, y ha sido procesada por la cocina conforme a las especificaciones 019 y 020.
- **Componente base de tarjeta en mostrador**: Se asume que la anatomía de las tarjetas de mostrador, su disposición en grilla y su comportamiento interactivo de apertura y cierre responden a lo implementado bajo la especificación 020.
- **Conectividad de red estándar**: Se asume un entorno de conectividad operativa estándar en el establecimiento comercial, previendo mecanismos de reintento idempotente ante microcortes transitorios de enlace.

---

## Fuera de alcance

- Validación algorítmica, comparación automática de texto o verificación por software del código numérico de seguridad al presionar entrega.
- Regeneración, modificación, reemplazo o expiración anticipada del código numérico de seguridad.
- Modificación del rango numérico del código de seguridad más allá del intervalo cerrado de 1000 a 9999.
- Empleo de lectores ópticos, escáneres de código de barras, lectores de códigos QR o periféricos biométricos.
- Reapertura, reversión, reactivación o cancelación de pedidos que ya alcanzaron el estado "completada".
- Modificación de la estructura general de columnas del KDS, del flujo de cocina o de la mecánica de tarjetas de mostrador especificada en 019 y 020.
- Procesamiento de cobros, reembolsos, liquidaciones de propinas o conciliaciones financieras de pasarelas de pago.
- Emisión de comprobantes fiscales impresos, tickets de papel o comandas físicas por impresora térmica.
- Envío de notificaciones por canales externos (SMS, correo electrónico, mensajería instantánea o notificaciones push) fuera de la pantalla de seguimiento web del comensal.
- Definición de contratos técnicos específicos, endpoints REST, esquemas de tablas, clases CSS, hooks o librerías de implementación.

---

## Preguntas abiertas

No se identifican preguntas abiertas ni ambigüedades pendientes. Las reglas de negocio, los invariantes transaccionales, las prioridades operativas y los escenarios de aceptación se encuentran estrictamente delimitados y alineados con las especificaciones previas del sistema (001 a 021).

---

## Lista de verificación de cobertura de historias de usuario

| Historia de usuario | Título / Alcance | Prioridad | Criterios de aceptación cubiertos | Sección en la especificación |
|---|---|---|---|---|
| `ENC-3.2.1` | Código numérico de seguridad de retiro oral (Protocolo Antifraude) | P1 | AC-1: Generación y persistencia de PIN aleatorio (1000 a 9999).<br>AC-2: Cotejo visual en tarjeta desplegada en mostrador. | Historia de usuario 1 (§AC-1, §AC-2), §RF-001, §RF-002, §RF-003, §RF-004, §RF-005, §Invariantes 1, 2, 3, §Casos Borde 1, 2, 3, 10, §SC-001, §SC-002, §SC-003. |
| `ENC-3.3.1` | Confirmación de coincidencia y acción directa de entrega | P2 | AC-1: Distintivo visual verde "Entrega Confirmada" al accionar botón.<br>AC-2: Deshabilitado inmediato contra pulsación doble. | Historia de usuario 3 (§AC-1, §AC-2), §RF-006, §RF-007, §RF-008, §Invariantes 8, §Casos Borde 4, §SC-004. |
| `ENC-3.3.2` | Marcar pedido como "Entregado" y archivar ciclo de orden | P1 | AC-1: Transición atómica de orden y ticket a completada con registro de instante de entrega.<br>AC-2: Remoción de mostrador e idempotencia transaccional.<br>AC-3: Conclusión definitiva en seguimiento del comensal con mensaje de entrega y resumen. | Historia de usuario 2 (§AC-1, §AC-2, §AC-3), §RF-009, §RF-010, §RF-011, §RF-012, §RF-013, §RF-014, §RF-015, §RF-016, §RF-017, §Invariantes 4, 5, 6, 7, 9, 10, §Casos Borde 5, 6, 7, 8, 9, §SC-005, §SC-006, §SC-007, §SC-008. |
