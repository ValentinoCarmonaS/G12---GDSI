# Especificación funcional: KDS — cola de cocina y preparación activa

**Identificador de especificación**: `019-kds-queue-preparation`  
**Rama de trabajo**: `019-kds-queue-preparation`  
**Fecha de creación**: 15/11/2026  
**Estado**: Aprobado  
**Entrada**: Requerimientos funcionales derivados de las historias de usuario `CHF-3.1.2`, `CHF-3.2.1`, `CHF-4.1.1`, `CHF-4.1.4` y `CHF-4.1.5`.

---

## Contexto y objetivo

El sistema de visualización de cocina (Kitchen Display System o KDS) constituye el soporte operativo visual para la elaboración de pedidos en cocina. Opera sobre órdenes previamente confirmadas y pagadas que ingresan en estado pendiente de preparación.

El objetivo de esta funcionalidad es proveer una interfaz táctil ergonómica y determinista basada en un tablero Kanban de dos columnas operativas:
1. **Columna 1 ("Pendientes")**: Presentación compacta de tickets no iniciados, optimizada para priorizar la cola cronológica y accionar el inicio de elaboración.
2. **Columna 2 ("En preparación")**: Presentación expandida de tickets en curso, con el desglose íntegro de platos, cronómetro progresivo en minutos enteros y alerta visual perimetral preventiva por tiempo transcurrido excesivo.

Esta especificación establece las reglas de ordenamiento determinista, la anatomía visual de las comandas, la transición idempotente de estados y el control temporal continuo, manteniéndose completamente agnóstica a la arquitectura técnica y al mecanismo de persistencia subyacente.

---

## Clarifications

### Session 2026-11-16

- Q: ¿Cómo debe estructurarse la cabecera (header) de la interfaz de cocina para el rol CHEF en el KDS? → A: Se remueve el título textual redundante ("Kitchen Display System (KDS)") dejando únicamente "EasyEat". Los enlaces horizontales de navegación se reemplazan por un menú desplegable de 3 barritas (botón hamburguesa) con acceso a las tres rutas canónicas de cocina (`/cocina`, `/cocina/platos`, `/cocina/ingredientes`), manteniendo indicador de sección activa `aria-current="page"`. Asimismo, se elimina el enlace y avatar de "Mi perfil" dado que el rol CHEF no cuenta con edición de perfil.
- Q: ¿Dónde deben ubicarse los datos de sesión y la acción de logout del cocinero? → A: Se trasladan al pie de página (footer) de la interfaz de cocina, alojando la insignia de rol CHEF, el correo electrónico del usuario, el identificador del local y el botón "Cerrar sesión".
- Q: ¿Cómo se visualiza el local en el pie de página de cocina? → A: El frontend consulta el endpoint público existente de branding (`GET /api/v1/establishments/{tenantId}/branding`) para exhibir el nombre comercial de fantasía (`Local: Café Parque`), implementando un fallback automático al identificador corto (`Local: 1aa45e0c`) con tooltip del UUID completo en caso de error o ausencia de nombre configurado.
- Q: ¿Qué elementos provisionales o placeholders deben removerse de `KitchenWorkspacePage` para dar protagonismo absoluto al tablero KDS? → A: Se eliminan el subtítulo ("Comandas en preparación..."), el bloque provisional de demora global (+5 min, reasignado a spec 021), el formulario manual de despacho por UUID (reemplazado por el botón LISTO de spec 020), la tarjeta estática de ingredientes (accesible ahora en el menú hamburguesa), el banner condicional de rechazo de platos (trasladado a la gestión de platos) y las cuatro tarjetas estadísticas de métricas del pie ("En Cola", "En Preparación", "Para Retirar", "Completadas hoy") con su polling asociado. El tablero Kanban KDS queda como el único elemento central de la pantalla.

### Session 2026-11-15

- Q: ¿Qué horizonte de fechas debe abarcar la cola activa de comandas en el KDS para evitar saturación de pedidos futuros? → A: La cola activa abarca la fecha operativa actual (`hoy`) más comandas incompletas de turnos previos si existieran; los pedidos programados para fechas futuras quedan excluidos de la vista de producción activa de hoy para evitar saturación visual.
- Q: ¿La Columna 1 exhibe todas las comandas pendientes o aplica paginación, y qué alcance de pedidos futuros del día incluye? → A: La Columna 1 exhibe todas las comandas pendientes del horizonte activo (día de hoy y turnos previos incompletos) en un contenedor con desplazamiento vertical (scroll táctil continuo) sin paginación por páginas. Se excluyen totalmente los pedidos de días posteriores (`pickupDate > hoy`), pero se incluyen todos los turnos del día en curso, ordenados cronológicamente: turnos vencidos incompletos arriba, luego el turno en curso, y sucesivamente los turnos futuros de la misma jornada hacia abajo accesibles por scroll.
- Q: ¿Cuál es el mecanismo de sincronización y refresco de la pantalla del KDS para reflejar nuevas órdenes y cambios de estado concurrentes? → A: Polling periódico silencioso (intervalo corto entre 3 y 5 segundos) en frontend para sincronizar la cola de forma liviana, desacoplada y sin estado en backend, complementado con el recálculo reactivo local del minutero Count-Up cada 60 segundos contra el instante de inicio sin sobrecargar el servidor.
- Q: ¿Cómo debe comportarse la interfaz del KDS cuando una de las dos columnas (o ambas) no contiene comandas? → A: La interfaz mantiene siempre su estructura fija de dos columnas y renderiza un contenedor con mensaje neutral indicativo (ej. "Sin pedidos pendientes" o "Sin pedidos en preparación"), evitando saltos visuales de diseño (layout shifts) y preservando la ergonomía táctil.
- Q: ¿Qué roles tienen acceso a visualizar y operar el KDS de cocina? → A: El KDS es exclusivo de la estación de producción en cocina y está restringido al rol CHEF; ningún otro rol (como ENCARGADO o ADMINISTRADOR) visualiza ni opera sobre esta pantalla ni sobre la acción de inicio de preparación.
- Q: ¿Cuáles son los requisitos de ergonomía táctil e interacción física en la pantalla de cocina (uso con guantes, humedad, grasa)? → A: Para garantizar la operabilidad en condiciones reales de cocina con dedos enguantados, húmedos o con restos orgánicos, los elementos interactivos críticos (especialmente el botón "EMPEZAR") deben poseer un área táctil mínima de 56px de altura (`min-h-[56px]`), ancho completo de tarjeta, tipografía extragrande de alto contraste (`text-lg font-black`), inhibición de selección de texto (`select-none`) y feedback táctil inmediato (`active:scale-[0.98]`). Asimismo, el identificador de comanda (`#ticketNumber`) debe renderizarse en tamaño destacado (`text-2xl font-black`) para permitir lectura periférica sin esfuerzo.

---

## Personas

1. **Chef que gestiona la línea de montaje en cocina**: Responsable del flujo general de pedidos, supervisa el cumplimiento del orden de salida según la urgencia cronológica y las franjas horarias comprometidas con los comensales.
2. **Chef operando la pantalla táctil de cocina**: Personal de cocina que interactúa físicamente con el monitor táctil situado en el área de producción para leer los platos a distancia visual y accionar los controles de preparación.
3. **Chef en la estación de cocción**: Cocinero que toma una comanda disponible para comenzar su producción física e indica al sistema que la orden entra en preparación activa.
4. **Chef que supervisa comandas en preparación activa**: Responsable de verificar los tiempos transcurridos de cocción en la línea y detectar órdenes demoradas para prevenir cuellos de botella operativos.

---

## Historias de usuario y escenarios de aceptación

### Historia de usuario 1 — Priorización automática de la cola de preparación: FIFO unificado por turno (Prioridad: P1)

**Identificador de referencia**: `CHF-3.1.2`  
**Persona**: Chef que gestiona la línea de montaje en cocina.

Como chef que gestiona la línea de montaje en cocina, quiero que el KDS organice automáticamente las comandas agrupándolas por franja horaria de retiro y aplicando orden de confirmación FIFO dentro de cada turno, para preparar primero lo más urgente de manera determinística, sin cálculos mentales y tratando bajo idéntica regla a pedidos inmediatos y programados.

**Por qué tiene prioridad P1**: Define el principio rector de ordenamiento de toda la producción en cocina. Sin un algoritmo determinista de encolado, la cocina carece de criterio unificado para procesar los pedidos y se producen errores en los tiempos comprometidos de entrega.

**Prueba independiente**: Puede comprobarse cargando comandas con diferentes modalidades (inmediatas y programadas) y franjas horarias distintas: la pantalla debe renderizar invariablemente las tarjetas ordenadas cronológicamente por franja y, dentro de la misma franja, por momento exacto de encolado.

**Escenarios de aceptación**:

1. **AC-1 (Ordenamiento FIFO unificado por franja horaria)**:
   - **Dado** que existen comandas en estado pendiente o en preparación asociadas a un mismo turno horario,
   - **Cuando** el personal de cocina consulta o se actualiza la cola del KDS,
   - **Entonces** las comandas se presentan ordenadas de forma ascendente por fecha de retiro, inicio de franja horaria y momento de encolado en cocina, conviviendo pedidos inmediatos y programados bajo la misma regla FIFO según su momento de confirmación.

2. **AC-2 (Precedencia absoluta de turnos vencidos incompletos)**:
   - **Dado** que el horario de cierre de una franja horaria ya pasó y aún existen comandas de dicho bloque sin finalizar,
   - **Cuando** inicia el siguiente turno horario del establecimiento,
   - **Entonces** las comandas incompletas del turno previo se mantienen al tope absoluto de la cola por precedencia cronológica y deben terminarse antes que las órdenes correspondientes al nuevo turno.

3. **AC-3 (Normalización de bloque operativo para pedidos inmediatos)**:
   - **Dado** que un comensal confirma una orden inmediata en el minuto `M` de una hora determinada,
   - **Cuando** se despacha la comanda a la cola de cocina,
   - **Entonces** el sistema le asigna como inicio de franja horaria el inicio del bloque operativo de 15 minutos en curso, calculado como `floor(M / 15) * 15`, garantizando una agrupación homogénea y consistente con los pedidos programados de ese mismo intervalo.

---

### Historia de usuario 2 — Iniciar preparación de comanda: transición a Columna 2 (Prioridad: P1)

**Identificador de referencia**: `CHF-4.1.1`  
**Persona**: Chef en la estación de cocción.

Como chef en la estación de cocción, quiero pulsar un botón "EMPEZAR" en la comanda compacta de pedidos pendientes, para pasar la orden a la columna de preparación activa, registrar el timestamp de inicio y notificar a los demás canales que comencé a trabajar en ella.

**Por qué tiene prioridad P1**: Desbloquea la transición operativa fundamental del flujo de trabajo de cocina, permitiendo distinguir visualmente las órdenes en espera de aquellas que ya tienen recursos asignados en cocción.

**Prueba independiente**: Puede verificarse seleccionando una comanda en Columna 1 y accionando el botón "EMPEZAR": la comanda desaparece de Columna 1, aparece en Columna 2 con su cronómetro en cero y la orden queda en estado en progreso ante consultas simultáneas.

**Escenarios de aceptación**:

1. **AC-1 (Visualización compacta en Columna 1)**:
   - **Dado** un ticket de comanda en estado pendiente de preparación,
   - **Cuando** se renderiza en la Columna 1 ("Pendientes"),
   - **Entonces** exhibe una tarjeta compacta únicamente con el número correlativo de ticket, el turno horario de retiro asignado y un botón prominente rotulado "EMPEZAR".

2. **AC-2 (Transición reactiva al iniciar preparación)**:
   - **Dado** que el chef pulsa el botón "EMPEZAR" sobre una comanda de la Columna 1,
   - **Cuando** el sistema procesa la acción de inicio de preparación,
   - **Entonces** el ticket transiciona a estado "en preparación", se registra el instante exacto de inicio, la orden asociada queda asegurada en estado "en progreso", la tarjeta se traslada inmediatamente a la Columna 2 mostrando el desglose completo de platos y se activa el cronómetro progresivo Count-Up.

3. **AC-3 (Idempotencia ante peticiones concurrentes o reintentos)**:
   - **Dado** que una orden ya se encuentra en estado "en preparación",
   - **Cuando** se recibe una petición concurrente o un reintento de red para iniciar la preparación de esa misma comanda,
   - **Entonces** el sistema confirma la operación de manera exitosa sin modificar el instante de inicio registrado originalmente ni generar duplicados de la tarjeta.

---

### Historia de usuario 3 — Lectura ergonómica de comanda táctil (Prioridad: P2)

**Identificador de referencia**: `CHF-3.2.1`  
**Persona**: Chef operando la pantalla táctil de cocina.

Como chef operando en la pantalla táctil de cocina, quiero leer de forma clara y sin ruido visual el número de ticket, el turno horario y el listado de platos con sus cantidades en tipografía de alto contraste, para preparar la comida con precisión y a distancia visual sin distracciones.

**Por qué tiene prioridad P2**: Garantiza la ergonomía y usabilidad visual en condiciones de operación real en cocina (distancia de un metro, iluminación variable y vapor), previniendo errores en las cantidades y platos elaborados.

**Prueba independiente**: Puede comprobarse visualizando tarjetas de Columna 2 con múltiples ítems y cantidades variadas, comprobando que la información de cada plato sigue el formato unificado y que no existen contenedores residuales de notas.

**Escenarios de aceptación**:

1. **AC-1 (Formato unificado y legibilidad a distancia)**:
   - **Dado** que un ticket en preparación se renderiza en la pantalla táctil del KDS,
   - **Cuando** el personal de cocina visualiza la comanda en la Columna 2,
   - **Entonces** observa de forma destacada el número correlativo de ticket en tamaño prominente (`text-2xl font-black`), el turno horario asignado en formato `HH:mm - HH:mm` y la lista de platos requeridos bajo el formato unificado `[Cantidad]x [Nombre del Plato]`, en tipografía de alto contraste legible a un metro de distancia e inmune a selecciones involuntarias de texto (`select-none`).

2. **AC-2 (Ausencia de elementos residuales o placeholders de texto libre)**:
   - **Dado** que la plataforma no captura modificaciones de texto libre arbitrarias,
   - **Cuando** se visualiza la tarjeta de comanda en cualquier columna del KDS,
   - **Entonces** la tarjeta no contiene placeholders vacíos (tales como "Sin notas" u "Observaciones: ninguna") ni contenedores residuales, maximizando el espacio de visualización para los platos a elaborar.

---

### Historia de usuario 4 — Cronómetro progresivo de preparación en minutos: Count-Up (Prioridad: P2)

**Identificador de referencia**: `CHF-4.1.4`  
**Persona**: Chef que supervisa comandas en preparación activa.

Como chef que supervisa las comandas en preparación activa, quiero ver en la cabecera de cada comanda de la Columna 2 los minutos enteros que lleva cocinándose, para controlar el tiempo transcurrido de forma serena y sin la distracción continua de un segundero.

**Por qué tiene prioridad P2**: Facilita el monitoreo temporal sostenido sin generar estrés ni fatiga visual producida por contadores de segundos parpadeantes en múltiples tarjetas simultáneas.

**Prueba independiente**: Puede verificarse observando una comanda en Columna 2 en el minuto 0 (`0 min`), verificando que cambia a `1 min` al cumplirse 60 segundos acumulados, y recargando el navegador o abriendo otra terminal para certificar que el valor exhibido se mantiene exacto.

**Escenarios de aceptación**:

1. **AC-1 (Visualización en minutos enteros calculada contra servidor)**:
   - **Dado** un ticket en preparación en la Columna 2 con su instante de inicio persistido,
   - **Cuando** se visualiza su tarjeta en el KDS,
   - **Entonces** exhibe en su cabecera un contador progresivo con el cálculo `floor((ahora - instante_inicio) / 60)` expresado en formato `MM min`, iniciando en `0 min` y actualizándose cada 60 segundos.

2. **AC-2 (Persistencia ante recarga y visualización multi-dispositivo)**:
   - **Dado** un ticket que acumula `M` minutos en cocción,
   - **Cuando** se recarga el navegador web o se visualiza la comanda desde otra pantalla o tableta conectada en la cocina,
   - **Entonces** la interfaz calcula los minutos contra el instante de inicio persistido en el servidor, exhibiendo exactamente el mismo valor de tiempo transcurrido `M min` sin reiniciarse a cero.

---

### Historia de usuario 5 — Alerta visual por demora excesiva en KDS: umbral de 15 minutos (Prioridad: P2)

**Identificador de referencia**: `CHF-4.1.5`  
**Persona**: Chef que supervisa comandas en preparación activa.

Como chef trabajando en una cocina con alta demanda, quiero que la tarjeta en preparación destaque su contorno e insignia en color ámbar/amarillo al superar los 15 minutos, para identificar de un solo vistazo qué órdenes se están demorando más de lo previsto sin tapar la visibilidad de los platos.

**Por qué tiene prioridad P2**: Provee una señal preventiva clara ante retrasos en la línea de elaboración, permitiendo al equipo reasignar esfuerzos antes de que la demora afecte negativamente al comensal en mostrador.

**Prueba independiente**: Puede comprobarse monitoreando una tarjeta a los 14 minutos (estilo neutral) y al alcanzar exactamente los 15 minutos: el borde exterior y la insignia de tiempo conmutan a ámbar/amarillo mientras que los nombres y cantidades de los platos mantienen tipografía nítida sobre fondo estándar.

**Escenarios de aceptación**:

1. **AC-1 (Estado neutral estándar previo a los 15 minutos)**:
   - **Dado** un ticket en Columna 2 cuyo tiempo transcurrido de preparación es estrictamente inferior a 15 minutos (`tiempo < 15 min`),
   - **Cuando** se renderiza la tarjeta en la pantalla del KDS,
   - **Entonces** exhibe el estilo perimetral neutral estándar y la insignia regular de tiempo transcurrido de la comanda.

2. **AC-2 (Activación reactiva de alerta perimetral ámbar a partir de 15 minutos)**:
   - **Dado** que el cronómetro de preparación alcanza o supera los 15 minutos (`tiempo >= 15 min`),
   - **Cuando** se actualiza el contador de tiempo transcurrido,
   - **Entonces** el contorno exterior de la tarjeta y la insignia del tiempo cambian reactivamente a color ámbar/amarillo de advertencia, manteniendo el cuerpo interior de la comanda con tipografía nítida y legible sin teñir el fondo de los textos.

---

### Historia de usuario 6 — Limpieza y ergonomía del entorno de trabajo KDS (Prioridad: P2)

**Identificador de referencia**: `CHF-4.1.2`  
**Persona**: Chef operando la pantalla táctil de cocina.

Como chef en la estación de cocina, quiero disponer de un entorno visual despejado y libre de marcadores provisionales o controles ajenos al KDS, con un menú de navegación compacto tipo hamburguesa y un pie de página con los datos de sesión y local, para maximizar el área de visualización del tablero Kanban táctil sin distracciones operativas.

**Por qué tiene prioridad P2**: Elimina la deuda técnica visual acumulada por placeholders de versiones previas, optimiza la ergonomía en monitores táctiles y centraliza la navegación de cocina sin sobrecargar la cabecera.

**Prueba independiente**: Puede comprobarse ingresando a `/cocina` con rol `CHEF`: la cabecera muestra únicamente "EasyEat" y el menú hamburguesa con las 3 secciones; la página contiene exclusivamente el tablero Kanban (sin bloques de demora, despacho manual, ingredientes ni métricas); y el pie de página exhibe el nombre comercial del local, la insignia CHEF, el correo electrónico y la acción de logout.

**Escenarios de aceptación**:

1. **AC-1 (Cabecera simplificada con menú desplegable para rol CHEF)**:
   - **Dado** que un usuario con rol `CHEF` accede a las vistas de cocina (`/cocina`, `/cocina/platos`, `/cocina/ingredientes`),
   - **Cuando** se renderiza la cabecera superior,
   - **Entonces** se omite el título textual redundante del KDS y el enlace de avatar/perfil, exhibiendo un botón de menú desplegable (hamburguesa) que al accionarse despliega las rutas "Comandas KDS", "Gestionar Platos" e "Ingredientes" con indicador `aria-current="page"` para la ruta activa.

2. **AC-2 (Pie de página operativo con datos del establecimiento y sesión)**:
   - **Dado** un usuario autenticado con rol `CHEF`,
   - **Cuando** se visualiza el pie de página de la aplicación,
   - **Entonces** exhibe el nombre comercial del local obtenido desde el branding del establecimiento (o el identificador corto como fallback), la insignia de rol `CHEF`, el correo electrónico del usuario y el botón "Cerrar sesión".

3. **AC-3 (Supresión de placeholders y foco exclusivo en Kanban)**:
   - **Dado** que el chef opera sobre la vista principal del KDS (`/cocina`),
   - **Cuando** la página completa su carga,
   - **Entonces** no se visualizan el subtítulo de página, el control provisional de demora global, el formulario manual de despacho por UUID, la tarjeta estática de catálogo de ingredientes, el banner de rechazo de platos ni las cuatro tarjetas estadísticas de métricas del turno, ocupando el tablero `KitchenKanbanBoard` la totalidad del área operativa.

---

## Requisitos funcionales

- **RF-001**: El sistema debe organizar las comandas activas del KDS en dos columnas operativas exclusivas: Columna 1 ("Pendientes") para órdenes no iniciadas y Columna 2 ("En preparación") para órdenes en elaboración activa.
- **RF-002**: El sistema debe ordenar determinísticamente las comandas aplicando el siguiente criterio de prelación ascendente: primero por fecha de retiro, segundo por hora de inicio de la franja horaria asignada, y tercero por momento de encolado de la orden en cocina. La visualización activa de producción del KDS debe limitar su alcance a la fecha operativa actual (`hoy`) más comandas incompletas de turnos de fechas previas si existieran, excluyendo pedidos programados para fechas futuras.
- **RF-003**: El sistema debe tratar bajo idéntica prioridad y regla FIFO dentro de una misma franja horaria tanto a pedidos de modalidad inmediata como a pedidos de modalidad programada.
- **RF-004**: El sistema debe mantener las comandas incompletas pertenecientes a turnos horarios ya finalizados en el tope absoluto de la cola de preparación, otorgándoles precedencia sobre todas las comandas pertenecientes al nuevo turno en curso o futuros.
- **RF-005**: Al recibir un pedido inmediato confirmado en el minuto `M`, el sistema debe asignarle como hora de inicio de franja el inicio del bloque operativo de 15 minutos en curso, computado como `floor(M / 15) * 15`.
- **RF-006**: En la Columna 1 ("Pendientes"), el sistema debe presentar las comandas en un contenedor con desplazamiento vertical (scroll táctil continuo) sin paginación por páginas, exhibiendo cada comanda en una tarjeta compacta que contiene el número de ticket destacado en tipografía de alto contraste, el turno horario en formato `HH:mm - HH:mm` y un botón de acción rotulado "EMPEZAR" con área táctil optimizada de al menos 56px de altura y tipografía prominente para facilitar la interacción con dedos enguantados o húmedos. Los pedidos del día en curso quedan accesibles por desplazamiento según su orden cronológico.
- **RF-007**: Al accionar el botón "EMPEZAR", el sistema debe registrar el instante de inicio, cambiar el estado del ticket a "en preparación", asegurar el estado de la orden asociada en "en progreso" y trasladar de inmediato la tarjeta a la Columna 2.
- **RF-008**: La acción de iniciar preparación debe ser idempotente: si se reciben peticiones repetidas o concurrentes sobre un ticket ya en preparación, el sistema debe responder exitosamente y preservar el instante de inicio original sin reiniciarlo.
- **RF-009**: En la Columna 2 ("En preparación"), el sistema debe exhibir la tarjeta expandida conteniendo el número de ticket, el turno horario `HH:mm - HH:mm`, el cronómetro progresivo y la lista completa de platos requeridos bajo el formato `[Cantidad]x [Nombre del Plato]`.
- **RF-010**: Las tarjetas de comanda en ambas columnas deben omitir cualquier campo vacío, texto de marcador de posición o contenedor de notas o modificaciones libres.
- **RF-011**: El sistema debe calcular el tiempo transcurrido de cada comanda en Columna 2 como `floor((ahora - instante_inicio) / 60)` y exhibirlo en formato `MM min`, comenzando en `0 min` y refrescándolo periódicamente cada 60 segundos.
- **RF-012**: El tiempo transcurrido en pantalla debe derivarse invariablemente del instante de inicio registrado por el servidor, garantizando que recargas de página o múltiples terminales sincronicen el mismo valor exacto.
- **RF-013**: Mientras el tiempo transcurrido sea menor a 15 minutos, la tarjeta de Columna 2 debe mantener un contorno visual neutral estándar.
- **RF-014**: Al alcanzar o superar los 15 minutos inclusive (`tiempo >= 15 min`), el sistema debe destacar el borde exterior de la tarjeta y la insignia del cronómetro en color ámbar/amarillo de alerta, sin alterar el contraste ni la legibilidad de la lista de platos.
- **RF-015**: La pantalla del KDS debe actualizar de manera autónoma y continua la cola activa mediante consultas periódicas silenciosas a intervalos cortos (entre 3 y 5 segundos) para reflejar nuevos pedidos y transiciones de estado concurrentes sin requerir interacción manual del personal ni recargas de página.
- **RF-016**: Cuando la Columna 1 o la Columna 2 no contengan comandas activas, el sistema debe preservar la estructura fija de dos columnas y renderizar en la columna correspondiente un mensaje neutral informativo (tales como "Sin pedidos pendientes" o "Sin pedidos en preparación"), evitando colapsos visuales o saltos bruscos en el diseño de pantalla.
- **RF-017**: En las vistas del rol CHEF, la cabecera superior debe ocultar el título textual redundante del KDS y el enlace de avatar de perfil, ofreciendo un menú desplegable tipo hamburguesa accesible para conmutar entre "Comandas KDS", "Gestionar Platos" e "Ingredientes".
- **RF-018**: La interfaz de cocina para el rol CHEF debe renderizar un pie de página operativo conteniendo el nombre comercial del establecimiento obtenido mediante el branding del local (con fallback al ID corto), la insignia de rol CHEF, el email del usuario autenticado y el botón de cierre de sesión.
- **RF-019**: La página de cocina (`/cocina`) debe eliminar los bloques de ajuste manual de demoras, el despacho de órdenes por UUID, la tarjeta estática de acceso a ingredientes, el banner de rechazo de platos, el subtítulo y las cuatro tarjetas estadísticas de métricas del turno, reservando el 100% del área de trabajo para el tablero Kanban de comandas activas.

---

## Invariantes y reglas deterministas

1. **Ordenamiento y alcance de cola determinista**: La cola activa de KDS abarca exclusivamente pedidos cuya fecha de retiro sea menor o igual a la fecha operativa actual ($\text{fechaRetiro} \le \text{hoy}$). Para cualquier par de tickets $T_A$ y $T_B$ en la cola, $T_A$ precede a $T_B$ si y solo si:
   - $\text{fechaRetiro}(T_A) < \text{fechaRetiro}(T_B)$, o
   - $(\text{fechaRetiro}(T_A) = \text{fechaRetiro}(T_B)) \land (\text{inicioTurno}(T_A) < \text{inicioTurno}(T_B))$, o
   - $(\text{fechaRetiro}(T_A) = \text{fechaRetiro}(T_B)) \land (\text{inicioTurno}(T_A) = \text{inicioTurno}(T_B)) \land (\text{instanteEncolado}(T_A) < \text{instanteEncolado}(T_B))$.
2. **Equivalencia de modalidades en FIFO**: La modalidad del pedido (inmediata o programada) no altera su posición dentro de una misma franja horaria. El desempate dentro de la franja se rige pura y exclusivamente por el instante de encolado.
3. **Precedencia de turnos vencidos**: Las comandas no finalizadas cuyo turno horario de retiro sea anterior al turno en curso poseen precedencia sobre cualquier orden del turno actual, independientemente de su momento de encolado.
4. **Normalización uniforme de pedidos inmediatos**: Para toda orden inmediata ingresada en el instante con minutos $M \in [0, 59]$, el inicio de su turno operativo satisface $\text{minutoInicio} = \lfloor M / 15 \rfloor \times 15 \in \{0, 15, 30, 45\}$ y su fin de turno satisface $\text{finTurno} = \text{inicioTurno} + 15 \text{ min}$.
5. **Inmutabilidad del instante de inicio e idempotencia**: Una vez fijado el instante de inicio al presionar "EMPEZAR", dicho valor es inmutable y no puede ser modificado por peticiones posteriores, reintentos o clics repetidos.
6. **Tiempo observable y sincronización desacoplada**: La sincronización de estados del KDS opera mediante consultas periódicas sin estado que preservan la alta cohesión del frontend. El cronómetro de comanda no reside en temporizadores locales volátiles; es una función pura del tiempo actual del sistema y el instante de inicio persistido en el servidor.
7. **Activación no destructiva de alerta perimetral**: El estado de alerta a los 15 minutos afecta únicamente el perímetro exterior de la tarjeta y la insignia del tiempo, preservando en todo momento la legibilidad y tipografía del cuerpo de platos.

---

## Entidades clave

- **Ticket de cocina (Comanda KDS)**: Representa la unidad operativa de trabajo visible en el KDS. Contiene el número correlativo de ticket visible, la orden de compra asociada, la fecha de retiro, el turno horario (`horaInicio` y `horaFin`), el instante de encolado en cocina, el estado en cocina (`PENDIENTE` o `EN_PREPARACION`), el instante de inicio de preparación (fijado al transicionar) y la colección de platos requeridos.
- **Ítem de comanda**: Elemento integrante de una comanda. Posee el nombre comercial del plato y la cantidad entera requerida para la orden.
- **Turno horario de retiro**: Bloque temporal de 15 minutos caracterizado por una fecha, una hora de inicio y una hora de fin (`horaInicio + 15 min`), utilizado como unidad de agrupación y ordenamiento para la preparación.

---

## Criterios de éxito y resultados medibles

- **SC-001**: El 100% de las comandas desplegadas en el KDS se posicionan conforme a la regla determinista de ordenamiento sin requerir reordenamiento manual por parte del personal de cocina.
- **SC-002**: El tiempo visualizado en el cronómetro de preparación coincide exactamente entre múltiples dispositivos o pantallas de cocina conectadas, con una discrepancia máxima de actualización de 60 segundos debido a la sincronización por minuto.
- **SC-003**: La transición de una comanda de Columna 1 a Columna 2 se refleja visualmente de manera inmediata tras la pulsación de "EMPEZAR", sin duplicación de tarjetas ni pérdida de información de platos.
- **SC-004**: El personal de cocina situado a un metro de distancia de la pantalla puede identificar el número de ticket, el turno y los platos requeridos sin necesidad de interactuar físicamente con la tarjeta.
- **SC-005**: Al alcanzar los 15 minutos de preparación, el 100% de las comandas demoradas exhiben el contorno ámbar/amarillo de advertencia de manera automática.

---

## Casos borde

1. **Convivencia de pedidos inmediatos y programados en la misma franja**: Un pedido programado para las 13:00 reservado horas antes y un pedido inmediato confirmado a las 13:04 se agrupan en el bloque 13:00 - 13:15. El pedido programado se ubica antes en la cola debido a que su momento de confirmación y encolado fue anterior.
2. **Turnos vencidos con comandas incompletas**: Si son las 13:20 y quedan dos comandas del turno 13:00 - 13:15 sin finalizar, estas dos comandas encabezan la cola por encima de todas las órdenes del bloque 13:15 - 13:30.
3. **Recarga de pantalla y visualización simultánea en varias terminales**: Si el cocinero recarga el navegador de la tableta en el minuto 7 de preparación, o si un segundo cocinero abre el KDS desde otra tableta en ese mismo instante, ambas pantallas consultan el instante de inicio y muestran inmediatamente `7 min` sin resetearse a `0 min`.
4. **Peticiones concurrentes o reintentos de red al pulsar "EMPEZAR"**: Si dos operadores pulsan simultáneamente el botón sobre la misma comanda o si la red reintenta la llamada, la primera petición fija el instante de inicio y transiciona la orden; las subsiguientes confirman el estado exitosamente sin alterar dicho instante ni crear duplicados en Columna 2.
5. **Comanda en el umbral exacto de 15 minutos**: Al transcurrir el segundo 0 del minuto 15 (`tiempo = 15 min`), la comanda activa de inmediato el borde ámbar y la insignia de alerta. Al minuto 14 con 59 segundos mantiene su estado neutral.
6. **Cambio de minuto durante la visualización activa**: Durante la jornada operativa en cocina, cada vez que el reloj cumple un minuto adicional respecto al instante de inicio, el valor mostrado en `MM min` incrementa en una unidad de forma reactiva sin requerir refresco manual de la página.
7. **Tickets con múltiples platos y cantidades elevadas**: Cuando una comanda incluye varios platos diferentes con cantidades diversas (por ejemplo, `3x Ensalada César`, `1x Sopa del Día`, `2x Tarta de Verduras`), cada ítem se lista en un renglón dedicado bajo el formato `[Cantidad]x [Nombre]` garantizando alto contraste y legibilidad sin truncamiento de nombres.
8. **Comandas sin campos de notas o texto libre**: Dado que no existen observaciones particulares ingresadas por el comensal, la comanda no presenta renglones en blanco, textos "Sin observaciones" ni separadores vacíos, maximizando el espacio para los ítems a cocinar.
9. **Columnas vacías sin pedidos**: Cuando no existan pedidos en estado pendiente o en preparación, ambas columnas conservan su división y ancho relativo estándar, mostrando sus respectivos mensajes neutrales de estado vacío ("Sin pedidos pendientes" o "Sin pedidos en preparación") sin bloquear la llegada y renderizado reactivo de nuevas comandas.

---

## Supuestos

- Se asume que las órdenes que arriban al KDS ya cuentan con confirmación de pago válida y se encuentran listas para su elaboración física.
- Se asume que las pantallas táctiles de cocina cuentan con una resolución suficiente para visualizar de manera clara las dos columnas operativas de trabajo simultáneamente.
- Se asume que el reloj de las terminales de cocina y el reloj del servidor se encuentran sincronizados mediante protocolos estándar de tiempo.

---

## Fuera de alcance

- Botón de demoras `+5` (funcionalidad asignada a la especificación posterior de seguimiento y demoras).
- Incorporación de nuevas columnas en el tablero Kanban más allá de la Columna 1 ("Pendientes") y Columna 2 ("En preparación") (la salida de pedidos listos hacia mostrador corresponde a la especificación de despacho).
- Edición, modificación o tachado individual de platos dentro de la comanda en cocina.
- Captura o edición de notas de texto libre arbitrarias asociadas al pedido.
- Notificaciones a canales externos, integración con pasarelas de pago o mecanismos de liquidación de fondos.
- Módulos de autenticación, autorización o gestión de sesiones de usuario.
- Detalles de implementación técnica tales como esquemas relacionales, endpoints de API, librerías gráficas o frameworks de componentes.

---

## Preguntas abiertas

No se identifican ambigüedades ni preguntas abiertas bloqueantes. Los criterios de aceptación, reglas de negocio y restricciones funcionales se encuentran exhaustiva y unívocamente especificados.
