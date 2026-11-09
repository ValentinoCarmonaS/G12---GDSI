# Especificación Funcional: Pago de Carrito Individual y Descuento de Stock Transaccional

**Rama de la Feature**: `015-individual-checkout-stock-deduction`  
**Fecha de Creación**: 09/11/2026  
**Estado**: Aprobado  
**Entrada**: Descripción del usuario: "Feature: Pago de carrito individual y descuento de stock transaccional (Spec 015) - Spec ID: 015"

---

## Contexto y Trazabilidad Funcional

- **Historias de Usuario Fuente Obligatorias**:
  - `COM-7.2.1` — Pago de carrito individual.
  - `CHF-5.1.1` — Descuento de stock transaccional.
- **Trazabilidad Funcional y Dependencias de Producto**:
  - Esta especificación se articula armónicamente sobre las capacidades operativas consolidadas en las iteraciones previas del producto:
    - Carrito de compras individual y gestión de ítems en sesión local (definido en Spec 009).
    - Configuración horaria y capacidades de atención del establecimiento (definido en Spec 010).
    - Motor de selección de franjas horarias y reservas de turnos con ventana de vigencia (definido en Spec 012).
    - Salvaguardas de disponibilidad y pausa manual de platos por cocina (definido en Spec 013).
    - Flujo de pago y redirección con Mercado Pago en checkout (definido en Spec 014).
  - La Spec 014 delegó formalmente a `COM-7.2.1` los casos especiales de carritos con monto cero por bonificación del 100% y el tratamiento de los estados pendientes devueltos por la pasarela de pagos externa. La presente Spec 015 absorbe y resuelve dichos casos delegados en lo referente al pago de carrito individual y al débito real de stock de insumos por receta.
  - Asimismo, esta especificación extiende el flujo de pago con Mercado Pago introduciendo: confirmación definitiva de la orden, despacho automático a la cola del sistema de cocina (KDS), descuento transaccional del stock real de materias primas por receta, rechazo del cobro por agotamiento de insumos por concurrencia, interrupción de la transacción por saturación concurrente de la franja horaria, omisión del paso por la pasarela para órdenes con bonificación total, y retención provisoria de inventario ante estados pendientes sin ingreso a cocina hasta la aprobación final.
- **Límites de Alcance Estrictos**:
  - No se incorporan historias adicionales, actores no previstos, medios de pago ajenos a Mercado Pago, monedas extranjeras, mecanismos de reembolso o conciliación contable, gestión administrativa de pagos ni notificaciones al personal de cocina más allá de la inserción automática de la orden en la cola del KDS.

---

## Clarificaciones

### Sesión 2026-11-09 (Grill-Me & Clarify)

- **Q1: Garantía de reserva y stock durante el pago frente a concurrencia (Criterios 2 y 3)**: Si el comensal se encuentra en el proceso de pago, ¿durante cuánto tiempo están garantizados su stock y su franja horaria frente a otros usuarios?  
  → **A1**: Durante los 15 minutos de la ventana de reserva temporal conjunta (definida en Spec 012 y Spec 014), el cupo de la franja horaria y el stock de insumos están estrictamente garantizados para el comensal. Por lo tanto:
  1. El rechazo por insumos agotados por concurrencia (Criterio 2) ocurre al momento de presionar "Pagar" e iniciar la transacción, si otro usuario consolidó primero la reserva sobre las últimas unidades disponibles antes de asegurarse el bloqueo temporal.
  2. La interrupción por franja horaria saturada (Criterio 3) ocurre si el comensal demora más de los 15 minutos de la ventana de pago, provocando que la reserva expire y se libere, y que otros comensales agoten los cupos disponibles de ese turno antes de que intente completar la operación.

- **Q2: Vigencia de la retención temporal de stock ante estado pendiente de Mercado Pago (Criterio 5)**: ¿Cómo opera la retención de stock y cupo cuando la pasarela devuelve un estado pendiente de resolución?  
  → **A2**: Al recibir el estado pendiente (`pending`), el comensal ya efectuó formalmente el pago dentro de los 15 minutos. La orden transiciona a estado pendiente de acreditación y la retención provisoria de stock de insumos y cupo de franja se mantiene congelada/activa a la espera de la resolución final asíncrona de Mercado Pago, sin enviarse a la cola del KDS. Si la pasarela notifica aprobación final vía webhook, se debita el stock real y se despacha al KDS; si notifica rechazo, se cancela la orden y se liberan los recursos. Toda aprobación tardía sobre órdenes que hubieran expirado por inactividad previa sin reporte de pago pendiente queda estrictamente fuera de este alcance y delegada a una historia posterior de conciliación financiera.

### Alineación con Reglas de Negocio y Especificaciones Previas

- **C1: Descuento de stock por receta en órdenes bonificadas al 100%**: ¿Aplica el débito real de ingredientes a pedidos con monto total cero por bonificación completa?  
  → **Resolución**: Sí. Conforme al criterio de negocio de `CHF-5.1.1`, todo pedido confirmado que ingresa a cocina para su preparación debe descontar los ingredientes exactos de su receta. Una bonificación del 100% constituye una condición comercial que extingue la obligación de cobro monetario, pero no exime la elaboración física del plato; por ende, se procesa operativamente como un pago aprobado por monto cero y aplica el descuento atómico de stock.

- **C2: Diferenciación entre retención temporal y débito real en estado pendiente**: ¿Cuál es el efecto sobre el inventario cuando la pasarela devuelve un estado pendiente?  
  → **Resolución**: El estado pendiente retiene temporalmente el stock de insumos y el cupo de la franja horaria bajo la ventana de reserva para asegurar que, si el pago se aprueba, los ingredientes sigan disponibles. Sin embargo, no constituye un débito real ni da ingreso a la orden en la cola del KDS. El débito físico y el envío a cocina quedan estrictamente supeditados a la recepción de la aprobación final.

- **C3: Concurrencia sobre insumos compartidos entre distintos platos**: ¿Cómo se evalúa la disponibilidad si dos órdenes concurrentes demandan el mismo ingrediente a través de recetas distintas?  
  → **Resolución**: La evaluación y el descuento de stock se ejecutan sobre las materias primas a nivel de receta. Si la cantidad disponible de un insumo no cubre la sumatoria de las recetas del pedido en el instante de confirmar el cobro, la transacción se rechaza en su totalidad, no se debita dinero y se solicita al comensal modificar su carrito, evitando vender comida que la cocina no puede preparar.

- **C4: Comportamiento ante saturación de franja horaria por concurrencia durante el pago**: Si otros usuarios reservan los últimos cupos de la franja mientras un comensal interactúa con el proceso de pago, ¿cómo responde el sistema?  
  → **Resolución**: El sistema detecta la pérdida de cupo antes de consolidar la orden, interrumpe la transacción de inmediato sin efectuar cobro, preserva los platos en el carrito y redirige al comensal a la selección de un nuevo horario de retiro disponible.

- **C5: Aprobación sin retorno del comensal**: Si la pasarela aprueba el pago pero el comensal cierra el navegador o pierde la conexión antes de volver al sitio, ¿cómo procede el pedido?  
  → **Resolución**: En estricta coherencia con la Spec 014 (AS-2b), la aprobación notificada por la pasarela tiene autoridad suficiente para que el backend debite el stock real de los insumos de forma atómica, cambie la orden al estado en curso y la despache automáticamente a la cola del KDS, garantizando que el pedido se elabore independientemente de la navegación del usuario.

---

## Propósito

Asegurar que el proceso de confirmación de pago de un carrito individual y el ingreso de pedidos a la cocina se encuentren estrictamente atados a la disponibilidad física real del inventario del restaurante, garantizando un descuento de ingredientes por receta indivisible y exacto, evitando la venta de platos no elaborables y manejando de forma segura y consistente las colisiones concurrentes de stock y cupos, las órdenes con bonificación total y los desenlaces pendientes de la pasarela.

---

## Actores

- **Comensal Solitario**: Usuario que ha armado su pedido individual en el carrito de compras, selecciona un horario de retiro y procede a pagar la totalidad de su orden para que sea preparada en cocina.
- **Chef Responsable de Cocina**: Encargado del servicio gastronómico que requiere que el inventario de materias primas refleje de manera inmediata y exacta el consumo generado por cada pedido confirmado, garantizando que los pedidos en la cola del KDS cuentan con todos los ingredientes necesarios para su elaboración.

---

## Escenarios de Aceptación y Pruebas

### User Story 1 — COM-7.2.1: Pago de Carrito Individual (Prioridad: P1)

Como comensal solitario,  
quiero pagar el 100% de mi orden y que se confirme de inmediato,  
para enviar la orden a la cocina.

**Por qué esta prioridad**: Es el flujo central de confirmación y cierre del circuito comercial para el comensal. Conecta la intención de compra con la cocina y asegura que el comensal reciba confirmación fehaciente solo cuando su pedido fue efectivamente admitido y respaldado por existencias reales.

**Prueba independiente**: Se puede verificar en entorno de pruebas iniciando el proceso de pago con un carrito individual, evaluando los desenlaces de aprobación con monto mayor a cero, rechazo por falta de insumos por concurrencia, interrupción por franja horaria saturada, aprobación directa de orden con monto cero por bonificación 100%, y retención provisoria ante estado pendiente devuelto por la pasarela.

#### Escenarios de Aceptación

- **AS-1 (Criterio Fuente 1 — Pago aprobado, débito de stock, orden en curso y envío a KDS)**:  
  **Dado** que el comensal posee un carrito con monto mayor a cero,  
  **Cuando** el pago es aprobado por la pasarela,  
  **Entonces** el sistema debita el stock real de los insumos, cambia el estado de la orden a en curso y la envía automáticamente a la cola del KDS.

- **AS-1b (Derivado de Criterio 1 y Spec 014 AS-2b — Pago aprobado sin retorno del comensal)**:  
  **Dado** que el comensal posee un carrito con monto mayor a cero y el pago es aprobado por la pasarela,  
  **Cuando** el comensal no retorna a la interfaz del sitio tras completar el pago,  
  **Entonces** el sistema debita el stock real de los insumos, cambia el estado de la orden a en curso y la envía automáticamente a la cola del KDS de manera autónoma en el servidor.

- **AS-2 (Criterio Fuente 2 — Insumos agotados por concurrencia al iniciar cobro)**:  
  **Dado** que el comensal inicia el proceso de pago al presionar "Pagar",  
  **Cuando** los insumos de su pedido se agotan por una transacción concurrente antes de confirmarse su cobro y asegurarse su reserva temporal de 15 minutos,  
  **Entonces** el sistema rechaza el pago, no debita dinero y le avisa que debe modificar su carrito.

- **AS-3 (Criterio Fuente 3 — Demora superior a 15 minutos y saturación de franja horaria)**:  
  **Dado** que el comensal demora en la pantalla de pagos superando la ventana de reserva de 15 minutos y provocando la expiración de su turno,  
  **Cuando** la franja horaria seleccionada alcanza su cupo máximo de reservas por otros usuarios antes de que complete el pago,  
  **Entonces** el sistema interrumpe la transacción indicando que debe elegir un nuevo horario de retiro.

- **AS-4 (Criterio Fuente 4 — Carrito con monto cero por bonificación del 100%)**:  
  **Dado** que el monto total del carrito individual es igual a cero debido a una bonificación del cien por ciento,  
  **Cuando** el comensal confirma la orden,  
  **Entonces** el sistema omite el flujo de la pasarela de pagos y envía el pedido directamente a la cocina.

- **AS-5 (Criterio Fuente 5 — Estado pendiente de la pasarela y retención provisoria)**:  
  **Dado** que el comensal efectúa el pago,  
  **Cuando** la pasarela demora en emitir la confirmación y devuelve un estado pendiente,  
  **Entonces** el sistema retiene el stock temporalmente y no envía la orden al KDS hasta obtener la aprobación final.

---

### User Story 2 — CHF-5.1.1: Descuento de Stock Transaccional (Prioridad: P1)

Como chef responsable de cocina,  
quiero que el sistema le descuente automáticamente a mi inventario la cantidad exacta de ingredientes de la receta (ej: 2 panes, 150g carne) ni bien un comensal confirma y paga un pedido,  
para garantizar que la oferta del menú está atada a la realidad y evitar vender comida que ya no podemos preparar.

**Por qué esta prioridad**: Es inseparable del cobro aprobado: un restaurante no puede cobrar platos cuya elaboración no pueda respaldar con ingredientes reales. La integridad física del inventario asegura la credibilidad operativa de la cocina y previene cancelaciones traumáticas durante el servicio.

**Prueba independiente**: Se puede verificar configurando recetas con cantidades enteras y fraccionarias, procesando pedidos aprobados unitarios y múltiples, y comprobando que en la base de datos se reste la cantidad matemática exacta de cada ingrediente, o que frente a la falta de un solo insumo no se descuente ninguno.

#### Escenarios de Aceptación

- **AS-6 (Criterio Fuente 6 — Descuento atómico y estricto de insumos de receta)**:  
  **Dado** que un pago de cliente se aprueba,  
  **Cuando** la orden ingresa al sistema,  
  **Entonces** se restan las cantidades de la receta de cada insumo en la base de datos de forma atómica y estricta.

- **AS-6b (Descuento atómico en órdenes con bonificación 100%)**:  
  **Dado** que una orden con monto total cero por bonificación del 100% es confirmada por el comensal,  
  **Cuando** la orden ingresa al sistema y se envía a la cocina,  
  **Entonces** se restan las cantidades de la receta de cada insumo en la base de datos de forma atómica y estricta.

---

## Requisitos Funcionales

- **FR-001**: El sistema debe debitar el stock real de los insumos correspondientes a las recetas de todos los platos de la orden, cambiar el estado de la orden a "en curso" y enviarla automáticamente a la cola del KDS de forma inmediata cuando el pago de un carrito con monto mayor a cero sea aprobado por la pasarela de pagos.
- **FR-002**: El sistema debe ejecutar el débito real de stock, el cambio de estado de la orden y su despacho a la cola del KDS en el procesamiento del servidor de manera totalmente desatendida, garantizando su concreción aun cuando el comensal no retorne a la interfaz gráfica del sitio tras completar el pago.
- **FR-003**: El sistema debe rechazar el pago, no debitar dinero de la cuenta del comensal y notificarle de manera clara en pantalla que debe modificar su carrito cuando los insumos de su pedido se agoten debido a una transacción concurrente al momento de iniciar el pago, antes de haber consolidado el bloqueo temporal de 15 minutos.
- **FR-004**: El sistema debe interrumpir la transacción, no debitar dinero y exhibir una indicación destacada señalando que debe elegir un nuevo horario de retiro cuando el comensal demore más de 15 minutos en el proceso de pago (provocando la expiración de la reserva) y la franja horaria seleccionada alcance su cupo máximo de reservas por concurrencia con otros usuarios.
- **FR-005**: El sistema debe omitir por completo la redirección y el flujo de la pasarela de pagos externa cuando el monto total del carrito individual sea igual a cero debido a una bonificación del cien por ciento, procesando la confirmación de la orden y enviándola directamente a la cocina con débito transaccional de stock de insumos.
- **FR-006**: El sistema debe retener temporalmente el stock de los insumos requeridos y el cupo de la franja horaria, manteniendo la orden en estado pendiente y fuera de la cola del KDS sin debitar inventario real mientras Mercado Pago informe un estado pendiente de resolución, manteniendo la retención congelada a la espera de la notificación terminal de la pasarela para proceder al débito definitivo y despacho a cocina (si aprueba) o a la liberación de recursos (si rechaza).
- **FR-007**: El sistema debe restar las cantidades exactas de materias primas estipuladas en la receta técnica de cada plato (admitiendo tanto unidades enteras como fracciones continuas de masa o volumen) sobre el inventario de la cocina en una única operación atómica y estricta en el momento en que la orden ingresa confirmada al sistema.
- **FR-008**: El sistema debe cancelar de forma indivisible la totalidad de la deducción de inventario de una orden si al menos uno de los insumos requeridos por sus recetas carece de existencias suficientes, asegurando que bajo ninguna circunstancia se produzcan descuentos parciales de ingredientes.
- **FR-009**: El sistema debe consolidar y multiplicar las cantidades de materias primas según el número de unidades de cada plato contenido en el carrito antes de evaluar la suficiencia de existencias y aplicar la deducción de inventario.
- **FR-010**: El sistema no debe registrar ni persistir órdenes en estado "en curso", cobros monetarios a usuarios ni deducciones efectivas sobre el inventario sin la justificación previa y fehaciente de un pago aprobado por la pasarela o de una orden confirmada con bonificación del cien por ciento.
- **FR-011**: El sistema debe garantizar que cualquier intento transaccional que resulte rechazado, interrumpido o fallido mantenga el saldo monetario del comensal inalterado, no descuente stock de insumos y no genere comandas en la cola del KDS.
- **FR-012**: El sistema debe procesar las notificaciones de aprobación y la deducción de stock bajo reglas estrictas de idempotencia, de modo que eventos idénticos o reenvíos de confirmación sobre una misma orden no apliquen deducciones repetidas de ingredientes ni dupliquen órdenes en el KDS.
- **FR-013**: El sistema debe actualizar el estado visual de los platos del menú en el catálogo para el resto de los comensales, reflejando de inmediato la condición de no disponible cuando el débito atómico de un pedido confirmado agote las existencias de materias primas de su receta.

---

## Restricciones Transversales

- **RT-1 Costo cero verificable**: La totalidad de los escenarios de aceptación y requisitos funcionales de esta especificación deben poder ejecutarse, probarse y verificarse de punta a punta en un entorno de pruebas sin incurrir en costos monetarios para el equipo, los comensales o el restaurante.
- **RT-2 Equivalencia con dinero real**: El comportamiento del sistema en el entorno de pruebas debe ser funcionalmente idéntico a una operación en producción con dinero real; la transición a producción no debe exigir modificaciones en las reglas de descuento atómico, control de concurrencia ni validación de estados de cobro.
- **RT-3 Reproducibilidad determinística de resultados**: Los desenlaces correspondientes a pago aprobado, rechazo por agotamiento concurrente de stock, interrupción por franja horaria saturada, estado pendiente y bonificación al 100% deben ser reproducibles de forma controlada y determinística mediante el uso de datos y cuentas de prueba de la pasarela y la simulación controlada de condiciones de concurrencia.

---

## Validaciones e Invariantes de Negocio

- **INV-01 (Atomicidad y estrictez absoluta del descuento de stock)**:  
  El descuento de ingredientes por receta es una operación indivisible: se debitan todas las cantidades exactas de todos los insumos que componen la totalidad de los platos de la orden, o no se debita ninguno. Queda terminantemente prohibido el débito parcial de ingredientes.
- **INV-02 (Prohibición de venta de comida no preparable)**:  
  La oferta del menú y la confirmación de pedidos están estrictamente subordinadas a la disponibilidad física real de insumos en el inventario. Ninguna orden puede confirmarse si sus materias primas no existen en cantidad suficiente al momento de consolidar la transacción.
- **INV-03 (Diferenciación ontológica entre retención temporal y débito real)**:  
  Una retención provisoria (durante el proceso de checkout o en estado pendiente de pasarela) bloquea preventivamente las existencias para evitar sobreventas, pero no constituye un débito definitivo de inventario. El débito real se concreta única y exclusivamente cuando el cobro queda formalmente aprobado o la orden bonificada al 100% se confirma.
- **INV-04 (Condición exclusiva de despacho a cocina)**:  
  Una comanda ingresa a la cola del KDS y pasa a estado "en curso" si y solo si concurren dos condiciones conjuntas e inseparables: la orden cuenta con cobro aprobado (o bonificación 100%) y el débito real de sus ingredientes ha sido ejecutado exitosamente.
- **INV-05 (Inocuidad transaccional ante rechazo o interrupción)**:  
  Cualquier pago que no resulte aprobado (por rechazo de la pasarela, agotamiento de insumos por concurrencia o saturación de franja horaria) no debita dinero de la cuenta del usuario, no debita stock real del inventario y no transmite comanda alguna a la cocina.
- **INV-06 (Equivalencia funcional de la bonificación al 100%)**:  
  Un carrito con monto total cero por bonificación del 100% posee el mismo valor operativo que un pago aprobado: omite la pasarela financiera pero ejecuta el débito atómico de insumos, cambia la orden a estado "en curso" y la envía de inmediato a la cola del KDS.
- **INV-07 (Idempotencia de inventario y KDS)**:  
  Una orden confirmada debita sus insumos exactamente una vez y genera un único registro en la cola del KDS; eventos de confirmación redundantes o reintentos de notificación deben ser neutralizados sin alterar los saldos de inventario ni emitir órdenes duplicadas.
- **INV-08 (Resolución atómica de colisiones concurrentes y garantía de ventana)**:  
  Frente a una contienda concurrente por las últimas unidades de un ingrediente o el último cupo de una franja horaria, el sistema otorga la reserva temporal exclusivamente al primer comensal que consolida el bloqueo; una vez asignada, la reserva de cupo y stock permanece 100% garantizada para ese comensal durante los 15 minutos de la ventana de pago. Los comensales concurrentes que intenten iniciar el pago cuando el stock ya fue bloqueado son rechazados de inmediato sin cobro monetario. Si un comensal excede los 15 minutos, su reserva expira, perdiendo la garantía de exclusividad.
- **INV-09 (Integridad justificativa de persistencia)**:  
  El sistema tiene prohibido persistir órdenes en estado "en curso", registrar cobros completados o registrar bajas definitivas en el inventario de insumos sin la constancia fehaciente de la aprobación emitida por la pasarela o el registro formal de bonificación al 100%.
- **INV-10 (Alineación continua de la carta)**:  
  El inventario de materias primas constituye la fuente única de verdad para la viabilidad de los platos; cuando un débito transaccional agota un insumo, todos los platos que dependen de dicho insumo pierden automáticamente su condición de venta inmediata en el catálogo.

---

## Comportamiento ante Errores y Casos Borde

- **CB-01 Pago aprobado con carrito mayor a cero**:  
  El comensal abona mediante la pasarela; esta aprueba el monto total. El sistema debita el stock real de los insumos de forma atómica, cambia el estado de la orden a "en curso", la despacha a la cola del KDS, vacía el carrito local y muestra la pantalla de confirmación exitosa del pedido.
- **CB-02 Insumos agotados por concurrencia al iniciar el cobro**:  
  Al momento de presionar "Pagar", otra transacción concurrente descuenta los ingredientes necesarios para uno o más platos del pedido antes de que se consolide el bloqueo de 15 minutos. El sistema detecta la falta de existencias de materias primas, rechaza el inicio del pago, no debita dinero, preserva los ítems en el carrito marcando visualmente los platos no disponibles por falta de stock y notifica al comensal que debe modificar su carrito para continuar.
- **CB-03 Franja horaria saturada tras exceder los 15 minutos en la pasarela**:  
  El comensal demora más de 15 minutos en completar el pago, provocando que su reserva temporal expire y se libere automáticamente a la cocina y al calendario. Si los cupos restantes de la franja son consumidos por otros usuarios antes de que intente completar o retomar el pago, el sistema interrumpe la transacción de inmediato antes de consolidar el cobro, no debita dinero, preserva el carrito intacto y reabre el selector de horarios con un mensaje visible indicando que la franja elegida se saturó y debe elegir un nuevo horario de retiro.
- **CB-04 Carrito con monto cero por bonificación del 100%**:  
  El comensal posee un carrito bonificado al 100% cuyo saldo a pagar es exactamente cero. Al hacer clic en confirmar orden, el sistema no interactúa con la pasarela de pagos ni redirige al usuario; debita de forma inmediata y atómica el stock de los insumos de las recetas, pasa la orden a "en curso", la envía a la cola del KDS y despliega la confirmación directa al comensal.
- **CB-05 Estado pendiente devuelto por la pasarela de pagos**:  
  La pasarela devuelve una respuesta en estado pendiente (por ejemplo, por procesamiento demorado o revisión manual de seguridad). Al haber efectuado el pago el comensal, el sistema transiciona la orden a estado pendiente de acreditación, mantiene congelada la retención temporal de stock y de cupo de la franja (evitando su vencimiento prematuro), no debita el inventario real y no envía la orden al KDS. Despliega al comensal una pantalla informativa indicando que el pago está pendiente de confirmación y que el pedido pasará a cocina ni bien se reciba la aprobación final. Si la pasarela luego aprueba, se debita el stock real y se envía a KDS; si rechaza, se cancela la orden y se liberan el stock y la franja. Toda aprobación tardía sobre órdenes ya expiradas por abandono previo se delega a una historia de contingencia posterior.
- **CB-06 Orden con múltiples platos e insumos compartidos**:  
  Un comensal incluye en su pedido varios platos que requieren la misma materia prima (por ejemplo, dos platos que consumen "Pan de hamburguesa" y "Carne vacuna"). El sistema computa la sumatoria total de las cantidades demandadas por toda la orden y evalúa si el inventario disponible cubre la suma acumulada; si la suma no puede abastecerse en su totalidad, se rechaza la transacción conforme a CB-02.
- **CB-07 Insumos con cantidades fraccionarias de receta**:  
  Las recetas técnicas de los platos contienen ingredientes expresados en medidas no enteras (por ejemplo, 150 gramos de carne picada, 0.25 litros de salsa). El sistema aplica el descuento transaccional con precisión matemática exacta sobre la unidad de medida del insumo, garantizando que no se produzcan pérdidas por redondeo ni inconsistencias entre la receta y el inventario.
- **CB-08 Insuficiencia parcial de stock en una receta con múltiples insumos**:  
  Un plato requiere cinco ingredientes distintos; cuatro tienen stock abundante pero el quinto cuenta con existencias insuficientes. El sistema bloquea el descuento de todos los ingredientes involucrados y rechaza la orden de forma íntegra, impidiendo que el inventario quede descuadrado con deducciones de los cuatro insumos disponibles.
- **CB-09 Reintento de pago tras rechazo o interrupción**:  
  Tras experimentar un rechazo por insumos agotados o una interrupción por franja horaria saturada, el comensal modifica su carrito o elige una nueva franja horaria disponible y presiona pagar nuevamente. El sistema realiza una validación completa y genera un nuevo intento transaccional sin conservar bloqueos remanentes del intento anterior.
- **CB-10 Modificación del carrito tras haber iniciado el pago**:  
  Si un comensal intenta alterar cantidades o agregar platos mientras existe un intento de pago en curso, el sistema exige cancelar explícitamente el intento previo o aguardar su conclusión, evitando discrepancias entre el monto/ítems informados a la pasarela y el contenido del carrito.
- **CB-11 Pago aprobado sin retorno del comensal a la interfaz**:  
  La pasarela aprueba el cobro pero el comensal cierra su pestaña o pierde conectividad. El webhook o notificación de la pasarela es procesado en el backend, se debita el stock de insumos por receta de forma atómica, la orden pasa a "en curso" y se encola en el KDS, asegurando que la cocina elabore el pedido pagado.
- **CB-12 Notificaciones repetidas de un pago ya aprobado**:  
  La pasarela envía confirmaciones duplicadas o el usuario recarga reiteradamente la página de confirmación. El sistema detecta que la orden ya fue transicionada a "en curso" y que el stock de insumos ya fue debitado, ignorando los eventos redundantes sin alterar nuevamente el inventario ni generar duplicados en el KDS.

---

## Entidades Clave de Negocio

- **Orden**: Representa el pedido individual solicitado por el comensal a partir de los platos de su carrito, asociada a un monto monetario, un horario de retiro asignado y un ciclo de vida de estados que transiciona a "en curso" al concretarse la aprobación o bonificación total.
- **Carrito Individual**: Agrupación de platos y cantidades seleccionados por el comensal para su adquisición personal en la sesión activa.
- **Receta de Plato**: Especificación técnica de cocina que define la nómina de materias primas y las cantidades exactas (enteras o fraccionarias) requeridas para la preparación de una unidad del plato terminado.
- **Insumo de Inventario**: Materia prima física almacenada en el inventario del restaurante con su cantidad disponible real y su unidad de medida asociada.
- **Cola del KDS (Kitchen Display System)**: Canal operativo del sistema de cocina donde se reciben de manera ordenada y secuencial los pedidos confirmados en estado "en curso" para su preparación inmediata o programada por la brigada gastronómica.
- **Transacción de Pago**: Registro de la interacción financiera asociada a la orden, cuyo resultado externo (aprobado, pendiente, rechazado) dictamina la ejecución de los débitos de stock y el avance del pedido.

---

## Supuestos

- Se asume la preexistencia funcional del carrito de compras (Spec 009), la configuración de capacidades de atención (Spec 010), el motor de franjas horarias (Spec 012), el control de stock e ingredientes (Spec 004, Spec 006, Spec 013) y la integración del flujo de pagos con Mercado Pago (Spec 014).
- El descuento de stock por receta aplica de forma equivalente tanto a pagos aprobados con monto mayor a cero como a órdenes con monto cero por bonificación del 100%, dado que ambas representan pedidos confirmados que ingresan a preparación física en cocina.
- La retención provisoria de inventario ante un estado pendiente devuelto por la pasarela constituye una reserva temporal y no un débito definitivo; el débito físico real en base de datos se efectúa únicamente al conocerse la aprobación formal.
- La cola del KDS es el destino obligatorio y automático de las órdenes que transicionan al estado "en curso".
- Las situaciones complejas relativas a aprobaciones financieras tardías sobre órdenes cuya reserva temporal hubiera expirado se consideran fuera de este alcance funcional y quedan delegadas a una historia de contingencia posterior, más allá de la gestión del estado pendiente establecida en el Criterio 5.

---

## Fuera de Alcance

- Soporte o integración de pasarelas de pago o proveedores financieros alternativos fuera de Mercado Pago.
- Captura, procesamiento, validación o almacenamiento de datos de tarjetas bancarias en las pantallas de EasyEat.
- Mecanismos de devolución de dinero, reembolsos comerciales, liquidación bancaria, conciliación contable o financiamiento en cuotas.
- Procesos de autenticación, autorización o paneles de administración financiera de pagos.
- Edición, creación o borrado de recetas técnicas de cocina (especificado en Spec 005) o fijación de costos y márgenes de ganancia (Spec 006 y Spec 007).
- Módulos de administración de compras a proveedores o auditoría de almacenes más allá del descuento automático de insumos por receta.
- Emisión de notificaciones push, correos electrónicos o avisos móviles al chef, manteniéndose exclusivamente la inserción automática de la orden en la cola del KDS.
- Tratamiento de aprobaciones tardías sobre reservas ya vencidas más allá de lo dispuesto en el Criterio 5 y el Caso Borde CB-05.

---

## Criterios de Éxito

- **SC-001**: El 100% de los pagos aprobados por la pasarela correspondientes a carritos con monto mayor a cero debitan de inmediato el stock real de los insumos de sus recetas, cambian el estado de la orden a "en curso" y la encolan en el KDS.
- **SC-002**: Cero (0) transacciones con insumos de receta agotados por concurrencia que resulten aprobadas o procesadas indebidamente; el 100% de dichos casos son rechazados de forma inmediata.
- **SC-003**: Cero (0) débitos de dinero en la cuenta de los comensales ante pagos rechazados por agotamiento de insumos o transacciones interrumpidas por saturación de franjas horarias.
- **SC-004**: El 100% de las órdenes con monto total cero debido a una bonificación del cien por ciento omiten el paso por la pasarela de pagos, debitan el stock real de los insumos de sus recetas y se envían de forma directa a la cocina.
- **SC-005**: El 100% de las transacciones con estado pendiente informado por la pasarela retienen provisoriamente el stock y la franja sin debitar inventario real y con cero (0) envíos a la cola del KDS hasta contar con aprobación final.
- **SC-006**: El 100% de los descuentos de stock sobre materias primas son atómicos y estrictos, registrándose cero (0) descuentos parciales ante falta de cualquier insumo de la receta.
- **SC-007**: Cero (0) ventas de platos o confirmaciones de órdenes cuya elaboración no pueda ser cumplida por falta física de materias primas en el inventario.
- **SC-008**: Cero (0) descuentos duplicados de inventario y cero (0) órdenes redundantes en la cola del KDS frente a notificaciones o eventos de confirmación repetidos para una misma transacción.
- **SC-009**: 100% de precisión matemática en la deducción de ingredientes cuyas recetas contengan cantidades fraccionarias de masa o volumen.
- **SC-010**: El 100% de los escenarios de aceptación son completamente testeables y verificables de extremo a extremo en un entorno de pruebas a costo cero (RT-1), manteniendo rigurosa equivalencia funcional respecto a una operación en producción con dinero real (RT-2) y reproducibilidad determinística de resultados (RT-3).

