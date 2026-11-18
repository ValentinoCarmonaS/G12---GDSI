# Especificación funcional: despacho a mostrador y sincronización de pedidos listos

**Identificador de especificación**: `020-counter-dispatch-ready-orders`  
**Rama de trabajo**: `020-counter-dispatch-ready-orders`  
**Fecha de creación**: 15/11/2026  
**Estado**: Aprobado  
**Entrada**: Requerimientos funcionales derivados de las historias de usuario `CHF-4.1.3`, `CHF-4.2.3`, `ENC-2.1.1` y `ENC-2.1.2`.

---

## Contexto y objetivo

En la operación gastronómica de atención en salón y despacho, el traspaso físico de la comida elaborada desde el área de producción culinaria (cocina) hacia la estación de entrega y atención al público (mostrador) constituye el enlace crítico para garantizar frescura, control y fluidez en el servicio.

El objetivo de esta especificación es definir el comportamiento observable, las reglas de negocio y los criterios de aceptación deterministas para:
1. Permitir que el personal de cocina dé por finalizada la preparación de una comanda completa mediante una acción directa, transfiriendo de inmediato la responsabilidad operativa al mostrador y liberando el espacio visual en la pantalla de cocina.
2. Proveer al personal de mostrador una interfaz dedicada para visualizar las órdenes listas en espera de retiro, organizada en tarjetas compactas cerradas por defecto que pueden desplegarse individualmente para cotejar los platos preparados con la bandeja física antes de proceder a la entrega.
3. Asegurar la sincronización continua y no intrusiva entre ambas estaciones operativas, de modo que las nuevas órdenes listas aparezcan de forma automática sin recargas de página, sin colapsar tarjetas que el operador esté consultando y con resiliencia ante contingencias transitorias de conectividad.

Esta especificación es estrictamente agnóstica a la tecnología, no prescribe detalles de arquitectura ni de persistencia y establece las bases operativas sobre las cuales se integrará posteriormente el protocolo de validación de entrega.

---

## Clarifications

### Session 2026-11-15

- Q: ¿Cuál es el criterio de ordenamiento determinista para la grilla de pedidos listos en la pantalla de mostrador? → A: Orden cronológico por mayor tiempo de espera en mostrador (FIFO por momento de finalización en cocina ascendente / tiempo en mostrador descendente: las órdenes que llevan más minutos listas aparecen primero al tope de la grilla), desempatando de manera determinista por número de ticket correlativo ascendente.
- Q: ¿Cómo debe responder una tarjeta que ya está desplegada cuando el encargado vuelve a interactuar con ella? → A: Alternancia bidireccional (toggle con protección interna): un clic sobre la cabecera o zona neutral de una tarjeta desplegada la colapsa nuevamente a su estado cerrado; las interacciones con el contenido interior (desglose de platos o contenedor del botón de entrega) no disparan el colapso accidental de la tarjeta.
- Q: ¿Cuál debe ser el comportamiento y estado observable del botón de entrega dentro de la tarjeta desplegada en esta fase? → A: El botón de entrega permanece visible en la tarjeta desplegada como contenedor visual operativo; cualquier comportamiento reactivo posterior a su pulsación, validación de código de retiro o cierre efectivo de la orden queda formalmente fuera de alcance para la especificación posterior de entrega antifraude.
- Q: ¿Qué órdenes deben listarse en la pantalla de mostrador y qué debe mostrar la interfaz ante la ausencia de pedidos listos? → A: Se listan exclusivamente las órdenes en estado "listo para retiro" correspondientes a la jornada operativa actual (`hoy`) del establecimiento; ante una lista vacía, la interfaz renderiza un mensaje informativo neutral (por ejemplo, "No hay pedidos listos para entrega"), evitando saltos bruscos de diseño o estados inconsistentes.
- Q: ¿Cómo debe representarse visualmente al comensal en la tarjeta desplegada cuando no cuenta con fotografía de perfil registrada? → A: Omisión limpia y directa del contenedor de imagen, alineando el nombre del comensal sin elemento gráfico sustituto.
- Q: ¿Qué señal visual debe emitir la pantalla de mostrador ante una pérdida sostenida de conexión de red (más de 15 segundos sin sincronizar)? → A: Insignia sutil y no bloqueante en la cabecera (por ejemplo, "Sin conexión — Reintentando...") que advierte al operador sin ocultar las tarjetas existentes ni colapsar las abiertas, desapareciendo automáticamente al reconectar.
- Q: ¿Cuál debe ser el formato exacto de exhibición del turno horario pactado en las tarjetas de mostrador? → A: Rango completo en formato "HH:mm - HH:mm" (por ejemplo, "13:00 - 13:15"), manteniendo plena consistencia con el KDS de cocina y los bloques de retiro seleccionados en el checkout.

---

## Personas

1. **Chef que completó la cocción de todos los platos de una bandeja**: Personal de cocina que, habiendo concluido la elaboración íntegra de los alimentos correspondientes a una comanda, finaliza el ticket en la pantalla táctil de producción para indicar que la bandeja física se encuentra lista para ventanilla.
2. **Chef que despacha bandejas a ventanilla**: Personal de cocina que traslada los pedidos terminados al área de traspaso físico y requiere la certeza de que el personal de mostrador reciba la orden en su pantalla sin retrasos operativos.
3. **Encargado de mostrador en la estación de entrega**: Personal de sala que administra la entrega física de pedidos a los clientes en ventanilla, consultando la pantalla de mostrador para organizar los despachos y acceder a la información de retiro al presentarse cada comensal.
4. **Encargado de mostrador que verifica la comanda física**: Operador de entrega que coteja visualmente el contenido armado de la bandeja física contra el desglose detallado de platos del pedido para asegurar la exactitud del despacho antes de entregarlo.

---

## Historias de usuario y escenarios de aceptación

### Historia de usuario 1 — Finalizar comanda completa y pasar a estado "Listo" (Prioridad: P1)

**Identificador de referencia**: `CHF-4.1.3`  
**Persona**: Chef que completó la cocción de todos los platos de una bandeja.

Como chef que completó la cocción de todos los platos de una bandeja, quiero pulsar un botón directo "LISTO" en la tarjeta de Columna 2, para dar por terminada la orden, retirarla de mi KDS y transferirla de inmediato a la responsabilidad del mostrador.

**Por qué tiene prioridad P1**: Es el disparador operativo fundamental que culmina la etapa de preparación en cocina. Sin esta acción, las órdenes permanecen indefinidamente en preparación activa y el flujo hacia mostrador queda completamente bloqueado.

**Prueba independiente**: Se puede comprobar visualizando un ticket en la Columna 2 de cocina y pulsando el botón "LISTO": la tarjeta desaparece de inmediato de la pantalla de cocina, el ticket y la orden asociada transicionan a "listo para retiro" con la marca temporal correspondiente, y no se requiere ninguna confirmación emergente ni tildado individual de platos.

**Escenarios de aceptación**:

1. **AC-1 (Finalización directa de bandeja completa sin checklist manual)**:
   - **Dado** un ticket de comanda en preparación activa ubicado en la Columna 2 de cocina,
   - **Cuando** el chef finaliza la preparación física y presiona el botón "LISTO",
   - **Entonces** el sistema procesa la finalización de la bandeja completa de manera directa, sin requerir confirmaciones emergentes ni exigir el tildado o tachado manual plato por plato.

2. **AC-2 (Transición atómica e indivisible de comanda y orden)**:
   - **Dado** que el sistema recibe la solicitud de finalización de una comanda,
   - **Cuando** ejecuta la transición operativa,
   - **Entonces** actualiza de forma atómica e indivisible el ticket a estado "listo para retiro", registra el instante exacto de finalización y actualiza la orden asociada al estado "listo para retiro", garantizando que o bien se completa íntegramente la transición de ambas entidades, o bien ninguna de ellas sufre modificación alguna.

3. **AC-3 (Despacho operativo y remoción inmediata en cocina)**:
   - **Dado** que un ticket fue marcado y confirmado como listo para retiro,
   - **Cuando** la pantalla de cocina recibe la confirmación de la transición,
   - **Entonces** remueve de inmediato la tarjeta de la Columna 2 de cocina, dando por concluido su ciclo en el área de elaboración y liberando el espacio visual para las demás órdenes en curso.

---

### Historia de usuario 2 — Sincronización reactiva con la cola de mostrador (Prioridad: P1)

**Identificador de referencia**: `CHF-4.2.3`  
**Persona**: Chef que despacha bandejas a ventanilla.

Como chef que despacha bandejas a ventanilla, quiero que al marcar "LISTO" la orden viaje automáticamente a la pantalla del encargado de mostrador, para asegurar que el personal de entrega sepa de inmediato qué pedidos están listos para entregar al comensal.

**Por qué tiene prioridad P1**: Garantiza el acoplamiento operacional en tiempo cuasi-real entre cocina y mostrador, previniendo pedidos enfriados en ventanilla por falta de aviso y evitando la necesidad de recargas manuales por parte del personal de sala.

**Prueba independiente**: Se puede comprobar finalizando un pedido en la pantalla de cocina y observando la pantalla de mostrador sin interactuar con ella: dentro del intervalo habitual de 3 a 5 segundos, la nueva orden aparece automáticamente en la lista de pedidos listos. Asimismo, desconectando brevemente la red de la terminal de mostrador y reconectándola, la siguiente actualización refleja la orden lista de forma íntegra.

**Escenarios de aceptación**:

1. **AC-1 (Aparición automática en ciclo periódico sin recarga)**:
   - **Dado** que una orden pasa al estado "listo para retiro" desde el área de cocina,
   - **When** la terminal de mostrador ejecuta su ciclo de actualización automática periódica habitual cada 3 a 5 segundos,
   - **Then** la orden aparece automáticamente incorporada en la lista de pedidos listos de mostrador, sin requerir recarga manual de la página y sin mostrar información desactualizada u obsoleta.

2. **AC-2 (Recuperación consistente ante cortes transitorios de conectividad)**:
   - **Dado** que ocurre un corte momentáneo de conectividad en la terminal de mostrador al momento exacto en que cocina finaliza una comanda,
   - **When** se restablece la comunicación con el sistema,
   - **Then** la siguiente consulta periódica de mostrador recupera la orden lista y la renderiza de forma visualmente consistente y completa en la grilla de pedidos listos.

---

### Historia de usuario 3 — Tablero de pedidos listos para entrega: diseño plegable/expandible (Prioridad: P1)

**Identificador de referencia**: `ENC-2.1.1`  
**Persona**: Encargado de mostrador en la estación de entrega.

Como encargado de mostrador en la estación de entrega, quiero visualizar una lista de pedidos listos en tarjetas cerradas que exhiban el ticket, turno y tiempo en mostrador, y poder desplegar cualquier tarjeta con un clic, para organizar los despachos con rapidez y acceder a los datos de retiro solo cuando el cliente se presenta en ventanilla.

**Por qué tiene prioridad P1**: Provee la superficie visual y ergonómica central para el rol de mostrador. El patrón de tarjetas cerradas optimiza la densidad de información en pantalla, permitiendo supervisar múltiples pedidos listos simultáneamente sin saturación visual.

**Prueba independiente**: Se puede verificar accediendo a la vista de mostrador con pedidos listos: cada uno se visualiza en tarjeta compacta cerrada mostrando ticket, turno y tiempo de espera en minutos. Al hacer clic sobre cualquier sector de la tarjeta, se expande mostrando código numérico de seguridad, nombre del comensal, foto (o sustituto limpio) y botón de entrega, sin que las actualizaciones periódicas posteriores cierren la tarjeta ni recarguen la vista.

**Escenarios de aceptación**:

1. **AC-1 (Visualización compacta en tarjetas cerradas con métricas clave)**:
   - **Dado** que existen órdenes en estado "listo para retiro",
   - **When** el encargado accede a la pantalla de mostrador,
   - **Then** cada pedido se muestra por defecto en una tarjeta en estado cerrado que exhibe de manera clara y destacada el número correlativo de ticket, el turno horario pactado para el retiro en formato de rango completo `HH:mm - HH:mm` y un contador que refleja los minutos enteros transcurridos en espera desde el instante en que cocina finalizó la comanda.

2. **AC-2 (Despliegue interactivo de datos operativos de retiro)**:
   - **Dado** una tarjeta de pedido listo en estado cerrado en la pantalla de mostrador,
   - **When** el encargado realiza un clic en cualquier parte de dicha tarjeta,
   - **Then** la tarjeta se despliega de forma inmediata exhibiendo el código numérico de seguridad de 4 dígitos, el nombre completo del comensal, su fotografía de perfil si la tiene configurada u omisión limpia en caso de no poseerla, y el contenedor con el botón de entrega.

3. **AC-3 (Actualización fluida sin parpadeo ni colapso de tarjetas abiertas)**:
   - **Dado** que la pantalla de mostrador se actualiza automáticamente cada 3 a 5 segundos,
   - **When** ingresa un nuevo pedido listo a la lista o se sincronizan los estados existentes,
   - **Then** la nueva orden se incorpora fluidamente a la grilla sin recargar la pantalla, sin generar parpadeos visuales y preservando sin colapsar el estado de aquellas tarjetas que el operador mantenga desplegadas en ese momento.

---

### Historia de usuario 4 — Verificación rápida de comanda física en mostrador (Prioridad: P2)

**Identificador de referencia**: `ENC-2.1.2`  
**Persona**: Encargado de mostrador que verifica la comanda física.

Como encargado de mostrador, quiero ver la lista de platos y cantidades dentro del detalle desplegado de la tarjeta, para constatar que la bandeja física armada coincida exactamente con lo solicitado por el cliente antes de proceder a la entrega.

**Por qué tiene prioridad P2**: Agrega valor sustancial al control de calidad y precisión del despacho en ventanilla, previniendo entregas erróneas o incompletas una vez que la orden ya está desplegada para su atención.

**Prueba independiente**: Se puede comprobar desplegando una tarjeta en mostrador y contrastando la lista de ítems: se observa la totalidad de platos solicitados con el formato unificado de cantidad y nombre, verificando que no se expone ningún valor monetario ni subtotal en la vista.

**Escenarios de aceptación**:

1. **AC-1 (Desglose unificado de platos y cantidades para cotejo visual)**:
   - **Dado** que el encargado despliega una tarjeta de pedido listo en mostrador,
   - **When** inspecciona el contenido detallado de la orden,
   - **Then** visualiza el listado íntegro de platos que componen el pedido bajo el formato unificado `[Cantidad]x [Plato]` para cotejar de forma directa y visual contra la bandeja física preparada.

2. **AC-2 (Omisión estricta de importes monetarios en pantalla operativa)**:
   - **Dado** que el pedido fue abonado previamente durante la etapa de checkout,
   - **When** se presentan los platos dentro del detalle desplegado en mostrador,
   - **Then** el sistema omite de forma estricta los precios individuales de los platos, subtotales y montos monetarios totales, focalizando la visualización exclusivamente en la precisión del despacho físico.

---

## Requisitos funcionales

- **RF-001**: La tarjeta de comanda en preparación activa en la Columna 2 de cocina debe presentar un botón directo y prominente rotulado "LISTO".
- **RF-002**: La activación del botón "LISTO" en cocina debe finalizar la comanda completa en una única operación atómica, sin requerir confirmaciones emergentes ni listas de verificación intermedias plato por plato.
- **RF-003**: Al confirmarse la finalización, el ticket de cocina y la orden asociada deben transicionar indivisiblemente al estado "listo para retiro", registrando ambos exactamente el mismo instante temporal de finalización.
- **RF-004**: Al completarse la transición a "listo para retiro", la comanda debe removerse de forma inmediata y automática de la Columna 2 de cocina, liberando el espacio visual en el KDS.
- **RF-005**: La pantalla de mostrador debe exhibir la lista de pedidos en estado "listo para retiro" correspondientes a la jornada operativa actual del establecimiento.
- **RF-006**: Cada pedido listo en mostrador debe renderizarse inicialmente en una tarjeta en estado cerrado que exponga: número correlativo de ticket destacado, turno horario pactado en formato de rango completo "HH:mm - HH:mm" y contador de minutos de espera transcurridos.
- **RF-007**: El tiempo en mostrador debe calcularse de manera continua en minutos enteros transcurridos a partir del instante exacto en que cocina finalizó la comanda.
- **RF-008**: Un pedido recién finalizado en cocina debe exhibir en su contador de espera en mostrador el valor de cero minutos (`0 min`).
- **RF-009**: La interacción mediante clic en cualquier parte de una tarjeta cerrada en mostrador debe desplegar la tarjeta revelando sus datos operativos.
- **RF-010**: Múltiples tarjetas de mostrador pueden permanecer desplegadas de forma simultánea e independiente en la pantalla.
- **RF-011**: Un clic sobre la cabecera o zona neutral de una tarjeta desplegada debe colapsar la tarjeta a su estado cerrado; las interacciones con elementos interiores (como el listado de platos o el botón de entrega) no deben provocar el colapso accidental.
- **RF-012**: La tarjeta desplegada en mostrador debe mostrar:
  1. Código numérico de seguridad de 4 dígitos para cotejo visual oral.
  2. Nombre completo del comensal.
  3. Fotografía de perfil del comensal si está disponible, u omisión limpia y directa del contenedor de imagen alineando su nombre sin elemento gráfico sustituto en caso de no poseerla.
  4. Desglose completo de platos en formato `[Cantidad]x [Plato]`.
  5. Contenedor visual con el botón de entrega.
- **RF-013**: La pantalla de mostrador no debe exhibir precios unitarios, subtotales ni importes monetarios totales.
- **RF-014**: La pantalla de mostrador debe actualizarse automáticamente de forma periódica en un ciclo continuo de 3 a 5 segundos sin recargar la página.
- **RF-015**: La actualización automática periódica no debe alterar ni colapsar las tarjetas que el operador mantenga desplegadas en ese momento.
- **RF-016**: La actualización automática periódica no debe generar duplicados visuales de pedidos que ya se encuentren renderizados en la pantalla.
- **RF-017**: Las tarjetas en la pantalla de mostrador deben ordenarse de forma determinista por mayor tiempo de espera transcurrido en mostrador (orden cronológico ascendente por instante de finalización en cocina), desempatando por número correlativo de ticket ascendente.
- **RF-018**: Ante una pérdida transitoria de conexión a red en la terminal de mostrador, el sistema debe reanudar la actualización en el siguiente ciclo exitoso tras el restablecimiento del enlace. Si la desconexión se prolonga de forma sostenida (más de 15 segundos sin respuesta), la interfaz debe exhibir una insignia sutil y no bloqueante en la cabecera (por ejemplo, "Sin conexión — Reintentando...") que informe la situación sin ocultar las órdenes visibles ni colapsar las tarjetas desplegadas, desapareciendo de manera automática al restablecerse la sincronización exitosa.
- **RF-019**: Cuando no existan pedidos en estado "listo para retiro", la pantalla de mostrador debe presentar un mensaje informativo neutral (por ejemplo, "No hay pedidos listos para entrega") preservando la estabilidad visual de la interfaz.

---

## Invariantes y reglas deterministas

1. **Atomicidad de la finalización en cocina**: La transición a "listo para retiro" es indivisible; el ticket de cocina y la orden asociada cambian de estado de forma conjunta o ninguna muta.
2. **Coincidencia temporal inmutable**: El instante de finalización registrado en el ticket de cocina y en la orden asociada debe ser exactamente idéntico.
3. **Ausencia de checklist en cocina**: La finalización de la bandeja completa no admite pasos intermedios de confirmación emergente ni selección plato por plato.
4. **Exclusión de pedidos listos en cocina**: La pantalla de cocina no dispone de columna para pedidos finalizados; una comanda confirmada como lista desaparece permanentemente de la vista de producción.
5. **Estado cerrado inicial en mostrador**: Toda tarjeta de pedido listo que ingrese a la pantalla de mostrador se renderiza cerrada por defecto.
6. **Autonomía de expansión en mostrador**: Cada tarjeta gestiona su estado desplegado o cerrado de forma aislada; la apertura o cierre de una tarjeta no altera el estado de ninguna otra.
7. **Invarianza de estado desplegado ante refrescos**: Las actualizaciones automáticas periódicas de fondo no deben cerrar, colapsar ni reiniciar el estado desplegado de ninguna tarjeta activa.
8. **Monotonía del contador de espera**: El tiempo en mostrador es un contador progresivo en minutos enteros calculado estrictamente como la diferencia entre el instante actual del sistema y el instante inmutable de finalización registrado por cocina.
9. **Determinismo del ordenamiento de despacho**: El orden de las tarjetas en mostrador responde estrictamente a la prioridad temporal del servicio: a mayor tiempo de espera en ventanilla, mayor precedencia visual (arriba a la izquierda en la grilla), con desempate determinista por número de ticket correlativo.
10. **Agnosticismo monetario en despacho**: La interfaz de mostrador es puramente operativa y física; bajo ninguna circunstancia se presentan datos monetarios, divisas o importes numéricos financieros.
11. **Idempotencia de visualización**: Consultas sucesivas con el mismo conjunto de órdenes listas deben producir exactamente la misma representación visual sin parpadeos, saltos de maquetación ni elementos repetidos.

---

## Entidades clave

- **Ticket de comanda en cocina**: Registro de control de producción culinaria asociado a un pedido, que transiciona de preparación activa a listo para retiro registrando el momento exacto de finalización.
- **Orden de pedido**: Registro integral del pedido del comensal que vincula los platos contratados, el turno horario convenido, el comensal titular, el código numérico de seguridad de 4 dígitos y el estado operativo general coordinado con cocina.
- **Tarjeta de pedido listo (Mostrador)**: Componente visual interactivo en la pantalla de entrega que presenta la información resumida en modo cerrado (ticket, turno y minutos de espera) y la información operativa detallada en modo desplegado (código de 4 dígitos, nombre, foto, desglose de platos `[N]x [Plato]` y botón de entrega).
- **Contador de tiempo en mostrador**: Valor numérico entero expresado en minutos que cuantifica la permanencia del pedido listo en ventanilla a partir de su marca temporal de finalización.

---

## Criterios de éxito y resultados medibles

- **SC-001**: El 100% de las pulsaciones exitosas sobre el botón "LISTO" en la Columna 2 de cocina remueven la tarjeta de la vista de cocina en menos de 1 segundo tras confirmarse la operación.
- **SC-002**: Toda comanda finalizada en cocina aparece visible en la pantalla de mostrador en un tiempo máximo no superior a 5 segundos en condiciones normales de red.
- **SC-003**: Cero colapsos involuntarios de tarjetas desplegadas en mostrador provocados por los ciclos automáticos de actualización periódica.
- **SC-004**: El tiempo en mostrador exhibido en pantalla coincide de forma exacta y consistente en cualquier terminal de mostrador conectada al sistema, con discrepancia máxima de 60 segundos debido a la resolución por minuto entero.
- **SC-005**: El 100% de las tarjetas desplegadas exhiben la totalidad de los platos solicitados en formato `[Cantidad]x [Plato]` sin incluir ningún dato de precio ni subtotal monetario.
- **SC-006**: La grilla de mostrador mantiene el orden determinista de mayor tiempo de espera arriba de forma continua e invariable frente a nuevas incorporaciones.

---

## Casos borde

1. **Pedido finalizado mientras la terminal de mostrador está temporalmente sin red**: Si el chef finaliza una orden mientras la terminal de mostrador experimenta un corte de conectividad, las órdenes ya presentes permanecen legibles. Si la desconexión se sostiene por más de 15 segundos, se hace visible la insignia no bloqueante de reconexión. Al restablecerse el enlace, la siguiente actualización periódica recupera de forma fluida las nuevas órdenes listas incorporándolas a la grilla con el tiempo de espera calculado correctamente desde el instante en que cocina las finalizó, y la insignia de advertencia se remueve automáticamente.
2. **Nueva orden lista mientras el encargado tiene una o más tarjetas desplegadas**: Cuando una nueva orden finaliza en cocina y se añade a la grilla de mostrador, se posiciona en el lugar correspondiente según su tiempo de espera sin colapsar ni desestructurar las tarjetas que el operador tenga abiertas en ese instante.
3. **Pedido de comensal sin foto de perfil configurada**: Cuando el usuario no cuenta con una fotografía de perfil registrada en su cuenta, la tarjeta desplegada omite limpiamente el contenedor de imagen y alinea de forma directa el nombre del comensal, manteniendo un diseño sobrio y sin elementos gráficos sustitutos innecesarios.
4. **Ticket con múltiples platos y cantidades elevadas**: Cuando una orden contiene una variedad extensa de ítems o cantidades mayores a 1, la tarjeta desplegada presenta el listado ordenado bajo el formato `[Cantidad]x [Plato]` con tipografía legible y desplazamiento interior si fuera necesario, sin desbordar el contenedor de la tarjeta.
5. **Ticket recién finalizado con contador de espera en cero minutos**: En el lapso inicial entre 0 y 59 segundos posteriores a la finalización en cocina, el contador de espera en mostrador exhibe de forma determinista `0 min`, incrementando a `1 min` exactamente al cumplirse el primer minuto transcurrido.
6. **Actualización automática repetida sin recarga manual**: La ejecución reiterada de la actualización periódica cada 3 a 5 segundos durante una jornada prolongada no degrada la interfaz ni genera fugas de memoria o saltos perceptibles en la pantalla del operador.
7. **Tarjeta cerrada que debe permanecer cerrada tras una actualización**: Si el operador no ha interactuado con una tarjeta cerrada, las sucesivas actualizaciones periódicas conservan su estado cerrado sin desplegarla de manera espuria.
8. **Tarjeta desplegada que debe permanecer desplegada tras una actualización**: Una tarjeta que el operador abrió manualmente permanece abierta tras cada ciclo de actualización periódica hasta que el propio operador decida colapsarla o la orden concluya su ciclo.
9. **Orden visible que vuelve a recibirse en una actualización posterior sin duplicarse**: Cuando una consulta periódica recupera una lista que incluye órdenes ya presentes en pantalla, el sistema reconoce la identidad de cada orden y refresca sus datos (como los minutos de espera) sin duplicar visualmente las tarjetas en la grilla.
10. **Finalización de bandeja completa sin pasos intermedios de checklist**: Ante una orden con múltiples platos pendientes, la acción del botón "LISTO" procesa la finalización directa de la totalidad de la bandeja sin exigir que los ítems hayan sido tildados o interactuados previamente en pantalla.

---

## Supuestos

- La estación de mostrador opera en un dispositivo con pantalla visual de escritorio, tableta o monitor de punto de despacho conectada a la red del establecimiento.
- La pantalla de cocina (KDS) y la terminal de mostrador comparten la misma referencia de tiempo del sistema para el cálculo del tiempo transcurrido en minutos.
- Los pedidos que alcanzan la cocina provienen de órdenes previamente confirmadas y pagadas, contando ya con un número correlativo de ticket y un código numérico de seguridad de 4 dígitos generado.
- Las fotografías de perfil de comensales, cuando existen, son provistas por el perfil del usuario autenticado registrado en el sistema.

---

## Fuera de alcance

- Verificación efectiva del PIN o validación del código numérico de 4 dígitos (asignada formalmente a la especificación `022-pin-antifraud-delivery-completion`).
- Comportamiento reactivo posterior al presionar el botón de entrega, confirmación de entrega física, cierre de orden o liberación de mesa (asignados a la especificación `022`).
- Lógica de ajuste de demoras acumulativas de cocina mediante botón `+5` (asignada a la especificación `021-live-tracking-delay-adjustment`).
- Notificaciones externas fuera de la pantalla de mostrador (tales como avisos por mensajería, SMS o correos electrónicos al comensal).
- Impresión física de comandas o recibos en papel en ventanilla de mostrador.
- Procesamiento de cobros, transacciones monetarias, recargos o settlement en mostrador.
- Mecanismos de autenticación, login o recuperación de contraseñas del personal de mostrador (cubiertos en la especificación de autenticación de personal `002`).
- Modificación, edición de platos, agregado de notas libres o cambios de cantidades sobre el pedido en mostrador o cocina.
- Especificación de detalles técnicos de implementación (tales como rutas internas de código, nombres de tablas, esquemas relacionales, componentes de frontend, hooks o librerías).

---

## Preguntas abiertas

No se identifican preguntas abiertas ni ambigüedades pendientes. Las decisiones de diseño y comportamiento observable fueron clarificadas y acordadas durante la sesión de clarificación.

---

## Lista de verificación de cobertura

| Historia de usuario | Título de historia | Prioridad | Secciones y criterios cubiertos |
| :--- | :--- | :---: | :--- |
| `CHF-4.1.3` | Finalizar comanda completa y pasar a estado "Listo" | P1 | Historias de usuario (HU-1: AC-1, AC-2, AC-3), Requisitos funcionales (RF-001 a RF-004), Invariantes (1 a 4), Casos borde (10) |
| `CHF-4.2.3` | Sincronización reactiva con la cola de mostrador | P1 | Historias de usuario (HU-2: AC-1, AC-2), Requisitos funcionales (RF-014, RF-018), Invariantes (11), Casos borde (1, 6) |
| `ENC-2.1.1` | Tablero de pedidos listos para entrega: diseño plegable/expandible | P1 | Historias de usuario (HU-3: AC-1, AC-2, AC-3), Requisitos funcionales (RF-005 a RF-011, RF-014 a RF-017, RF-019), Invariantes (5 a 9), Casos borde (2, 3, 5, 7, 8, 9) |
| `ENC-2.1.2` | Verificación rápida de comanda física en mostrador | P2 | Historias de usuario (HU-4: AC-1, AC-2), Requisitos funcionales (RF-012, RF-013), Invariantes (10), Casos borde (4) |
