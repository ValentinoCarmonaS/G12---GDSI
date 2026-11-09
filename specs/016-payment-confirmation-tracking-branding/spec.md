# Especificación funcional: confirmación de pago, tiempo estimado en vivo y branding del restaurante

**Rama de la feature**: `016-payment-confirmation-tracking-branding`  
**Fecha de creación**: 09/11/2026  
**Estado**: Borrador (Alineado con Grill-Me)  
**Entrada**: Descripción del usuario: "Feature: Confirmación de pago, tiempo estimado en vivo y branding del restaurante (Spec 016) - Spec ID: 016"

---

## Contexto y trazabilidad funcional

- **Historias de usuario fuente obligatorias**:
  - `COM-7.3.1` — Confirmación de pago (Prioridad P1).
  - `COM-7.3.2` — Tiempo estimado en vivo (Prioridad P1).
  - `ADM-2.1.1` — Configurar branding del restaurante (Prioridad P2).
- **Trazabilidad funcional y dependencias de producto**:
  - Esta especificación se apoya estrictamente sobre los flujos operacionales consolidados en iteraciones previas de EasyEat:
    - Carrito de compras individual y persistencia de sesión local (definido en Spec 009).
    - Configuración horaria de apertura, cierre y capacidades de atención del establecimiento (definido en Spec 010).
    - Motor de selección de franjas horarias y reserva de turnos con ventana temporal de 15 minutos (definido en Spec 012).
    - Salvaguardas de disponibilidad y pausa manual de platos por cocina (definido en Spec 013).
    - Flujo de pago con Mercado Pago en checkout, resultado de pago con autoridad externa, idempotencia de retornos y no captura de datos bancarios (definido en Spec 014).
    - Pago de carrito individual, descuento de stock transaccional por receta, despacho automático a la cola del KDS y transición a estado en curso (definido en Spec 015).
  - La presente Spec 016 absorbe y resuelve la experiencia posterior a la confirmación del pago para el comensal: despliegue de la pantalla verde de éxito con número de orden, envío garantizado de comprobante por correo electrónico ante interrupciones de navegación, detalle de franja de retiro con seguimiento en vivo, bloqueo de interacciones preventivo durante el procesamiento, cálculo dinámico del tiempo estimado según saturación del KDS para retiro inmediato, exhibición de horario pactado con activación de cuenta regresiva para reservas programadas, actualización automática ante demoras y ajustes globales del KDS, y cambio destacado a estado "listo para retirar" por mostrador.
  - Asimismo, incorpora la personalización visual de la marca del restaurante desde el módulo administrativo "Mi Local" (logotipo, nombre de fantasía, descripción y paleta de colores primarios), asegurando que la interfaz del comensal adopte dinámicamente dicha identidad al ingresar a pedir en el establecimiento correspondiente.
- **Límites estrictos de alcance**:
  - No se agregan historias, actores, flujos, estados de orden, canales de notificación, integraciones, pasarelas de pago, monedas, mecanismos de reembolso, conciliación, autenticación, autorización ni avisos push no derivados estrictamente de COM-7.3.1, COM-7.3.2 y ADM-2.1.1.

---

## Clarificaciones

### Sesión 09/11/2026 (Grill-Me & Clarify)

- **Q1: Activación de la cuenta regresiva en reservas programadas (COM-7.3.2, Criterio 6)**: Para las órdenes con franja horaria programada a futuro, ¿con qué antelación respecto al turno acordado debe comenzar la cuenta regresiva en lugar de exhibir el horario pactado estático?  
  → **A1**: La cuenta regresiva dinámica se activa exactamente 15 minutos antes del inicio de la franja horaria pactada para el retiro. Este umbral es consistente con la duración de los bloques unitarios de franjas y con la ventana temporal estándar definida en las especificaciones operativas previas (Spec 010 y Spec 012). Mientras falten más de 15 minutos para la franja, la pantalla exhibe de forma fija la fecha y el horario acordado de retiro.

- **Q2: Convivencia y persistencia entre pantalla de confirmación (COM-7.3.1) y seguimiento en vivo (COM-7.3.2)**: Si el comensal recarga la pantalla de confirmación, vuelve atrás en el navegador o reingresa al pedido (por ejemplo, desde el enlace de su correo electrónico o su historial de pedidos), ¿cómo interactúan la pantalla verde de éxito y el seguimiento en vivo?  
  → **A2**: Ambas experiencias conviven de manera unificada y persistente. La pantalla de confirmación contiene de forma permanente el estado de aprobación verde, el número de orden, el detalle de la franja horaria y el componente de seguimiento en vivo con su avance actualizado (progreso, tiempo dinámico o estado listo para retirar), garantizando que el usuario conserve la tranquilidad de su comprobante visual y el control en tiempo real en una misma experiencia unificada ante cualquier recarga o reingreso.

- **Q3: Fallback visual ante ausencia de configuración de branding previa (ADM-2.1.1, Criterio 9)**: Si un administrador aún no ha configurado o guardado los elementos de marca en "Mi Local", ¿qué identidad visual visualiza el comensal al entrar a pedir?  
  → **A3**: El sistema aplica la identidad visual base institucional de EasyEat: paleta de colores neutra predeterminada de la plataforma, isotipo por defecto del sistema y el nombre comercial registrado durante el alta operativa del establecimiento, asegurando coherencia y legibilidad sin bloquear la operación comercial.

- **Q4: Política de emisión del correo de comprobante y cobertura ante interrupciones (COM-7.3.1, Criterio 2)**: ¿Se emite el correo electrónico únicamente si se detecta una interrupción en el navegador, o se adopta un despacho universal ante toda orden aprobada?  
  → **A4**: Se adopta el despacho universal e incondicional: todo pago aprobado genera y envía automáticamente en segundo plano el correo electrónico con el comprobante formal, detalle de la orden y enlace de seguimiento. Esta política garantiza al 100% que ante cualquier cierre de navegador, corte de conexión o interrupción previa a la carga de la pantalla verde, el comensal disponga de su comprobante en su bandeja de entrada, proveyendo al mismo tiempo un respaldo transaccional estándar para todos los usuarios.

- **Q5: Selección y accesibilidad de colores primarios en "Mi Local" (ADM-2.1.1, Criterio 9)**: ¿Cómo debe estructurarse la selección de la paleta de colores primarios para asegurar la coherencia estética y legibilidad en la vista del comensal?  
  → **A5**: Se adopta selección libre mediante código hexadecimal (formato `#RRGGBB`) con cálculo automático de contraste: el sistema evalúa la luminosidad del color seleccionado para determinar dinámicamente si los textos e iconos sobre dicho fondo deben ser oscuros o claros, garantizando siempre el cumplimiento de contraste mínimo accesible y legibilidad en botones, barras y destacados visuales.

- **Q6: Comportamiento visual del reloj ante ajustes globales de tiempos del KDS (COM-7.3.2, Criterio 7)**: ¿Cómo debe presentarse visualmente al comensal el reajuste temporal cuando el personal de cocina incrementa o ajusta los tiempos de preparación globales?  
  → **A6**: Se adopta la actualización silenciosa del reloj numérico: el sistema actualiza de manera fluida y en vivo el valor de los minutos restantes en el reloj dinámico sin introducir carteles de alerta, popups ni distintivos adicionales, manteniendo la interfaz despejada y limpia mientras refleja fielmente el nuevo escenario operativo.

- **Q7: Desglose de la tríada de colores de marca en "Mi Local" (ADM-2.1.1, Criterio 9)**: ¿Qué colores específicos componen la selección de "colores primarios" que el administrador configura para la identidad visual de su restaurante?  
  → **A7**: La configuración cromática se define mediante tres colores explícitos en formato hexadecimal (`#RRGGBB`), cada uno con cálculo automático de contraste para textos e iconos asociados:
  1. **Color Primario**: para botones de acción principal (CTA, confirmar orden, agregar al carrito) y acciones clave.
  2. **Color Secundario**: para la cabecera del establecimiento, barras superiores y fondos destacados de sección.
  3. **Color de Acento**: para insignias (badges), etiquetas de estado, píldoras informativas y bordes de selección activa.

---

## Propósito

Brindar al comensal certeza absoluta e inmediata sobre la aprobación de su pago mediante una confirmación visual inequívoca, respaldada por un comprobante garantizado vía correo electrónico ante interrupciones, proporcionando visibilidad continua y en tiempo real del progreso de preparación de su comida —tanto para retiros inmediatos calculados según la saturación de la cocina como para reservas programadas—, y permitir a los administradores de los restaurantes plasmar su identidad visual de marca en la experiencia de pedido de sus clientes.

---

## Actores

- **Comensal**: Usuario que ha completado el pago de su orden a través de la pasarela y requiere confirmación inmediata de la transacción, certeza sobre su turno de entrega y seguimiento dinámico del estado de preparación antes de acercarse al mostrador.
- **Personal de KDS (Cocina y Mostrador)**: Equipo operativo del restaurante que gestiona la cola de elaboración, ajusta tiempos de preparación globales ante eventualidades del servicio y marca los pedidos completados para su entrega en mostrador.
- **Administrador del restaurante**: Responsable comercial que gestiona los parámetros del establecimiento en el módulo "Mi Local", definiendo logotipo, nombre de fantasía, descripción y paleta cromática primaria para transmitir la identidad de su marca a los comensales.

---

## Escenarios de aceptación y pruebas

### User Story 1 — COM-7.3.1: Confirmación de pago (Prioridad: P1)

Como comensal,  
quiero ver una pantalla verde de éxito apenas el banco aprueba el pago,  
para quedarme tranquilo de que la transacción funcionó.

**Por qué esta prioridad**: Es el cierre inmediato del proceso de compra. Otorga al usuario certeza jurídica y operativa sobre la recepción de sus fondos y la creación efectiva de su orden, evitando ansiedad, abandonos y reclamos por cobros inciertos.

**Prueba independiente**: Se puede validar simulando una respuesta de pago aprobada desde la pasarela, comprobando la redirección inmediata a la pantalla verde con animación, número de orden visible, detalle de la franja horaria pactada, botón de seguimiento activo, bloqueo preventivo ante clics repetidos y emisión autónoma del comprobante por correo electrónico si se interrumpe el navegador.

#### Escenarios de aceptación

- **AS-1 (Criterio fuente 1 — Redirección a pantalla verde con animación y número de orden)**:  
  **Dado** que la pasarela de pagos retorna un código de aprobación exitoso,  
  **Cuando** el sistema procesa la respuesta,  
  **Entonces** redirige al comensal a una pantalla verde de éxito con animaciones visuales y le muestra el número de orden generado.

- **AS-2 (Criterio fuente 2 — Garantía de comprobante por correo electrónico ante interrupción)**:  
  **Dado** que el pago es aprobado exitosamente por la pasarela,  
  **Cuando** el navegador del comensal sufre una interrupción (cierre de ventana, caída de conexión o recarga) antes de cargar la pantalla de éxito,  
  **Entonces** el sistema garantiza el envío de un correo electrónico a la casilla registrada del comensal con el comprobante, el detalle de la orden y el enlace de seguimiento.

- **AS-3 (Criterio fuente 3 — Detalle de franja horaria y botón de seguimiento en vivo)**:  
  **Dado** que el comensal visualiza la pantalla de confirmación,  
  **Cuando** revisa la información mostrada,  
  **Entonces** el sistema le detalla explícitamente la franja horaria de retiro elegida y habilita un botón para hacer seguimiento en vivo.

- **AS-4 (Criterio fuente 4 — Bloqueo de interacciones durante el procesamiento para evitar cobros duplicados)**:  
  **Dado** que el sistema se encuentra procesando el pago y esperando la respuesta de la pasarela,  
  **Cuando** el comensal presiona múltiples veces el botón de confirmación,  
  **Entonces** la interfaz bloquea las interacciones previniendo cobros duplicados.

---

### User Story 2 — COM-7.3.2: Tiempo estimado en vivo (Prioridad: P1)

Como comensal esperando,  
quiero ver en la pantalla de mi orden el tiempo estimado para que me la entreguen,  
para saber cuánto tiempo libre tengo antes de ir a mostrador.

**Por qué esta prioridad**: Resuelve la incertidumbre del comensal durante la elaboración de su comida. Optimiza el flujo físico en el salón y mostrador al sincronizar la llegada del comensal con el momento exacto en que la orden está lista, evitando aglomeraciones.

**Prueba independiente**: Se puede verificar accediendo a la vista de seguimiento con una orden de retiro inmediato observando la barra de progreso y tiempo dinámico según saturación de cocina; con una reserva programada observando el horario fijo hasta el umbral de 15 minutos; modificando los tiempos de preparación en el KDS y verificando el ajuste reactivo del reloj del comensal; y marcando la orden como finalizada en el KDS para constatar la desaparición del tiempo y el destaque del aviso de retiro.

#### Escenarios de aceptación

- **AS-5 (Criterio fuente 5 — Progreso y tiempo estimado dinámico para retiro inmediato según saturación KDS)**:  
  **Dado** que el comensal confirmó un pedido de retiro inmediato,  
  **Cuando** ingresa a la sección de seguimiento,  
  **Entonces** visualiza una barra de progreso y un tiempo estimado de entrega calculado dinámicamente según la saturación actual de la cola en el KDS.

- **AS-6 (Criterio fuente 6 — Horario pactado estático y activación de cuenta regresiva al acercarse el turno)**:  
  **Dado** que el comensal posee una reserva programada para una franja horaria futura,  
  **Cuando** revisa su pedido,  
  **Entonces** el sistema muestra el horario de retiro pactado en lugar de un contador en vivo, activando la cuenta regresiva únicamente al acercarse el turno (al restar 15 minutos o menos para el inicio de la franja horaria acordada).

- **AS-7 (Criterio fuente 7 — Actualización automática del reloj dinámico ante ajustes globales en KDS)**:  
  **Dado** que el pedido sufre demoras operativas imprevistas en la cocina,  
  **Cuando** el personal del KDS ajusta los tiempos de preparación globales,  
  **Entonces** el sistema actualiza automáticamente el reloj dinámico en la pantalla del comensal para reflejar el nuevo escenario sin que este deba refrescar manualmente la vista.

- **AS-8 (Criterio fuente 8 — Desaparición de tiempo y estado destacado listo para retirar al finalizar en KDS)**:  
  **Dado** que el personal de cocina marca el pedido como finalizado en el KDS,  
  **Cuando** el sistema recibe la actualización,  
  **Entonces** el tiempo estimado desaparece y la pantalla cambia a un estado destacado indicando que el comensal ya puede retirar su comida por mostrador.

---

### User Story 3 — ADM-2.1.1: Configurar branding del restaurante (Prioridad: P2)

Como administrador,  
quiero subir el logo, editar el nombre de fantasía, la descripción y elegir los colores primarios,  
para que la vista del comensal respete la identidad visual de mi marca.

**Por qué esta prioridad**: Pertenece al dominio de configuración visual y fidelización comercial. Si bien no bloquea la realización funcional de los pedidos, garantiza la diferenciación estética y la presencia de marca de cada restaurante en un ecosistema multi-tenant.

**Prueba independiente**: Se puede probar accediendo con rol de Administrador a la sección "Mi Local", cargando un archivo de logotipo válido, editando nombre de fantasía, descripción y seleccionando la tríada de colores primarios de marca (primario para acciones, secundario para cabecera y acento para detalles) en formato hexadecimal `#RRGGBB`, guardando los cambios y verificando de inmediato que un comensal al entrar al catálogo o vistas de dicho restaurante visualice los elementos de marca y contraste actualizados.

#### Escenarios de aceptación

- **AS-9 (Criterio fuente 9 — Aplicación de branding configurado en la vista del comensal)**:  
  **Dado** que el administrador guarda los cambios en "Mi Local",  
  **Cuando** un comensal entra a pedir en dicho establecimiento,  
  **Entonces** la plataforma adopta la paleta de colores configurada (color primario en botones de acción, secundario en cabecera y de acento en distintivos) y el logo configurado, exhibiendo además su nombre de fantasía y descripción comercial con contraste legible garantizado.

---

## Casos borde y manejo de errores

- **CB-001 (Pago aprobado con retorno exitoso regular)**:  
  La pasarela retorna la aprobación del pago; el sistema procesa la notificación de inmediato, asegura el estado de la orden, redirige a la pantalla verde con animación y exhibe el número de orden asignado junto al botón de seguimiento y el detalle de la franja.
- **CB-002 (Pago aprobado con navegador interrumpido antes de renderizar la pantalla verde)**:  
  Si el comensal cierra el navegador, pierde la conexión a internet o el dispositivo se reinicia en el lapso entre la aprobación de la pasarela y la redirección a la pantalla verde, el backend procesa de forma autónoma la confirmación autoritativa y despacha inmediatamente el correo electrónico con el comprobante completo, detalle de ítems, franja y enlace directo de seguimiento conforme a la política universal de emisión.
- **CB-003 (Presión repetida o múltiple del botón de confirmación durante el procesamiento)**:  
  Al hacer clic en el botón de confirmación de pago, la interfaz inhabilita inmediatamente dicho control y bloquea interacciones adicionales, desplegando un indicador visual de procesamiento que previene el reenvío de peticiones y garantiza que no se produzcan cobros duplicados.
- **CB-004 (Pedido de retiro inmediato con cola de cocina altamente saturada)**:  
  Si la cola del KDS acumula una alta densidad de pedidos en elaboración, el tiempo estimado de entrega para retiro inmediato calcula e informa un tiempo extendido coherente con dicha sobrecarga, reflejando fielmente la saturación real de la cocina.
- **CB-005 (Reserva programada para una franja futura distante)**:  
  Si la orden fue programada para un turno diferido (por ejemplo, para el día siguiente o con varias horas de anticipación), la vista de seguimiento exhibe de forma fija y destacada la fecha y el horario pactado, sin mostrar contadores activos ni inducir al comensal a acudir al local prematuramente.
- **CB-006 (Transición de reserva programada al umbral de proximidad de 15 minutos)**:  
  Cuando el reloj del sistema alcanza los 15 minutos previos al inicio de la franja horaria pactada, la vista de seguimiento transmuta automáticamente la visualización del horario estático a una cuenta regresiva dinámica en tiempo real hacia el inicio del turno.
- **CB-007 (Ajuste de tiempos de preparación por personal de KDS durante el seguimiento activo)**:  
  Si la cocina introduce un ajuste temporal global ante un cuello de botella o demora imprevista, la pantalla del comensal en seguimiento actualiza en vivo y de forma silenciosa el valor numérico del tiempo estimado en el minutero, sincronizando la expectativa del comensal sin exigirle recargar la pantalla ni emitir mensajes emergentes disruptivos.
- **CB-008 (Pedido marcado como finalizado en KDS mientras el comensal observa el seguimiento)**:  
  En el instante exacto en que la cocina marca la orden como finalizada en el KDS, la interfaz del comensal remueve el contador o tiempo restante, aplica una transición visual notoria y presenta un estado destacado de llamado a mostrador ("Tu pedido está listo para retirar"), acompañado del número de orden en gran formato.
- **CB-009 (Guardado de branding completo por el administrador en "Mi Local")**:  
  El administrador define simultáneamente logotipo, nombre de fantasía, descripción y la tríada de colores primarios en formato hexadecimal (`#RRGGBB`: primario para acciones, secundario para cabecera y acento para detalles); al presionar guardar, el sistema valida que los datos cumplan con los formatos permitidos, aplica el cálculo de contraste para asegurar legibilidad, persiste la configuración y la asocia unívocamente al establecimiento.
- **CB-010 (Comensal que entra a pedir inmediatamente después de un cambio de branding)**:  
  El comensal ingresa al catálogo o flujo de compra del restaurante; la plataforma recupera la configuración de branding vigente del establecimiento y aplica el color primario en botones principales y llamadas a la acción, el color secundario en cabeceras y barras contextuales, y el color de acento en insignias y distintivos, mostrando el logotipo, nombre de fantasía y textos actualizados.
- **CB-011 (Establecimiento que no posee configuración previa de branding en "Mi Local")**:  
  Si el local no ha cargado elementos personalizados de marca, el sistema despliega el diseño institucional neutro de EasyEat, garantizando accesibilidad y funcionalidad completas sin presentar elementos rotos o vacíos.
- **CB-012 (Reingreso o recarga de la pantalla de confirmación por el comensal)**:  
  Si el comensal recarga la pantalla de confirmación o ingresa a través del enlace del correo electrónico, el sistema presenta de forma armónica la confirmación aprobada junto con el widget vivo de seguimiento de su pedido en su estado actual, manteniendo la trazabilidad histórica de la orden.

---

## Requisitos funcionales

- **FR-001**: El sistema debe redirigir al comensal a una pantalla verde de éxito con animaciones visuales y exhibir de forma visible el número de orden generado cuando la pasarela retorne un código de aprobación exitoso. *(Derivado de COM-7.3.1, Criterio 1)*
- **FR-002**: El sistema debe emitir de forma universal y garantizada un correo electrónico a la casilla registrada del comensal con el comprobante formal, el detalle completo de la orden y el enlace de seguimiento ante todo pago aprobado, asegurando su recepción incluso si el navegador sufre una interrupción antes de cargar la pantalla de éxito. *(Derivado de COM-7.3.1, Criterio 2)*
- **FR-003**: El sistema debe exhibir en la pantalla de confirmación la franja horaria de retiro elegida por el comensal y disponer de un control habilitado para realizar el seguimiento en vivo de la orden. *(Derivado de COM-7.3.1, Criterio 3)*
- **FR-004**: El sistema debe bloquear inmediatamente las interacciones sobre la interfaz mientras se procesa el pago y se aguarda la respuesta autoritativa de la pasarela, impidiendo presiones múltiples y cobros duplicados. *(Derivado de COM-7.3.1, Criterio 4)*
- **FR-005**: El sistema debe mostrar una barra de progreso y un tiempo estimado de entrega calculado dinámicamente según la saturación actual de la cola de pedidos en el KDS para órdenes con modalidad de retiro inmediato. *(Derivado de COM-7.3.2, Criterio 5)*
- **FR-006**: El sistema debe mostrar el horario de retiro pactado de forma estática en lugar de un contador en vivo para reservas programadas a futuro, activando la cuenta regresiva únicamente al restar 15 minutos o menos para el inicio de la franja horaria acordada. *(Derivado de COM-7.3.2, Criterio 6)*
- **FR-007**: El sistema debe actualizar automáticamente y de manera silenciosa el reloj dinámico en la pantalla del comensal, reflejando el nuevo valor de minutos restantes cuando el personal del KDS registre ajustes en los tiempos globales de preparación de la cocina. *(Derivado de COM-7.3.2, Criterio 7)*
- **FR-008**: El sistema debe retirar el tiempo estimado de entrega y hacer transicionar la pantalla a un estado destacado de aviso indicando que el pedido se encuentra "listo para retirar" por mostrador cuando el personal de cocina marque la orden como finalizada en el KDS. *(Derivado de COM-7.3.2, Criterio 8)*
- **FR-009**: El sistema debe reflejar en la vista del comensal el logotipo, el nombre de fantasía, la descripción comercial y la tríada de colores de marca configurados en formato hexadecimal por el administrador en la sección "Mi Local" (color primario para acciones clave, color secundario para cabeceras y color de acento para etiquetas y bordes), calculando automáticamente el contraste adecuado para textos e iconos sobre elementos destacados al momento en que el comensal ingresa a pedir en dicho establecimiento. *(Derivado de ADM-2.1.1, Criterio 9)*

---

## Entidades clave de negocio

- **Orden confirmada**: Registro formal de la transacción comercial de compra que contiene el identificador unívoco de la orden (número de orden), los platos adquiridos, los montos abonados, el identificador del comensal, el establecimiento gastronómico de destino, la franja horaria de retiro asignada y el estado actual de elaboración.
- **Comprobante de orden**: Documento informativo generado automáticamente tras la aprobación del pago que sintetiza la validez de la transacción, el detalle de ítems, el costo total liquidado, la franja horaria estipulada y el enlace de acceso al seguimiento del pedido.
- **Seguimiento dinámico de orden**: Estado visible de atención para el comensal compuesto por la barra de avance, la métrica temporal de preparación (tiempo dinámico o cuenta regresiva) y la indicación de llamado a mostrador para el retiro físico.
- **Estimación de tiempo de entrega**: Métrica de tiempo remanente derivada de la carga de comandas activas en la cola del KDS y los modificadores globales introducidos por el personal operativo.
- **Identidad visual del restaurante (Branding)**: Conjunto de activos y atributos estéticos de personalización pertenecientes a un establecimiento gastronómico, que incluye archivo de logotipo, denominación de fantasía, texto descriptivo comercial y la tríada de colores de marca en formato hexadecimal (color primario, secundario y de acento) con sus niveles de contraste asociados.

---

## Validaciones e invariantes de negocio

- **INV-001 (Aprobación autoritativa como condición excluyente de la pantalla verde)**:  
  La pantalla verde de éxito solo se presenta si la pasarela de pagos emite un código de aprobación formal y exitoso. Ninguna acción del comensal o alteración en la interfaz de usuario puede sustituir o simular este resultado.
- **INV-002 (Garantía incondicional de emisión universal de comprobante por correo)**:  
  Toda orden con confirmación de pago aprobada genera y despacha de manera automática, garantizada y asíncrona en el servidor el comprobante formal por correo electrónico, asegurando el respaldo tanto en flujos regulares como ante cualquier interrupción o cierre previo a la visualización de la pantalla de éxito.
- **INV-003 (Bloqueo de interfaz e indemnidad contra cobros duplicados)**:  
  El inicio del procesamiento de cobro inhabilita de inmediato los controles de envío de la pantalla. El bloqueo de interacciones no interfiere ni cancela la recepción del desenlace emitido por la pasarela de pagos externa.
- **INV-004 (Completitud informativa de la confirmación)**:  
  La pantalla de confirmación exitosa debe exhibir de forma obligatoria y simultánea el número de orden asignado, la franja horaria de retiro acordada y el acceso directo al seguimiento en vivo.
- **INV-005 (Gobernanza de la saturación del KDS sobre el tiempo inmediato)**:  
  El tiempo estimado exhibido para órdenes de retiro inmediato debe responder dinámicamente al estado y volumen de trabajo real de la cola de preparación en el KDS, no pudiendo tratarse de un valor estático inmutable.
- **INV-006 (Exclusión temporal de contador para reservas programadas distantes)**:  
  Una reserva programada cuya franja horaria de retiro inicie en un plazo superior a 15 minutos debe mostrar únicamente el horario pactado y tiene estrictamente prohibido presentar un contador dinámico regresivo hasta ingresar a dicho umbral.
- **INV-007 (Sincronización reactiva y silenciosa ante ajustes operativos de cocina)**:  
  Cualquier reajuste en los tiempos de preparación globales efectuado por el personal de cocina en el KDS debe verse reflejado directamente en el minutero del reloj dinámico del comensal de forma silenciosa, sin recarga de pantalla ni elementos intrusivos.
- **INV-008 (Irreversibilidad del estado 'Listo para retirar' y retiro del tiempo estimado)**:  
  Al marcarse la orden como finalizada en el KDS, la supresión del tiempo estimado y la transición al estado visual destacado de retiro en mostrador son de carácter definitivo e inmediato.
- **INV-009 (Consistencia multi-tenant y legibilidad del branding configurado)**:  
  Los elementos de marca guardados en "Mi Local" (logotipo, nombre de fantasía, descripción y la tríada de colores primarios en hexadecimal `#RRGGBB` con contraste garantizado) se circunscriben estrictamente al establecimiento gestionado y se aplican a las vistas de catálogo y compra del comensal cuando este interactúa con dicho restaurante.
- **INV-010 (Fallback institucional ante ausencia de personalización)**:  
  Ante la inexistencia de datos de marca personalizados en un establecimiento, la plataforma debe utilizar la identidad institucional base de EasyEat sin degradar la experiencia visual ni interrumpir el proceso de compra.

---

## Límites de alcance (Out of scope)

- **Medios de pago externos ajenos a Mercado Pago**: No se admite ni contempla ninguna otra pasarela, billetera virtual ni modalidad de cobro bancario externa en esta especificación.
- **Tratamiento directo de datos sensibles de tarjetas**: EasyEat no captura, valida, transmite ni almacena números de tarjeta, códigos de seguridad ni vencimientos bancarios.
- **Mecanismos de conciliación, liquidación o reembolso**: Procesos de devolución de dinero, disputas, contracargos, liquidación de fondos a cuentas de los restaurantes y administración financiera de pagos quedan fuera de alcance.
- **Canales de notificación adicionales al correo electrónico**: Queda explícitamente excluido el envío de notificaciones push móviles o web, mensajería por SMS, notificaciones por WhatsApp o cualquier otro canal ajeno al correo electrónico estipulado en COM-7.3.1.
- **Módulos de analítica, métricas de venta o reportería**: Paneles de estadísticas de cobro, conversión o tiempos medios de entrega no forman parte de este alcance.
- **Edición de recetas, ingredientes, costos o catálogo**: La administración culinaria pertenece a iteraciones previas y no se modifica en esta especificación.
- **Alteraciones al algoritmo base del KDS**: La especificación se limita a consumir y reflejar la saturación calculada y los ajustes introducidos manualmente por el personal del KDS, sin redefinir la lógica matemática interna de optimización de comandas.
- **Personalización estética avanzada**: Se excluyen fuentes tipográficas personalizadas, modificaciones de grilla o maquetación, animaciones a medida y temas gráficos múltiples; el branding se restringe estrictamente a logotipo, nombre de fantasía, descripción y paleta cromática primaria.

---

## Supuestos de negocio

- **A-001 (Existencia operativa del checkout y pagos)**: Se asume en plena operación el flujo de pago con Mercado Pago y su resultado autoritativo formalmente integrado según lo especificado en Spec 014 y Spec 015.
- **A-002 (Gestión de turnos y franjas horarias operativas)**: Se asume disponible el motor de reserva de franjas horarias con su ventana de vigencia temporal consolidado en Spec 010 y Spec 012.
- **A-003 (Recepción de pedidos en KDS y despacho transaccional)**: Se asume que las órdenes confirmadas ingresan automáticamente en la cola del KDS con descuento de stock transaccional según Spec 015.
- **A-004 (Infraestructura de correo electrónico disponible)**: Se asume que la plataforma dispone del mecanismo de despacho de correos electrónicos transaccionales del producto para materializar el envío de comprobantes ante interrupción.
- **A-005 (Módulo administrativo y roles preexistentes)**: Se asume la existencia del rol Administrador con acceso al panel "Mi Local" para gestionar los parámetros de su establecimiento gastronómico (Spec 001, Spec 002, Spec 004).
- **A-006 (Umbral temporal de cuenta regresiva para reservas programadas)**: Se documenta formalmente el supuesto de que el término "al acercarse el turno" estipulado en COM-7.3.2 Criterio 6 equivale a un lapso de 15 minutos previos al inicio de la franja horaria pactada, concordante con la cadencia de bloques temporales de EasyEat.

---

## Criterios de éxito medibles (Success criteria)

- **SC-001**: El 100% de las transacciones con código de aprobación exitoso emitido por la pasarela redirigen al comensal a la pantalla verde de éxito exhibiendo el número de orden asignado.
- **SC-002**: El 100% de los incidentes de interrupción del navegador del comensal ocurridos tras una aprobación de pago resultan en la emisión garantizada del correo electrónico con el comprobante y detalle de orden.
- **SC-003**: El 100% de las pantallas de confirmación desplegadas exhiben la franja horaria de retiro seleccionada y presentan habilitado el control de seguimiento en vivo.
- **SC-004**: Registro de 0 cobros duplicados y 0 peticiones transaccionales redundantes atribuibles a presiones múltiples sobre el botón de confirmación durante el procesamiento del pago.
- **SC-005**: El 100% de los pedidos bajo modalidad de retiro inmediato presentan en la vista de seguimiento una barra de progreso y una estimación de entrega calculada dinámicamente según la saturación de la cocina en el KDS.
- **SC-006**: El 100% de los pedidos bajo modalidad de reserva programada exhiben el horario pactado de forma fija mientras la diferencia temporal con el turno sea mayor a 15 minutos, iniciando la cuenta regresiva en el 100% de los casos al ingresar en dicho umbral.
- **SC-007**: El 100% de los ajustes globales de tiempos introducidos por el personal del KDS se reflejan en el reloj dinámico de la pantalla del comensal sin necesidad de recarga manual por parte del usuario.
- **SC-008**: El 100% de las órdenes marcadas como finalizadas en el KDS actualizan la pantalla del comensal retirando el contador y exhibiendo de inmediato el estado destacado de retiro en mostrador.
- **SC-009**: El 100% de las configuraciones de marca guardadas exitosamente por el administrador en "Mi Local" se reflejan en la vista del comensal al momento de entrar a pedir en dicho establecimiento.

---

## Matriz de trazabilidad

| Historia fuente | Criterio fuente | Escenario de aceptación | Requisito funcional | Invariante asociada | Criterio de éxito |
|---|---|---|---|---|---|
| **COM-7.3.1** | Criterio 1: Aprobación, pantalla verde y número de orden | AS-1 | FR-001 | INV-001, INV-004 | SC-001 |
| **COM-7.3.1** | Criterio 2: Interrupción del navegador y envío de correo | AS-2 | FR-002 | INV-002 | SC-002 |
| **COM-7.3.1** | Criterio 3: Detalle de franja y botón de seguimiento en vivo | AS-3 | FR-003 | INV-004 | SC-003 |
| **COM-7.3.1** | Criterio 4: Bloqueo de interacciones y prevención de cobro duplicado | AS-4 | FR-004 | INV-003 | SC-004 |
| **COM-7.3.2** | Criterio 5: Retiro inmediato, barra de progreso y tiempo dinámico KDS | AS-5 | FR-005 | INV-005 | SC-005 |
| **COM-7.3.2** | Criterio 6: Reserva programada, horario pactado y cuenta regresiva a 15 min | AS-6 | FR-006 | INV-006 | SC-006 |
| **COM-7.3.2** | Criterio 7: Ajustes globales de cocina y actualización de reloj dinámico | AS-7 | FR-007 | INV-007 | SC-007 |
| **COM-7.3.2** | Criterio 8: Finalización en KDS, supresión de tiempo y aviso destacado de retiro | AS-8 | FR-008 | INV-008 | SC-008 |
| **ADM-2.1.1** | Criterio 9: Guardado en "Mi Local" y adopción de branding por el comensal | AS-9 | FR-009 | INV-009, INV-010 | SC-009 |

