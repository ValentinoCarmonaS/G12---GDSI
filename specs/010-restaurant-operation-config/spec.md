# Feature Specification: Restaurant Operation Configuration: Business Schedules, Capacity Limits & Online Payment Methods

**Feature Branch**: `010-restaurant-operation-config`

**Created**: 31/10/2026

**Status**: Draft

**Input**: User description: "Sistema web de pedidos de comida (con delivery y retiro). Iteración actual extiende funcionalidad existente de administración operativa del restaurante, selector de franjas del comensal, checkout y flujo de aprobación de pedidos. Tres historias de usuario aprobadas y cerradas: ADM-2.2.1 (Configurar horarios comerciales), ADM-2.2.2 (Configurar capacidad máxima por franja horaria) y ADM-2.2.3 (Habilitar métodos de pago)."

---

## Clarifications

### Session 2026-10-31 (Alineación de Alcance, Arquitectura de Dominio e Invariantes Operativos - Grill-Me)

- Q: ¿Cómo se define la precisión temporal y la inclusión/exclusión del instante de cierre en el bloqueo de pedidos? (ADM-2.2.1 AC-1.1, INV-1, EC-001) → A: Se adopta un intervalo temporal semi-abierto $[\text{hora\_apertura}, \text{hora\_cierre})$. El instante exacto fijado como $\text{hora\_cierre}$ (por ejemplo, 16:00:00) marca el fin de la jornada operativa y se considera cerrado. Por ende, cualquier pedido iniciado a las 16:00:00 o posterior es bloqueado automáticamente, satisfaciendo estrictamente el criterio de que a las 16:01 el sistema rechaza nuevas órdenes para ese día.
- Q: ¿Qué sucede si un comensal intenta armar un pedido fuera del horario comercial para una franja futura válida? (ejemplo: a las 22:00 reservar para mañana a las 13:00) (ADM-2.2.1 AC-1.1, INV-1, EC-017) → A: La admisión temporal se evalúa sobre la franja horaria destino de retiro ($\text{hora\_franja\_pedido}$), no sobre el instante en que el comensal navega la plataforma. A las 22:00 es válido reservar para mañana a las 13:00 si el establecimiento opera en esa franja. En cambio, para la fecha en curso, a las 16:01 ya no existen franjas comerciales operativas remanentes, bloqueando automáticamente toda nueva reserva para el día de hoy conforme a AC-1.1.
- Q: ¿Cuál es el alcance técnico, de costos y de pruebas para la habilitación de métodos de pago en ADM-2.2.3? (ADM-2.2.3 AC-3.1, A-005, Out of Scope) → A: Costo cero y desacoplamiento de pasarelas reales: no requiere cuentas bancarias, pasarelas de pago pagas ni consumo de APIs externas reales. La funcionalidad consiste exclusivamente en un indicador booleano de configuración (`enabled: true/false`) administrable desde el Back-Office que determina la visibilidad del método en el checkout del comensal. Las pruebas son 100% determinísticas, gratuitas y locales, verificando la presencia o ausencia del elemento en la interfaz según dicho estado.
- Q: ¿Qué alcance operativo tiene el término "reserva" en el criterio AC-1.1 y cómo afecta a delivery vs. retiro? (INV-10, Supuestos) → A: El término "reserva" en AC-1.1 se interpreta formalmente como "pedido de retiro programado en mostrador" (pickup) para ese día calendario. Dado que la narrativa busca "evitar que entren pedidos de retiro cuando la cocina está cerrada", el bloqueo opera sobre todo pedido destinado al día cuya hora programada o de emisión caiga fuera del horario comercial. Los pedidos con despacho por delivery quedan supeditados a la misma regla general de cocina cerrada.
- Q: ¿Cómo responde el selector de franjas ante una reducción de capacidad que deja a los pedidos ya aprobados por encima del nuevo límite? (ADM-2.2.2 AC-2.1, INV-3, INV-8, EC-005) → A: No retroactividad e invalidación inmediata: la actualización de la configuración no altera, no cancela ni desaprueba pedidos existentes (INV-8). Sin embargo, al cumplirse que $\text{pedidos\_aprobados} \ge \text{capacidad\_maxima}$, la franja transiciona inmediatamente a estado "Agotada" en el selector del comensal, impidiendo cualquier reserva adicional.
- Q: ¿Cuál es el comportamiento del sistema ante franjas de 15 minutos dentro del horario comercial que carezcan de configuración explícita de capacidad? (EC-007) → A: Política de resguardo de cocina: toda franja horaria sin una capacidad configurada explícitamente asume por defecto una capacidad de 0 pedidos, presentándose como agotada en el selector del comensal para evitar sobrecargas no supervisadas.
- Q: ¿Qué sucede en el checkout si el Administrador apaga todos los métodos de pago online disponibles? (ADM-2.2.3 AC-3.1, EC-009, FR-013) → A: Bloqueo informativo de compra: si no existe ningún método de pago online habilitado, el checkout despliega un mensaje explicativo indicando que no hay medios de pago online disponibles en ese momento y deshabilita la acción de confirmación de compra, impidiendo la emisión de pedidos huérfanos de pago.
- Q: ¿Cómo se maneja la concurrencia si el Administrador deshabilita un método de pago mientras un comensal se encuentra en la pantalla de checkout? (INV-9, EC-010, FR-012) → A: Validación de disponibilidad al someter la orden: la habilitación del método de pago se valida en el instante exacto en que el comensal confirma la transacción. Si el método fue apagado mientras estaba en pantalla, el sistema rechaza la confirmación, actualiza la lista de opciones y solicita al comensal seleccionar un método vigente.

---

## Invariantes de Negocio

Los siguientes invariantes constituyen reglas operativas formales e inviolables del sistema:

- **INV-1: Condición de admisión temporal según franja de retiro**  
  $$\text{pedido\_admitido\_en\_franja\_F\_del\_día\_D} \iff (\text{hora\_apertura\_D} \le \text{hora\_inicio\_F} < \text{hora\_cierre\_D}) \land \text{franja\_F\_no\_caducada}$$  
  *Ejemplo numérico*: Si el horario comercial de un establecimiento es de 11:00 a 16:00, a las 22:00 de un martes un comensal puede generar un pedido para el miércoles en la franja de 13:00 a 13:15 porque dicha franja cae dentro del horario operativo de ese día. En cambio, el martes a las 16:01 no existen franjas válidas remanentes para el martes, bloqueando automáticamente cualquier pedido para el mismo día (AC-1.1).

- **INV-2: Condición de admisión por capacidad en franja**  
  $$\text{pedido\_admitido\_en\_franja\_F} \iff \text{pedidos\_aprobados\_en\_F} < \text{capacidad\_maxima\_F}$$  
  *Ejemplo numérico*: En la franja horaria de 12:00 (12:00 a 12:15) con capacidad máxima de 50 pedidos, si existen 49 pedidos aprobados, el próximo pedido es admitido para aprobación.

- **INV-3: Condición de agotamiento de franja horaria**  
  $$\text{franja\_F\_marcada\_agotada} \iff \text{pedidos\_aprobados\_en\_F} \ge \text{capacidad\_maxima\_F}$$  
  *Ejemplo numérico*: En la franja de 12:00 con capacidad máxima de 50 pedidos, al aprobarse el pedido número 50 ($\text{pedidos\_aprobados} = 50 \ge 50$), la franja se marca inmediatamente como "Agotada" en el selector del comensal. Si la capacidad se reduce de 50 a 40 con 45 pedidos aprobados, la franja se marca como "Agotada" de forma inmediata ($45 \ge 40$).

- **INV-4: Visibilidad de métodos de pago en checkout**  
  $$\text{método\_pago\_M\_visible\_en\_checkout} \iff \text{admin\_habilitó\_M} = \text{true}$$  
  *Ejemplo numérico*: Si MercadoPago está habilitado ($\text{true}$) y Stripe está deshabilitado ($\text{false}$), el checkout del comensal exhibe únicamente MercadoPago y omite totalmente la opción de Stripe.

- **INV-5: Partición determinística de franjas horarias**  
  $$\text{franja\_F} = \text{bloque\_de\_15\_minutos\_iniciando\_en\_hora\_F}$$  
  *Ejemplo numérico*: Para una jornada de 12:00 a 14:00, la grilla se compone de exactamente 8 bloques continuos y disjuntos: [12:00-12:15), [12:15-12:30), [12:30-12:45), [12:45-13:00), [13:00-13:15), [13:15-13:30), [13:30-13:45), [13:45-14:00).

- **INV-6: Dominio de la capacidad máxima por franja**  
  $$\text{capacidad\_maxima\_F} \in \mathbb{N}_0 \quad (\text{entero mayor o igual a 0})$$  
  *Ejemplo numérico*: Valores de capacidad como 0, 1, 25 o 50 son admitidos. Valores como $-1$, $-10$ o $15.5$ son estrictamente rechazados por la validación de entrada.

- **INV-7: Coherencia temporal de apertura y cierre**  
  $$\text{hora\_apertura\_del\_día} < \text{hora\_cierre\_del\_día}$$  
  *Ejemplo numérico*: Una configuración con apertura 09:00 y cierre 17:00 es admitida. Intentos de definir apertura 16:00 y cierre 16:00, o apertura 18:00 y cierre 11:00, son rechazados.

- **INV-8: No retroactividad de las actualizaciones de configuración**  
  $$\text{configuración\_actualizada} \implies (\text{no\_altera}(\text{pedidos\_ya\_aprobados}) \land \text{aplica\_a}(\text{pedidos\_nuevos}))$$  
  *Ejemplo numérico*: Si el Administrador adelanta el cierre de las 17:00 a las 15:00 o reduce la capacidad de una franja, los pedidos que ya hubieran sido aprobados antes del guardado conservan su estado y validez operativa intactos.

- **INV-9: Evaluación dinámica y no cacheada del método de pago**  
  $$\text{método\_pago\_M\_disponible\_en\_checkout} \iff \text{admin\_habilitó\_M} = \text{true} \quad (\text{evaluado al confirmar})$$  
  *Ejemplo numérico*: Si un comensal visualiza Stripe en su pantalla y el Administrador lo apaga antes de que el comensal presione "Confirmar Pago", el sistema revalida el estado en tiempo real, rechaza el pago por ese medio y le solicita elegir una opción activa.

- **INV-10: Interpretación unívoca de reserva en retiro**  
  $$\text{reserva\_en\_AC-1.1} \equiv \text{pedido\_de\_retiro\_programado\_en\_mostrador}$$  
  *Definición*: La regla de bloqueo por horario de cierre rige para la admisión de pedidos de retiro programado para ese día calendario en el establecimiento gastronómico.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configurar horarios comerciales del restaurante (Priority: P1)

Como administrador,  
quiero definir los días y horarios exactos de apertura y cierre,  
para evitar que entren pedidos de retiro cuando la cocina está cerrada.

**Why this priority**: Es crítica para la viabilidad de la operación gastronómica (`ADM-2.2.1`). Sin control de horarios, el restaurante recibiría pedidos fuera de su turno de trabajo, generando insatisfacción en los comensales e incumplimiento físico de entrega (criterio AC-1.1).

**Independent Test**: Puede probarse de forma autónoma ingresando con rol de Administrador a la sección de configuración operativa, definiendo días y rangos de apertura y cierre (ejemplo: martes de 11:00 a 16:00), y verificando que el comensal pueda generar pedidos dentro de dicho intervalo y que a las 16:01 el sistema bloquee automáticamente nuevos pedidos para ese día.

**Acceptance Scenarios**:

1. **Dado** que el restaurante tiene configurado el horario de cierre a las 16:00 para el día en curso, **Cuando** la hora del sistema alcanza las 16:01, **Entonces** el sistema bloquea automáticamente la entrada y confirmación de nuevas reservas o pedidos de retiro para ese día (AC-1.1).
2. **Dado** un Administrador en el panel de configuración operativa, **Cuando** define para un día comercial una hora de apertura a las 11:00 y de cierre a las 16:00 y confirma la acción, **Entonces** el sistema persiste el horario y aplica inmediatamente la regla de admisión comercial para dicho día.
3. **Dado** un comensal intentando realizar un pedido de retiro para el día de la fecha, **Cuando** la hora actual es anterior a la hora de apertura configurada (ejemplo: 09:30 siendo la apertura a las 10:00), **Entonces** el sistema bloquea la creación del pedido y notifica el horario de apertura correspondiente.
4. **Dado** un comensal que inicia un pedido a las 15:58 en un restaurante con cierre a las 16:00, **Cuando** el pedido es admitido por el sistema antes del cierre, **Entonces** el pedido puede continuar su flujo de aprobación incluso si la confirmación definitiva ocurre después de las 16:00.
5. **Dado** un Administrador configurando el horario de un día, **Cuando** intenta establecer una hora de apertura igual o posterior a la hora de cierre (ejemplo: apertura 17:00 y cierre 15:00), **Entonces** el sistema rechaza el guardado y despliega un mensaje de error indicando que la apertura debe preceder al cierre.
6. **Dado** un día de la semana sin horario comercial configurado por el Administrador, **Cuando** un comensal intenta seleccionar dicho día en la plataforma, **Entonces** el sistema presenta el día como no disponible e impide la creación de órdenes.
7. **Dado** que el restaurante se encuentra fuera de su horario comercial (ejemplo: son las 22:00 y el cierre fue a las 16:00), **Cuando** un comensal programa un pedido para el día siguiente seleccionando una franja válida de atención (ejemplo: 13:00 a 13:15, con horario de 11:00 a 16:00), **Entonces** el sistema admite la creación de la reserva diferida con normalidad.

---

### User Story 2 - Configurar capacidad máxima por franja horaria (Priority: P1)

Como administrador,  
quiero definir cuántos pedidos máximo (slots) podemos preparar y entregar en cada bloque de 15 minutos,  
para evitar la saturación de la cocina y garantizar las entregas a tiempo.

**Why this priority**: Es crítica para la capacidad productiva del restaurante (`ADM-2.2.2`). Evita cuellos de botella en la cocina y retrasos en la preparación, asegurando un flujo continuo de pedidos acorde a la capacidad operativa real (criterio AC-2.1).

**Independent Test**: Puede probarse de forma autónoma configurando una capacidad máxima en una franja horaria de 15 minutos (ejemplo: 50 pedidos en la franja de 12:00), simulando la aprobación sucesiva de pedidos hasta alcanzar 50, y confirmando que exactamente con la aprobación del pedido 50 la franja se marca como "Agotada" en el selector del comensal.

**Acceptance Scenarios**:

1. **Dado** que la capacidad máxima configurada es de 50 pedidos para la franja horaria de 12:00, **Cuando** el pedido número 50 de dicha franja es aprobado, **Entonces** el sistema marca inmediatamente esa franja como "Agotada" en el selector del comensal (AC-2.1).
2. **Dado** que una franja de 15 minutos se encuentra marcada como "Agotada" por alcanzar su capacidad máxima, **Cuando** un comensal accede al selector de horarios, **Entonces** visualiza dicha franja deshabilitada con el distintivo "Agotada" y no puede seleccionarla para su orden.
3. **Dado** un Administrador en la matriz de franjas horarias de 15 minutos, **Cuando** ingresa una capacidad máxima como número entero no negativo (ejemplo: 35) y guarda los cambios, **Entonces** el sistema actualiza la capacidad disponible para dicha franja.
4. **Dado** un Administrador configurando una franja horaria, **Cuando** intenta ingresar un valor de capacidad negativo (ejemplo: -5) o caracteres no numéricos, **Entonces** el sistema bloquea el guardado y muestra un mensaje indicando que la capacidad debe ser un número entero mayor o igual a 0.
5. **Dado** que una franja posee 40 pedidos ya aprobados con una capacidad original de 50, **Cuando** el Administrador reduce la capacidad máxima de esa franja a 30, **Entonces** la franja pasa de inmediato a estado "Agotada" en el selector del comensal, manteniendo intactos los 40 pedidos previamente aprobados.
6. **Dado** que una franja se encuentra en estado "Agotada" con 50 pedidos aprobados, **Cuando** el Administrador incrementa su capacidad máxima a 60, **Entonces** el sistema rehabilita la franja en el selector del comensal indicando disponibilidad para 10 pedidos adicionales.
7. **Dado** un Administrador que asigna capacidad 0 a un bloque de 15 minutos, **Cuando** se persiste la configuración, **Entonces** dicha franja se marca como "Agotada" permanentemente en el selector, impidiendo cualquier pedido en ese intervalo.

---

### User Story 3 - Habilitar y deshabilitar métodos de pago online (Priority: P2)

Como administrador,  
quiero poder prender o apagar métodos de pago online (ejemplo: MercadoPago, Stripe),  
para adaptarme a las comisiones y cuentas bancarias de mi empresa.

**Why this priority**: Otorga flexibilidad comercial, financiera y administrativa al establecimiento (`ADM-2.2.3`). Permite gestionar costos por comisiones de pasarelas y contingencias operativas sin interrumpir el funcionamiento básico del local (criterio AC-3.1).

**Independent Test**: Puede probarse de forma autónoma ingresando al panel administrativo de métodos de pago, alternando el estado de una pasarela (ejemplo: apagar Stripe dejando encendido MercadoPago), ingresando como comensal a la pantalla de checkout y comprobando que Stripe no figure listado entre las opciones de pago.

**Directriz de implementación y testing (Regla de Costo Cero y Simplicidad)**:
Para evitar sobreingeniería y garantizar que la implementación sea 100% gratuita, ágil y sencilla de testear por cualquier desarrollador o agente:
- **Sin cuentas bancarias ni costos**: Quien implemente esta historia NO debe crear cuentas comerciales en MercadoPago ni Stripe, ni pagar comisiones, ni ingresar tarjetas reales, ni configurar webhooks o SDKs complejos de cobro.
- **Modelo puramente lógico**: Los métodos de pago se representan como una lista fija de opciones (ejemplo: `MERCADO_PAGO`, `STRIPE`), cada una con un indicador booleano de visibilidad (`enabled: true/false`) en la configuración del restaurante.
- **Comportamiento en UI**: En el panel de Administrador se presenta un interruptor (switch/toggle) para alternar el valor booleano. En el Checkout del comensal se realiza un renderizado condicional filtrando únicamente aquellos métodos que tengan `enabled == true`.
- **Estrategia de prueba**: Las pruebas (unitarias, de integración y de interfaz) se ejecutan de forma local, offline y determinística, validando simplemente que al alternar el estado en la base de datos o estado en memoria, la opción aparezca o desaparezca del selector del checkout.

**Acceptance Scenarios**:

1. **Dado** que el Administrador apaga un método de pago online (ejemplo: Stripe), **Cuando** el comensal llega a la pantalla de checkout, **Entonces** esa opción ya no se encuentra disponible entre los métodos seleccionables (AC-3.1).
2. **Dado** que el Administrador enciende un método de pago previamente deshabilitado (ejemplo: MercadoPago), **Cuando** un comensal accede al checkout, **Entonces** dicha opción se exhibe habilitada y lista para ser seleccionada.
3. **Dado** que el Administrador apaga todos los métodos de pago online del establecimiento, **Cuando** un comensal accede al checkout, **Entonces** el sistema muestra un mensaje informativo indicando la indisponibilidad temporal de medios de pago online y deshabilita la confirmación de la orden.
4. **Dado** un comensal que tiene seleccionado un método de pago en el checkout, **Cuando** el Administrador deshabilita dicho método antes de que el comensal confirme la transacción, **Entonces** al pulsar la confirmación el sistema rechaza la operación, notifica que el método fue deshabilitado y solicita seleccionar una alternativa activa.
5. **Dado** un entorno de desarrollo o pruebas sin pasarelas externas reales, **Cuando** se valida la visibilidad de los métodos de pago en el checkout, **Entonces** la visualización depende exclusivamente del indicador booleano (`enabled: true/false`) configurado por el Administrador, sin requerir credenciales pagas ni conexión a servicios financieros externos.

---

### Edge Cases

- **EC-001**: Pedido iniciado exactamente en el minuto de `hora_cierre` → El intervalo es semi-abierto $[\text{hora\_apertura}, \text{hora\_cierre})$. Si el cierre es a las 16:00, un intento de pedido iniciado a las 16:00:00 se considera fuera de horario y es bloqueado por el sistema.
- **EC-002**: Pedido iniciado antes del cierre y aprobado después del cierre → Un pedido admitido válidamente a las 15:58 que queda en revisión conserva su validez temporal; su posterior aprobación a las 16:02 es procesada exitosamente sin ser afectada por el bloqueo de nuevas entradas.
- **EC-003**: Cambio de `hora_cierre` mientras existen pedidos pendientes de aprobación → Si el Administrador adelanta el cierre del restaurante mientras hay pedidos pendientes, estos conservan su curso operativo y pueden ser aprobados o rechazados normalmente, sin alteraciones retroactivas (INV-8).
- **EC-004**: Cambio de `capacidad_maxima_F` con pedidos ya aprobados en la franja $F$ → Si se aumenta la capacidad en una franja con pedidos aprobados, se habilitan nuevos cupos; si se reduce pero permanece por encima de los aprobados, la franja continúa disponible con menor remanente.
- **EC-005**: Reducción de `capacidad_maxima_F` por debajo del total de pedidos ya aprobados → Si existen 45 pedidos aprobados y la capacidad se reduce a 30, la franja se marca inmediatamente como "Agotada" en el selector del comensal. Los 45 pedidos aprobados se preservan intactos sin cancelaciones.
- **EC-006**: Configuración de `capacidad_maxima_F = 0` → La franja se exhibe marcada permanentemente como "Agotada" en el selector del comensal, impidiendo el ingreso de órdenes en ese bloque de 15 minutos.
- **EC-007**: Franjas horarias de 15 minutos sin configuración explícita de capacidad → El sistema asigna por defecto una capacidad de 0 pedidos, tratándola como agotada para prevenir la entrada de órdenes imprevistas en la cocina.
- **EC-008**: Días de la semana sin configuración de horario comercial → El establecimiento se considera completamente cerrado durante todo el día; el selector del comensal bloquea la selección de dicho día.
- **EC-009**: Administrador deshabilita todos los métodos de pago online → En la pantalla de checkout, el comensal visualiza un mensaje bloqueante informando la falta de medios de pago disponibles y el botón de confirmar orden permanece deshabilitado.
- **EC-010**: Comensal a mitad de checkout cuando el Administrador deshabilita el método que tenía en pantalla → Al momento de interactuar para confirmar el pago, el sistema valida la disponibilidad en tiempo real (INV-9), rechaza el procesamiento bajo el método inactivo e instruye a elegir una pasarela habilitada.
- **EC-011**: Comensal a mitad de checkout cuando el Administrador habilita un método nuevo → El comensal visualiza el nuevo método tras recargar sus opciones de checkout o al ingresar nuevamente a la pantalla.
- **EC-012**: Modificaciones simultáneas de configuración por múltiples administradores → Se aplica control de concurrencia optimista; el último guardado confirmado establece la configuración vigente sin corromper la integridad de las grillas de horarios ni de capacidades.
- **EC-013**: Cambio de capacidad u horario comercial mientras el selector del comensal está abierto → Al intentar confirmar la selección de una franja que se agotó o cerró mientras el selector estaba abierto, el sistema revalida la disponibilidad y notifica al comensal el cambio de estado impidiendo el pedido.
- **EC-014**: Zona horaria operativa del restaurante → Todas las validaciones de horarios comerciales y franjas horarias se computan exclusivamente respecto a la zona horaria oficial del establecimiento comercial, sin importar la zona horaria del dispositivo del comensal o administrador.
- **EC-015**: Franjas contiguas con capacidades dispares → Franjas consecutivas de 15 minutos (ejemplo: 12:00 con 50 pedidos y 12:15 con 10 pedidos) operan de manera totalmente independiente. El agotamiento de una no altera el cupo de la siguiente.
- **EC-016**: Bloqueo de pedido concurrente en el instante exacto de la aprobación número 50 → Cuando se confirma la aprobación del pedido que completa el cupo de la franja, cualquier otro pedido simultáneo en evaluación que exceda el límite es rechazado atómicamente por franja agotada.
- **EC-017**: Reserva programada para un día futuro fuera del horario actual → Un comensal navegando a las 22:00 (con cocina cerrada para el día de hoy) puede reservar válidamente para una franja de las 13:00 del día siguiente si el horario comercial de mañana lo contempla. En cambio, cualquier intento de pedir para el día de hoy después de las 16:00 es bloqueado (EC-001, AC-1.1).
- **EC-018**: Desincronización de fecha/hora en clientes → Las decisiones de validación temporal y cálculo de franjas son autoridad exclusiva del servidor; la desincronización horaria en el navegador o teléfono del cliente no altera las reglas de negocio.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al Administrador definir días de apertura y rangos horarios exactos de apertura y cierre para el establecimiento gastronómico (mapea a ADM-2.2.1 y AC-1.1).
- **FR-002**: El sistema DEBE validar que la hora de apertura sea estrictamente anterior a la hora de cierre ($\text{hora\_apertura} < \text{hora\_cierre}$), rechazando horarios iguales o invertidos con un mensaje explicativo (INV-7).
- **FR-003**: El sistema DEBE bloquear automáticamente la creación y confirmación de nuevos pedidos de retiro para el día en curso en el instante en que la hora actual iguale o supere la hora de cierre configurada ($\text{hora\_actual} \ge \text{hora\_cierre}$), garantizando que a las 16:01 un cierre fijado a las 16:00 impida nuevas órdenes para ese día por agotamiento de franjas operativas (mapea a ADM-2.2.1, AC-1.1 e INV-1).
- **FR-004**: El sistema DEBE admitir pedidos programados para franjas futuras siempre que la franja destino se encuentre dentro del horario de apertura y cierre del día correspondiente ($\text{hora\_apertura} \le \text{hora\_franja} < \text{hora\_cierre}$), con independencia de si el restaurante se encuentra operando al momento de navegar o armar el pedido (INV-1 y EC-017).
- **FR-005**: El sistema DEBE particionar la jornada operativa en bloques determinísticos y consecutivos de 15 minutos calculados a partir de la hora de apertura del día (mapea a ADM-2.2.2 e INV-5).
- **FR-006**: El sistema DEBE permitir al Administrador configurar la capacidad máxima de pedidos (slots) para cada franja horaria de 15 minutos mediante un valor numérico entero no negativo (mapea a ADM-2.2.2 e INV-6).
- **FR-007**: El sistema DEBE rechazar cualquier intento de configurar capacidades negativas ($\text{capacidad} < 0$) o no enteras con un mensaje explícito de error (INV-6).
- **FR-008**: El sistema DEBE admitir la entrada de pedidos en una franja horaria $F$ si y solo si la cantidad acumulada de pedidos aprobados en dicha franja es estrictamente menor que la capacidad máxima configurada ($\text{pedidos\_aprobados\_en\_F} < \text{capacidad\_maxima\_F}$) (mapea a ADM-2.2.2, AC-2.1 e INV-2).
- **FR-009**: El sistema DEBE marcar automáticamente una franja horaria como "Agotada" en el selector del comensal en el instante en que la cantidad de pedidos aprobados iguale o supere su capacidad máxima ($\text{pedidos\_aprobados\_en\_F} \ge \text{capacidad\_maxima\_F}$), deshabilitando su selección (mapea a ADM-2.2.2, AC-2.1 e INV-3).
- **FR-010**: El sistema DEBE permitir al Administrador habilitar o deshabilitar individualmente cada método de pago online mediante un interruptor de software (`enabled: true/false`) a nivel de configuración, sin requerir integración con cuentas bancarias reales, hardware fiscal ni pasarelas financieras pagas (mapea a ADM-2.2.3 y AC-3.1).
- **FR-011**: El sistema DEBE presentar en el checkout del comensal únicamente los métodos de pago online que se encuentren en estado habilitado ($\text{admin\_habilitó\_M} = \text{true}$), excluyendo totalmente de la vista las opciones apagadas (mapea a ADM-2.2.3, AC-3.1 e INV-4).
- **FR-012**: El sistema DEBE revalidar en tiempo real la disponibilidad del método de pago seleccionado al momento de confirmar la transacción en el checkout; si la opción fue deshabilitada durante el flujo, el sistema DEBE rechazar el cobro e instruir al comensal a seleccionar un método habilitado (INV-9).
- **FR-013**: El sistema DEBE deshabilitar el botón de confirmación de pedido y mostrar un mensaje informativo en el checkout cuando todos los métodos de pago online del establecimiento se encuentren deshabilitados (EC-009).
- **FR-014**: El sistema DEBE preservar inalterados y en estado válido todos los pedidos ya aprobados ante cualquier modificación posterior en los horarios comerciales, capacidades por franja o métodos de pago (INV-8).
- **FR-015**: El sistema DEBE asignar por defecto una capacidad de 0 pedidos a cualquier franja horaria que no posea una capacidad configurada explícitamente, manteniéndola marcada como agotada en el selector del comensal (EC-007).
- **FR-016**: El sistema DEBE considerar cerrado y no disponible para pedidos a todo día de la semana que carezca de horario comercial configurado (EC-008).
- **FR-017**: El sistema DEBE reflejar los cambios de configuración operativa guardados por el Administrador en la interacción inmediata del comensal con el selector de franjas o checkout, sin requerir reinicios del sistema (SC-006).

---

### Key Entities

- **HorarioComercial**: Representa el esquema operativo del restaurante para un día específico de la semana. Define si el local opera ese día, su hora exacta de apertura ($\text{HH:mm}$) y su hora exacta de cierre ($\text{HH:mm}$), asociado a la zona horaria del establecimiento.
- **FranjaHoraria**: Bloque temporal discreto, continuo y disjunto de exactamente 15 minutos dentro de la jornada de atención del restaurante. Posee fecha, hora de inicio ($\text{HH:mm}$) y hora de finalización ($\text{HH:mm}$).
- **ConfiguracionCapacidadFranja**: Entidad que gobierna el volumen de producción admitido en una franja horaria determinada. Contiene la franja horaria de referencia, la capacidad máxima configurada (entero $\ge 0$), el total acumulado de pedidos aprobados en dicha franja y el estado derivado resultante (Disponible o Agotada).
- **MetodoPagoConfigurable**: Catálogo tipado de métodos de pago online soportados por la plataforma. Contiene el identificador del método (ejemplo: MercadoPago, Stripe), su nombre comercial visible y su estado booleano de visibilidad operativa (Habilitado / Deshabilitado). No almacena credenciales bancarias, tokens privados, secretos de API ni realiza procesamiento de pagos.
- **ConfiguracionOperativaRestaurante**: Agregado administrativo que consolida los horarios semanales, la matriz de capacidades por franja horaria y el estado de habilitación de los métodos de pago para un establecimiento gastronómico.
- **PedidoEnFranja**: Vínculo entre una orden emitida por un comensal y una franja horaria específica para su preparación y retiro. Contiene el identificador de la orden, la franja asignada y el estado de aprobación del pedido.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los intentos de iniciar o confirmar pedidos de retiro destinados al día en curso con $\text{hora\_pedido} \ge \text{hora\_cierre\_del\_día}$ son bloqueados automáticamente por el sistema (AC-1.1).
- **SC-002**: El 100% de los intentos de iniciar pedidos de retiro para franjas fuera del horario comercial ($\text{hora\_franja} < \text{hora\_apertura}$ o $\text{hora\_franja} \ge \text{hora\_cierre}$) son bloqueados automáticamente por el sistema.
- **SC-003**: En una franja horaria con capacidad máxima configurada en 50 pedidos, la franja se marca como "Agotada" en el selector exactamente al aprobarse el pedido número 50 asignado a dicha franja (AC-2.1).
- **SC-004**: El 100% de los métodos de pago online deshabilitados por el Administrador (`enabled = false`) son omitidos en la pantalla de checkout del comensal sin requerir interacción con pasarelas externas reales (AC-3.1).
- **SC-005**: El Administrador puede actualizar y guardar la configuración completa de un conjunto operativo (horario diario, capacidad por franja o método de pago) en $\le 5$ interacciones en la interfaz de gestión.
- **SC-006**: Los cambios de configuración de horarios, capacidades o métodos de pago se reflejan en el selector de franjas y en el checkout del comensal en un tiempo $\le 2$ segundos tras la confirmación del guardado.
- **SC-007**: El 100% de los intentos de configurar una capacidad máxima negativa ($\text{capacidad} < 0$) o no entera son rechazados en la interfaz con un mensaje de validación descriptivo.
- **SC-008**: El 100% de los intentos de configurar una hora de apertura mayor o igual a la hora de cierre ($\text{hora\_apertura} \ge \text{hora\_cierre}$) son rechazados en la interfaz con un mensaje de validación descriptivo.
- **SC-009**: El 100% de los intentos de ingresar formatos horarios no válidos son interceptados y bloqueados por las validaciones de entrada.
- **SC-010**: El sistema mantiene un estado observable 100% consistente (cero franjas marcadas erróneamente como disponibles o agotadas, cero métodos de pago apagados mostrados como activos) ante modificaciones concurrentes entre administradores.
- **SC-011**: El 100% de los pedidos que hayan sido aprobados con anterioridad a una reducción de capacidad o a un adelantamiento de horario de cierre conservan su estado aprobado sin sufrir alteraciones ni cancelaciones retroactivas (INV-8).

---

## Assumptions

- **A-001 (Autenticación y rol de Administrador preexistentes)**: `[SUPUESTO]` Existe un rol "Administrador" y un mecanismo de autenticación y autorización previo que valida los privilegios del usuario antes de acceder a la pantalla de configuración operativa, en concordancia con las especificaciones 001, 002 y 004.
- **A-002 (Flujo de aprobación de pedidos preexistente)**: `[SUPUESTO]` Existe un flujo y estado de aprobación de pedidos previo en el sistema (el criterio AC-2.1 presupone que los pedidos transicionan a estado "Aprobado", evento que incrementa el contador de ocupación de la franja).
- **A-003 (Selector de franjas del comensal preexistente)**: `[SUPUESTO]` Existe un componente o pantalla de selección de franjas horarias en el flujo del comensal; esta especificación gobierna la disponibilidad de cada bloque de 15 minutos y la aplicación del distintivo "Agotada".
- **A-004 (Checkout del comensal preexistente)**: `[SUPUESTO]` Existe una pantalla de checkout previa en la plataforma; esta iteración gobierna exclusivamente la lista de métodos de pago online visibles y elegibles.
- **A-005 (Implementación a costo cero y pruebas locales de métodos de pago)**: `[SUPUESTO]` Los métodos de pago online se modelan conceptualmente como opciones tipadas de configuración gobernadas exclusivamente por una bandera booleana (`enabled: true/false`). La presente iteración opera a costo cero, sin contratar pasarelas de pago, sin cuentas bancarias y sin consumir servicios externos en desarrollo o producción, permitiendo pruebas automatizadas 100% offline mediante verificación de visibilidad en interfaz.
- **A-006 (Interpretación de reserva y pedidos de retiro diferidos)**: `[SUPUESTO]` El término "reserva" utilizado en AC-1.1 se interpreta unívocamente como "pedido de retiro programado en mostrador" (pickup); los pedidos programados para días y franjas futuras válidas se admiten las 24 horas, mientras que el bloqueo por cocina cerrada rige para pedidos cuyo horario de preparación o entrega corresponda a una jornada ya cerrada ($\text{hora\_actual} \ge \text{hora\_cierre}$).
- **A-007 (Definición del límite exacto de cierre)**: `[SUPUESTO]` El intervalo de servicio se interpreta como semi-abierto $[\text{hora\_apertura}, \text{hora\_cierre})$. El instante exacto de cierre (16:00:00) se considera fuera de horario; en consecuencia, a las 16:01 el bloqueo se encuentra plenamente efectivo (AC-1.1).
- **A-008 (Zona horaria única del establecimiento)**: `[SUPUESTO]` Todas las validaciones de horarios comerciales y franjas se realizan tomando como referencia la zona horaria oficial del establecimiento comercial (establecida en la especificación 008, por ejemplo `America/Argentina/Buenos_Aires`).
- **A-009 (No retroactividad de cambios administrativos)**: `[SUPUESTO]` Cualquier ajuste en horarios comerciales, capacidades o medios de pago impacta exclusivamente sobre transacciones e intentos de pedido futuros, protegiendo la inmutabilidad de los pedidos previamente aprobados (INV-8).
- **A-010 (Valores de capacidad y horarios por defecto)**: `[SUPUESTO]` Aquellas franjas de 15 minutos sin capacidad configurada asumen una capacidad de 0 pedidos (agotadas por defecto), y los días sin horario configurado se tratan como cerrado comercialmente, resguardando la cocina de pedidos imprevistos.
- **A-011 (Umbral de interacción operativa del Administrador)**: `[SUPUESTO]` Se define un umbral de hasta 5 interacciones de usuario para que el Administrador complete la carga o edición de un bloque de configuración, garantizando agilidad de uso en el back-office.

---

## Out of Scope

- **Integración real con pasarelas de pago (MercadoPago, Stripe u otras)**: La presente especificación prohíbe explícitamente el uso de credenciales bancarias reales, suscripciones o pasarelas pagas, endpoints externos de cobro, SDKs financieros, webhooks y procesamiento de transacciones monetarias; la historia ADM-2.2.3 se limita única y exclusivamente a la persistencia de un flag booleano (`enabled`) y al filtrado condicional en la interfaz de checkout.
- Hardware fiscal, controladores fiscales físicos, impresoras de comandas fiscales e integración con AFIP, CAE o facturación electrónica legal.
- Procesamiento de cobros directos, conciliación bancaria, acreditación de fondos, transferencias, retenciones impositivas, reembolsos o contracargos.
- Configuración de comisiones porcentuales de pasarelas, tasas bancarias, claves privadas de API o vinculación de cuentas bancarias de la empresa.
- Módulos de autenticación, registro de usuarios, reseteo de claves y asignación de roles para el personal (cubiertos por las especificaciones 001, 002 y 004).
- Soporte para múltiples sucursales bajo una misma cuenta o gestión de múltiples zonas horarias simultáneas para un mismo local.
- Gestión de calendarios de feriados nacionales, días no laborables dinámicos, horarios especiales de festividades o cierres extraordinarios programados.
- Generación de reportes analíticos de ocupación de cocina, métricas de rendimiento por franja o historial y auditoría de modificaciones de configuración.
- Envío de notificaciones al comensal por correo electrónico, SMS o mensajería push ante cambios en los horarios o estados de franjas.
- Modificación, reprogramación, edición de ítems o cancelación de pedidos que ya hubieran sido aprobados.
- Reasignación de pedidos entre diferentes franjas horarias o entre establecimientos.
- Capacidad máxima diferenciada según el canal de entrega (cupos separados para retiro en mostrador vs. delivery).
- Sistema de reservas de mesas físicas para comensales en el salón del restaurante.
- Bloqueo o reserva de stock de platos por franja horaria (el inventario físico y la disponibilidad culinaria continúan gobernados por los ingredientes de las recetas según las especificaciones 004, 005, 006 y 008).
- Mecanismos de lista de espera, turnos virtuales o colas de espera automáticas cuando una franja horaria se encuentra agotada.
